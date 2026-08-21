'use strict';

// ── Constants ─────────────────────────────────────────────────────────
const MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB matching OpenAiProvider.MAX_FILE_SIZE
const TRANSCRIBE_URL = '/api/v1/transcribe';	// STT endpoint

// ── Helpers ───────────────────────────────────────────────────────────
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
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
// which the OpenAI Whisper endpoint accepts directly — no PCM conversion
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
                `${formatBytes(blob.size)} · openai · whisper-1`;
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
// Shape comes from OpenAiResponse: { task, language, duration, text, segments }
function renderTranscript(data) {
    document.getElementById('metadata').innerHTML =
        `<code>${data.task ?? 'transcribe'}</code> · ` +
        `<code>${data.language ?? 'unknown'}</code> · ` +
        `<code>${(data.duration ?? 0).toFixed(1)}s</code>`;

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

// ── Interaction handlers ───────────────────────────────────────────
document.getElementById('recordBtn').addEventListener('click', async () => {
    try {
        await startRecording();
    } catch (err) {
        setStatus('error', `Error: ${err.message}`);
    }
});

document.getElementById('stopBtn').addEventListener('click', stopRecording);
document.getElementById('sendBtn').addEventListener('click', sendRecording);
document.getElementById('discardBtn').addEventListener('click', clearRecording);
document.getElementById('oversizedResetBtn').addEventListener('click', clearRecording);
document.getElementById('errorResetBtn').addEventListener('click', resetToIdle);
document.getElementById('newSessionBtn').addEventListener('click', resetToIdle);
