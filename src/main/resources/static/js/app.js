'use strict';

// ── Constants ─────────────────────────────────────────────────────────
const MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB matching OpenAiProvider.MAX_FILE_SIZE
const TRANSCRIBE_URL = '/api/v1/transcribe';	// STT endpoint
const UPTIME_URL = '/api/v1/admin/uptime';		// admin uptime endpoint
const UPTIME_POLL_MS = 1000;					// header refresh interval

// ── Helpers ───────────────────────────────────────────────────────────
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// Renders UptimeResponse.utcServerStart (RFC 3339) in the viewer's local time
function formatStartTime(iso) {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '--';
    const pad = (n) => String(n).padStart(2, '0');
    return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ` +
           `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

// UptimeResponse.serverUptimeSeconds (double) as e.g. "2d 03:14:07" / "00:01:30"
function formatUptime(seconds) {
    const total = Math.max(0, Math.floor(seconds));
    const pad = (n) => String(n).padStart(2, '0');
    const days = Math.floor(total / 86400);
    const clock = `${pad(Math.floor(total / 3600) % 24)}:${pad(Math.floor(total / 60) % 60)}:${pad(total % 60)}`;
    return days > 0 ? `${days}d ${clock}` : clock;
}

// Maps the status codes TransciptionController returns to something readable
function describeFailure(status) {
    if (status === 400) return 'No audio was received by the server.';
    if (status === 413) return 'Recording exceeds the 25 MB upload limit.';
    if (status === 502) return 'Transcription provider is unavailable.';
    return `Request failed: ${status}`;
}

// ── View state ────────────────────────────────────────────────────────
// One div is shown at a time everything else carries the `hidden` attribute.
const views = document.querySelectorAll('.view');
const statusDot = document.getElementById('statusDot');
const statusTextEl = document.getElementById('statusText');
const stageGlow = document.getElementById('stageGlow');

function setStatus(status, text) {
    views.forEach((v) => { v.hidden = v.dataset.view !== status; });

    stageGlow.classList.toggle('recording', status === 'recording');

    statusDot.className = 'sdot';
    if (status === 'recording') statusDot.classList.add('rec');
    else if (status === 'transcribing') statusDot.classList.add('busy');
    else if (status === 'done') statusDot.classList.add('ok');

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
let blob = null;

async function startRecording() {
    chunks = [];	// audio input buffer
    blob = null;	// binary blob containing .webm file
	
	// First attempt at this prompts for mic access permissions
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
	
	// MediaRecorder API for direct to .webm conversion
    mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
	
    mediaRecorder.ondataavailable = (e) => chunks.push(e.data);	// push to buffer
	
	// On stop handler validates and formats to .webm binary
    mediaRecorder.onstop = () => {
        blob = new Blob(chunks, { type: 'audio/webm' });
		
		// Handle size maximum
        if (blob.size > MAX_FILE_SIZE) {
            setStatus('oversized', `Recording saved (${formatBytes(blob.size)}). Exceeds 25 MB limit, please try again.`);
        } else {
			// Before sending, show basic meta data and prompt confirmation
            document.getElementById('reviewMeta').textContent =
                `${formatBytes(blob.size)} · openai · gpt-4o-mini-transcribe`;
            setStatus('review', `Recording saved (${formatBytes(blob.size)}). Ready to send.`);
        }
    };
	
	// Start recording
    mediaRecorder.start();
    setStatus('recording', 'Recording...');
}

// Onstop moves us on to the review view once the final chunk lands
function stopRecording() {
    if (mediaRecorder) {
        mediaRecorder.stop();
        mediaRecorder = null;
    }
    if (stream) {
        stream.getTracks().forEach((t) => t.stop());
        stream = null;
    }
}

// Clear buffered audio
function clearRecording() {
    chunks = [];
    blob = null;
    if (mediaRecorder && mediaRecorder.state !== 'inactive') {
        mediaRecorder.stop();
    }
    mediaRecorder = null;
    if (stream) {
        stream.getTracks().forEach((t) => t.stop());
        stream = null;
    }
    setStatus('idle', 'Recording cleared.');
}

// Send to server for STT processing
async function sendRecording() {
    if (serverOnline === false) {
        setStatus('error', 'Not connected to server.');
        return;
    }
    if (!blob || blob.size > MAX_FILE_SIZE) {
        setStatus('error', 'Nothing valid to send.');
        return;
    }
    setStatus('transcribing', 'Transcribing...');
	
	// Append .webm binary
    const body = new FormData();
    body.append('audio', blob, 'recording.webm');
	
	
	// Send to server
    try {
        const res = await fetch(TRANSCRIBE_URL, { method: 'POST', body });
        if (!res.ok) throw new Error(describeFailure(res.status));
        renderTranscript(await res.json());
        setStatus('done', 'Transcription complete.');
    } catch (err) {
        setStatus('error', 'Error: ' + err.message);
    }
    blob = null;
    chunks = [];
}

// ── Transcript rendering ──────────────────────────────────────────────
// Shape comes from OpenAi4oResponse: { text, usage }
function renderTranscript(data) {
    const usage = data.usage ?? {};
    const tokens = (n) => (n ?? 0).toLocaleString();
    document.getElementById('metadata').innerHTML =
        `<code>gpt-4o-mini-transcribe</code> · token usage · ` +
        `<code>input ${tokens(usage.input_tokens)}</code> · ` +
        `<code>output ${tokens(usage.output_tokens)}</code> · ` +
        `<code>total ${tokens(usage.total_tokens)}</code>`;

    const p = document.createElement('p');
    p.className = 'transcript-text';
    p.textContent = (data.text ?? '').trim();
    document.getElementById('transcript').replaceChildren(p);
}

function resetToIdle() {
    document.getElementById('metadata').replaceChildren();
    document.getElementById('transcript').replaceChildren();
    setStatus('idle', '');
}

// ── Server status header ──────────────────────────────────────────
// Polls AdminController's uptime endpoint once a second. 
const serverStatusEl = document.getElementById('serverStatus');
const serverDot = document.getElementById('serverDot');
const serverConnEl = document.getElementById('serverConn');
const serverStartEl = document.getElementById('serverStart');
const serverUptimeEl = document.getElementById('serverUptime');

const offlineBanner = document.getElementById('offlineBanner');
const statusLine = document.getElementById('statusLine');
const idleTitle = document.getElementById('idleTitle');
const recordBtn = document.getElementById('recordBtn');
const sendBtn = document.getElementById('sendBtn');

let uptimeInFlight = false;	// skip a tick rather than stacking slow requests
let serverOnline = null;	// null until the first poll resolves, then a bool

// Gates everything that needs the server.
function applyReachability(online) {
    if (online === serverOnline) return;
    serverOnline = online;

    offlineBanner.hidden = online;
    statusLine.hidden = !online;
    stageGlow.classList.toggle('offline', !online);

    recordBtn.disabled = !online;
    sendBtn.disabled = !online;
    idleTitle.textContent = online ? 'Click to start transcribing' : 'Not connected to server';
}

function setServerOffline() {
    applyReachability(false);
    serverStatusEl.classList.remove('up');
    serverStatusEl.classList.add('down');
    serverDot.className = 'sdot down';
    serverConnEl.textContent = 'Offline';
    serverStartEl.textContent = '--';
    serverUptimeEl.textContent = '--';
}

// Shape comes from UptimeResponse: { utcServerStart, utcNow, serverUptimeSeconds }
function renderServerStatus(data) {
    applyReachability(true);
    serverStatusEl.classList.remove('down');
    serverStatusEl.classList.add('up');
    serverDot.className = 'sdot up';
    serverConnEl.textContent = 'Connected';
    serverStartEl.textContent = formatStartTime(data.utcServerStart);
    serverUptimeEl.textContent = formatUptime(data.serverUptimeSeconds ?? 0);
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

pollUptime();
setInterval(pollUptime, UPTIME_POLL_MS);

// ── Interaction handlers ───────────────────────────────────────────
recordBtn.addEventListener('click', async () => {
    if (serverOnline === false) return;
    try {
        await startRecording();
    } catch (err) {
        setStatus('error', `Error: ${err.message}`);
    }
});

document.getElementById('stopBtn').addEventListener('click', stopRecording);
sendBtn.addEventListener('click', sendRecording);
document.getElementById('discardBtn').addEventListener('click', clearRecording);
document.getElementById('oversizedResetBtn').addEventListener('click', clearRecording);
document.getElementById('errorResetBtn').addEventListener('click', resetToIdle);
document.getElementById('newSessionBtn').addEventListener('click', resetToIdle);
