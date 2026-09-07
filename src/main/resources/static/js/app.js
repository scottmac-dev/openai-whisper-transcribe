'use strict';

// ── Constants ─────────────────────────────────────────────────────────
const MAX_FILE_SIZE = 25 * 1024 * 1024;         // 25 MB matching TranscriptionService.MAX_FILE_SIZE
const MODEL = 'gpt-4o-mini-transcribe';         // matching openai.model in application.properties
const TRANSCRIBE_URL = '/api/v1/transcribe';    // STT endpoint
const UPTIME_URL = '/api/v1/admin/uptime';      // admin uptime endpoint
const STATS_URL = '/api/v1/global/stats';       // global token usage endpoint
const UPTIME_POLL_MS = 1000;                    // header/footer refresh interval

// Client-side ceiling on the transcribe round trip. 
// Deliberately above the server's 5s budget, last resort fallback for unforseen connection errors
const REQUEST_TIMEOUT_MS = 30000;

// Recording container, in preference order. 
// Chrome and Firefox take webm/opus.
// iOS browser is WebKit and takes neither, so mp4 is provided as fallback.
const AUDIO_MIME_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'];

// Capture tuned for speech-to-text pipeline
const AUDIO_CONSTRAINTS = {
    channelCount: 1,			// mono chanel, single voice signal
    sampleRate: 16000,			// reduce from default 48kHz -> 16kHz
    echoCancellation: true,		// remove speaker feedback into microphone
    noiseSuppression: true,		// attempt to reduce background interference
    autoGainControl: true,		// attempt to adjust gain for consistent volume
};

// Encode opus at 24kbps, shrinks upload size of compressed result
const AUDIO_BITS_PER_SECOND = 24000;

// Dont wait until recording ends to process audio buffer, break into 1 second chunks 
// and process in chunks for streamlined latency.
const RECORDER_TIMESLICE_MS = 1000;

// ── Helpers ───────────────────────────────────────────────────────────
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// Token counts (int64) with thousands separators, e.g. "12,480"
function formatTokens(n) {
    return Number(n ?? 0).toLocaleString();
}

