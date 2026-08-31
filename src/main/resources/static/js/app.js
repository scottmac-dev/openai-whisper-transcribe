'use strict';

// ── Constants ─────────────────────────────────────────────────────────
const MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB matching OpenAiProvider.MAX_FILE_SIZE
const MODEL = 'gpt-4o-mini-transcribe';	// matching OpenAiProvider.MODEL
const TRANSCRIBE_URL = '/api/v1/transcribe';	// STT endpoint
const UPTIME_URL = '/api/v1/admin/uptime';		// admin uptime endpoint
const STATS_URL = '/api/v1/global/stats';		// global token usage endpoint
const UPTIME_POLL_MS = 1000;					// header/footer refresh interval

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

async function startRecording() {
    chunks = [];    // audio input buffer

    // First attempt at this prompts for mic access permissions
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });

    // MediaRecorder API for direct to .webm conversion
    mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });

    mediaRecorder.ondataavailable = (e) => chunks.push(e.data);  // push to buffer

    // Stopping ends the session: the final chunk is packed into a .webm blob
    // and uploaded straight away, no confirmation step in between.
    mediaRecorder.onstop = () => {
        const blob = new Blob(chunks, { type: 'audio/webm' });
        chunks = [];

        // Too big to upload — the only path that does not reach the server
        if (blob.size > MAX_FILE_SIZE) {
            setStatus('oversized', `Recording stopped (${formatBytes(blob.size)}). Exceeds 25 MB limit, please try again.`);
        } else {
            sendRecording(blob);
        }
    };

    // Start recording
    mediaRecorder.start();
    setStatus('recording', 'Recording...');
}

// Releases the mic, onstop then handles the upload
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

// Guarded entry point for every path that starts a recording
async function beginRecording() {
    if (serverOnline === false) return;
    try {
        await startRecording();
    } catch (err) {
        setStatus('error', `Error: ${err.message}`);
    }
}

// Send to server for STT processing
async function sendRecording(blob) {
    if (serverOnline === false) {
        setStatus('error', 'Not connected to server.');
        return;
    }
    setStatus('transcribing', 'Transcribing...');

    // Append .webm binary
    const body = new FormData();
    body.append('audio', blob, 'recording.webm');

    // Send to server, timing the round trip for the usage view
    const sentBytes = blob.size;
    const startedAt = performance.now();
    try {
        const res = await fetch(TRANSCRIBE_URL, { method: 'POST', body });
        if (!res.ok) throw new Error(describeFailure(res.status));
        const data = await res.json();
        lastRequest = { data, sentBytes, elapsedMs: performance.now() - startedAt };
        renderTranscript(data);
        setStatus('done', 'Transcription complete.');
    } catch (err) {
        setStatus('error', 'Error: ' + err.message);
    }
}

// ── Transcript rendering ──────────────────────────────────────────────
// Shape comes from OpenAiTranscribeResponse: { text, usage }
function renderTranscript(data) {
    const p = document.createElement('p');
    p.className = 'transcript-text';
    p.textContent = (data.text ?? '').trim();
    document.getElementById('transcript').replaceChildren(p);
}

// ── Usage view ────────────────────────────────────────────────────────
// Everything known about the last transcribe request
let lastRequest = null;	// { data, sentBytes, elapsedMs }

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
    const details = usage.input_token_details ?? {};
    const words = (data.text ?? '').trim().split(/\s+/).filter(Boolean).length;

    addUsageHeading('Last request');
    addUsageRow('model', MODEL);
    addUsageRow('provider', 'openai');
    addUsageRow('audio sent', formatBytes(sentBytes));
    addUsageRow('response time', formatDuration(elapsedMs));
    addUsageRow('transcript', `${formatTokens(words)} words`);

    addUsageHeading('Token usage');
    addUsageRow('input tokens', formatTokens(usage.input_tokens));
    addUsageRow('audio', formatTokens(details.audio_tokens), true);
    addUsageRow('text', formatTokens(details.text_tokens), true);
    addUsageRow('output tokens', formatTokens(usage.output_tokens));
    addUsageRow('total tokens', formatTokens(usage.total_tokens));
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
    idleTitle.textContent = online ? 'Click to start transcribing' : 'Not connected to server';
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

let statsInFlight = false;	// same skip-a-tick guard the uptime poll uses

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
    pollUptime();
    pollStats();
}

poll();
setInterval(poll, UPTIME_POLL_MS);

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