// Client measured round trip for the transcribe call, e.g. "1.42 s" / "840 ms"
function formatDuration(ms) {
    return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${Math.round(ms)} ms`;
}

// UptimeResponse.serverUptimeSeconds (double) as e.g. "2d 03:14:07" / "00:01:30"
function formatUptime(seconds) {
    const total = Math.max(0, Math.floor(seconds));
    const pad = (n) => String(n).padStart(2, '0');
    const days = Math.floor(total / 86400);
    const clock = `${pad(Math.floor(total / 3600) % 24)}:${pad(Math.floor(total / 60) % 60)}:${pad(total % 60)}`;
    return days > 0 ? `${days}d ${clock}` : clock;
}

// Fallback wording error message per status if response doesnt fit schema.
const FAILURE_BY_STATUS = {
    400: 'No audio was received by the server.',
    413: 'Recording exceeds the 25 MB upload limit.',
    415: 'That audio format was rejected by the server.',
    502: 'Transcription provider is unavailable.',
    504: 'Transcription timed out. Please try again.',
};

// Try to extract ApiExceptionHandler message body which is formatted according to YAML ErrorResponse schema
// Fallback to the mapping above if message body not provided 
async function describeFailure(res) {
    try {
        const body = await res.json();
        if (typeof body?.message === 'string' && body.message.trim()) return body.message;
    } catch {
        // Not JSON, or an empty body. Fall through to the status map.
    }
    return FAILURE_BY_STATUS[res.status] ?? `Request failed: ${res.status}`;
}


// Provide descriptive errors which may occur from the browser media recorder APIS
const MIC_ERRORS = {
    NotAllowedError: 'Microphone access was blocked. Allow it in your browser settings, then try again.',
    NotFoundError: 'No microphone was found. Connect one and try again.',
    NotReadableError: 'The microphone is already in use by another application.',
    OverconstrainedError: 'No microphone matches the requested audio settings.',
    SecurityError: 'Microphone access is not permitted on this page.',
};

function describeMicFailure(err) {
    return MIC_ERRORS[err?.name] ?? `Microphone unavailable: ${err?.name ?? 'unknown error'}.`;
}

// getUserMedia only works in a secure browser context requiring localhost or HTTPS. 
// Provide explicit
function micSupportError() {
    if (!window.isSecureContext) {
        return 'Recording needs a secure connection. Open this page over HTTPS, or on localhost.';
    }
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
        return 'This browser does not support microphone recording.';
    }
    return null;
}

// Evaluated once - nothing it depends on changes over the life of the page.
const micUnsupported = micSupportError();

// ── View state ────────────────────────────────────────────────────────
// One div is shown at a time everything else carries the `hidden` attribute.
const views = document.querySelectorAll('.view');
const stage = document.querySelector('.stage');
const statusDot = document.getElementById('statusDot');
const statusTextEl = document.getElementById('statusText');
const stageGlow = document.getElementById('stageGlow');

function setStatus(status, text) {
    views.forEach((v) => { v.hidden = v.dataset.view !== status; });

    stageGlow.classList.toggle('recording', status === 'recording');

    statusDot.className = 'sdot';
    if (status === 'recording') statusDot.classList.add('rec');
    else if (status === 'transcribing') statusDot.classList.add('busy');
    else if (status === 'done' || status === 'usage') statusDot.classList.add('ok');

    statusTextEl.textContent = text || 'Ready';

    // Views that display the status message inside the stage itself
    if (status === 'transcribing') document.getElementById('transcribingText').textContent = text;
    if (status === 'oversized') document.getElementById('oversizedText').textContent = text;
    if (status === 'error') document.getElementById('errorText').textContent = text;
}

// ── Recording ─────────────────────────────────────────────────────────
// MediaRecorder gives out of the box processing and formatting to .webm,
// which the OpenAI transcription endpoint accepts directly — no PCM conversion
// needed on our side.
let mediaRecorder = null;
let stream = null;
let chunks = [];
let recorderFailed = false;   // set by onerror so onstop cannot upload a dead session
let recordingMimeType = '';   // what MediaRecorder actually chose, not what was asked for

// Find a mime type supported by browser in preference order
// Fallback '' which is any which should prevent error and choose any
function pickMimeType() {
    return AUDIO_MIME_TYPES.find((t) => MediaRecorder.isTypeSupported(t)) ?? '';
}

// The provider infers the audio format from the filename extension, not from the content type. 
// Extension has to follow whatever the browser actually recorded.
function extensionFor(mimeType) {
    return mimeType.startsWith('audio/mp4') ? 'm4a' : 'webm';
}

// Stops the tracks and drops the recorder.
function releaseMic() {
    if (stream) {
        stream.getTracks().forEach((t) => t.stop());
        stream = null;
    }
    mediaRecorder = null;
}

async function startRecording() {
    chunks = [];            // audio input buffer
    recorderFailed = false;

    // First attempt at this prompts for mic access permissions
    stream = await navigator.mediaDevices.getUserMedia({ audio: AUDIO_CONSTRAINTS });

    const preferred = pickMimeType();
    const options = { audioBitsPerSecond: AUDIO_BITS_PER_SECOND };
	
    if (preferred) options.mimeType = preferred;
    mediaRecorder = new MediaRecorder(stream, options);

    // Read the type back rather than trusting the request as the browser may pick something
    // else and both the blob and the filename have to agree on chosen mime type.
    recordingMimeType = mediaRecorder.mimeType || preferred || 'audio/webm';

    mediaRecorder.ondataavailable = (e) => {
		// A timeslice can deliver empty chunks, only push if data recorded
        if (e.data.size > 0) chunks.push(e.data);
    };

    // The recorder can die mid-session if the mic is unplugged or the OS takes the device
    // away. This prevents the UI sits on the recording view forever.
    mediaRecorder.onerror = (e) => {
        recorderFailed = true;
        releaseMic();
        setStatus('error', `Recording failed: ${e?.error?.name ?? 'unknown error'}.`);
    };

    // Stopping ends the session, the final chunk is packed into a blob and uploaded.
    mediaRecorder.onstop = () => {

        // onerror has already put the error view up
        if (recorderFailed) {
            chunks = [];
            return;
        }

        const blob = new Blob(chunks, { type: recordingMimeType });
        chunks = [];

        // Nothing captured at all
        if (blob.size === 0) {
            setStatus('error', 'No audio was captured. Check your microphone and try again.');
            return;
        }

        // Too big to upload
        if (blob.size > MAX_FILE_SIZE) {
            setStatus('oversized', `Recording stopped (${formatBytes(blob.size)}). Exceeds 25 MB limit, please try again.`);
        } else {
            sendRecording(blob);
        }
    };

    // Start recording, delivering encoded data every RECORDER_TIMESLICE_MS
    mediaRecorder.start(RECORDER_TIMESLICE_MS);
    setStatus('recording', 'Recording...');
}

// Releases the mic, onstop then handles the upload
function stopRecording() {
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
    }
    releaseMic();
}

// Guarded entry point for every path that starts a recording
async function beginRecording() {
    if (serverOnline === false) return;

    // Reported rather than silently ignored
    if (micUnsupported) {
        setStatus('error', micUnsupported);
        return;
    }

    try {
        await startRecording();
    } catch (err) {
        releaseMic();
        setStatus('error', describeMicFailure(err));
    }
}

// Send to server for STT processing
async function sendRecording(blob) {
    if (serverOnline === false) {
        setStatus('error', 'Not connected to server.');
        return;
    }
    setStatus('transcribing', 'Transcribing...');

    // Append the recorded audio under the extension matching its container
    const body = new FormData();
    body.append('audio', blob, `recording.${extensionFor(blob.type || recordingMimeType)}`);

    // Send to server, timing the round trip for the usage view
    const sentBytes = blob.size;
    const startedAt = performance.now();
    try {
        const res = await fetch(TRANSCRIBE_URL, {
            method: 'POST',
            body,
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
        });

        if (!res.ok) throw new Error(await describeFailure(res));
        const data = await res.json();
        lastRequest = { data, sentBytes, elapsedMs: performance.now() - startedAt };
        setStatus('done', 'Transcription complete.');
        renderTranscript(data);
    } catch (err) {
        setStatus('error', err.name === 'TimeoutError'
            ? 'The request timed out before the server answered. Please try again.'
            : err.message || 'The transcription request failed. Please try again.');
    }
}

// ── Transcript rendering ──────────────────────────────────────────────
// Shape comes from TranscriptionResult: { text, usage }
function renderTranscript(data) {
    const p = document.createElement('p');
    p.className = 'transcript-text';
    p.textContent = (data.text ?? '').trim();
    document.getElementById('transcript').replaceChildren(p);
}

// ── Usage view ────────────────────────────────────────────────────────
// Everything known about the last transcribe request
let lastRequest = null; // { data, sentBytes, elapsedMs }

const usageList = document.getElementById('usageList');

// Appends one <dt>/<dd> pair; `sub` dims it as a breakdown row
function addUsageRow(label, value, sub) {
    const dt = document.createElement('dt');
    const dd = document.createElement('dd');
    dt.textContent = label;
    dd.textContent = value;
    if (sub) { dt.className = 'sub'; dd.className = 'sub'; }
    usageList.append(dt, dd);
}

// Section heading, spans both grid columns
function addUsageHeading(text) {
    const h = document.createElement('div');
    h.className = 'usage-head';
    h.textContent = text;
    usageList.append(h);
}

function renderUsage() {
    usageList.replaceChildren();
    if (!lastRequest) {
        addUsageRow('status', 'No request in this session yet.');
        return;
    }

    const { data, sentBytes, elapsedMs } = lastRequest;
    const usage = data.usage ?? {};
    const words = (data.text ?? '').trim().split(/\s+/).filter(Boolean).length;

    addUsageHeading('Last request');
    addUsageRow('model', MODEL);
    addUsageRow('provider', 'openai');
    addUsageRow('audio sent', formatBytes(sentBytes));
    addUsageRow('response time', formatDuration(elapsedMs));
    addUsageRow('transcript', `${formatTokens(words)} words`);

    addUsageHeading('Token usage');
    addUsageRow('input tokens', formatTokens(usage.inputTokens));
    addUsageRow('audio', formatTokens(usage.audioTokens), true);
    addUsageRow('text', formatTokens(usage.textTokens), true);
    addUsageRow('output tokens', formatTokens(usage.outputTokens));
    addUsageRow('total tokens', formatTokens(usage.totalTokens));
}

// Drops the last transcription and returns to the record screen
function resetToIdle() {
    document.getElementById('transcript').replaceChildren();
    usageList.replaceChildren();
    lastRequest = null;
    chunks = [];
    setStatus('idle', '');
}

// The transcript/usage "New session" buttons: clear, then record again straight
// away. Offline it stops at idle, where beginRecording's guard leaves it.
function newSession() {
    resetToIdle();
    beginRecording();
}

// ── Server status header ──────────────────────────────────────────
// Polls AdminController's uptime endpoint once a second. 
const serverStatusEl = document.getElementById('serverStatus');
const serverDot = document.getElementById('serverDot');
const serverConnEl = document.getElementById('serverConn');
const serverUptimeEl = document.getElementById('serverUptime');

const offlineBanner = document.getElementById('offlineBanner');
const statusLine = document.getElementById('statusLine');
const idleTitle = document.getElementById('idleTitle');
const recordBtn = document.getElementById('recordBtn');

let uptimeInFlight = false;  // skip a tick rather than stacking slow requests
let serverOnline = null;     // null until the first poll resolves, then a bool

// Gates everything that needs the server.
function applyReachability(online) {
    if (online === serverOnline) return;
    serverOnline = online;

    offlineBanner.hidden = online;
    statusLine.hidden = !online;
    stageGlow.classList.toggle('offline', !online);

    setRecordingAvailable(online && !micUnsupported,
            micUnsupported ?? (online ? 'Click to start transcribing' : 'Not connected to server'));
}

// Single owner of the record button's enabled state and the idle view's caption.
function setRecordingAvailable(available, caption) {
    recordBtn.disabled = !available;
    // disabled stops the click; aria-disabled is what a screen reader reports.
    recordBtn.setAttribute('aria-disabled', String(!available));
    idleTitle.textContent = caption;
}

function setServerOffline() {
    applyReachability(false);
    serverStatusEl.classList.remove('up');
    serverStatusEl.classList.add('down');
    serverDot.className = 'sdot down';
    serverConnEl.textContent = 'Offline';
    serverUptimeEl.textContent = '--';
}

// Shape comes from UptimeResponse: { utcServerStart, utcNow, serverUptimeSeconds }
function renderServerStatus(data) {
    applyReachability(true);
    serverStatusEl.classList.remove('down');
    serverStatusEl.classList.add('up');
    serverDot.className = 'sdot up';
    serverConnEl.textContent = 'Connected';
    serverUptimeEl.textContent = formatUptime(data.serverUptimeSeconds ?? 0);
}

// ── Global token usage footer ─────────────────────────────────────
// Polls StatsController's global stats endpoint on the same tick as uptime.
const tokenStatsEl = document.getElementById('tokenStats');
const statsInputEl = document.getElementById('statsInput');
const statsOutputEl = document.getElementById('statsOutput');
const statsTotalEl = document.getElementById('statsTotal');

let statsInFlight = false;  // same skip-a-tick guard the uptime poll uses

// Shape comes from GlobalStatsResponse: { inputTokens, outputTokens }
function renderTokenStats(data) {
    const input = Number(data.inputTokens ?? 0);
    const output = Number(data.outputTokens ?? 0);
    tokenStatsEl.classList.remove('stale');
    statsInputEl.textContent = formatTokens(input);
    statsOutputEl.textContent = formatTokens(output);
    statsTotalEl.textContent = formatTokens(input + output);
}

// Keep the last known counts on screen, just dimmed
function setTokenStatsStale() {
    tokenStatsEl.classList.add('stale');
}

async function pollStats() {
    if (statsInFlight) return;
    statsInFlight = true;
    try {
        const res = await fetch(STATS_URL, { cache: 'no-store' });
        if (!res.ok) throw new Error(String(res.status));
        renderTokenStats(await res.json());
    } catch {
        setTokenStatsStale();
    } finally {
        statsInFlight = false;
    }
}

// Poll uptime/connection on set interval
async function pollUptime() {
    if (uptimeInFlight) return;
    uptimeInFlight = true;
    try {
        const res = await fetch(UPTIME_URL, { cache: 'no-store' });
        if (!res.ok) throw new Error(String(res.status));
        renderServerStatus(await res.json());
    } catch {
        setServerOffline();
    } finally {
        uptimeInFlight = false;
    }
}

function poll() {
    // Nothing on screen to update while the tab is hidden
    if (document.hidden) return;
    pollUptime();
    pollStats();
}

poll();
setInterval(poll, UPTIME_POLL_MS);

// Applied before the first poll lands, so an insecure context is visible immediately
if (micUnsupported) {
    setRecordingAvailable(false, micUnsupported);
}

// ── Interaction handlers ───────────────────────────────────────────
// idle → recording → (auto upload) → done, with New session looping back round
recordBtn.addEventListener('click', beginRecording);
document.getElementById('stopBtn').addEventListener('click', stopRecording);
document.getElementById('newSessionBtn').addEventListener('click', newSession);
document.getElementById('usageNewSessionBtn').addEventListener('click', newSession);

// Dead ends, both drop back to idle rather than straight into a recording
document.getElementById('oversizedResetBtn').addEventListener('click', resetToIdle);
document.getElementById('errorResetBtn').addEventListener('click', resetToIdle);

document.getElementById('usageBtn').addEventListener('click', () => {
    renderUsage();
    setStatus('usage', 'Transcription complete.');
});
document.getElementById('backToTranscriptBtn').addEventListener('click', () => {
    setStatus('done', 'Transcription complete.');
});
