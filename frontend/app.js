(function () {
    'use strict';

    const API = 'http://localhost:8080/api';
    const LS_CONV = 'vector_conversations';
    const LS_ACTIVE = 'vector_active_conv';
    const MAX_CONV = 50;

    /* ---------- state ---------- */
    let loading = false;
    let currentMode = 'quick';
    let conversations = [];
    let activeId = null;
    let currentStreamEs = null;
    let userScrolledUp = false;

    /* ---------- DOM refs ---------- */
    const $ = s => document.querySelector(s);
    const chat = $('#chat');
    const input = $('#input');
    const sendBtn = $('#send-btn');
    const micBtn = $('#mic-btn');
    const statusDot = $('#status-badge');
    const statusLabel = document.querySelector('.status-label');
    const modeLabel = $('#mode-label');
    const statModel = $('#stat-model');
    const statReqs = $('#stat-requests');
    const statCache = $('#stat-cache');
    const welcome = $('#welcome');
    const modeBtns = document.querySelectorAll('.nav-btn');
    const convList = $('#conversation-list');
    const newChatBtn = $('#new-chat-btn');
    const clearBtn = $('#clear-chat-btn');
    const stopBtn = $('#stop-btn');

    const modeNames = { quick: 'Quick Answer', study: 'Study Mode', debug: 'Debug Mode' };
    const modeHints = {
        quick: 'Ask anything, get an answer',
        study: 'Deep dive with detailed explanations',
        debug: 'Step-by-step debugging and fixes',
    };

    /* ---------- helpers ---------- */
    function esc(s) {
        const d = document.createElement('div');
        d.textContent = s;
        return d.innerHTML;
    }

    function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 8); }

    function timeAgo(ts) {
        const sec = Math.floor((Date.now() - ts) / 1000);
        if (sec < 60) return 'just now';
        if (sec < 3600) return Math.floor(sec / 60) + 'm ago';
        if (sec < 86400) return Math.floor(sec / 3600) + 'h ago';
        return Math.floor(sec / 86400) + 'd ago';
    }

    function convTitle(messages) {
        const first = messages.find(m => m.role === 'user');
        if (!first) return 'New chat';
        const t = first.content.slice(0, 40);
        return t.length < first.content.length ? t + '...' : t;
    }

    /* ---------- fetch wrapper ---------- */
    async function api(path, opts, timeoutMs) {
        const ms = timeoutMs || 30_000;
        const ctrl = new AbortController();
        const id = setTimeout(() => ctrl.abort(), ms);
        try {
            const res = await fetch(API + path, { ...opts, signal: ctrl.signal });
            clearTimeout(id);
            return res;
        } catch (e) {
            clearTimeout(id);
            throw e;
        }
    }

    /* ---------- markdown ---------- */
    function renderMarkdown(t) {
        if (!t) return '';
        let s = esc(t);
        s = s.replace(/^### (.+)$/gm, '<h3>$1</h3>');
        s = s.replace(/^## (.+)$/gm, '<h2>$1</h2>');
        s = s.replace(/^# (.+)$/gm, '<h1>$1</h1>');
        s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        s = s.replace(/\*(.+?)\*/g, '<em>$1</em>');
        s = s.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');
        s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
        s = s.replace(/^[*-] (.+)$/gm, '<li>$1</li>');
        s = s.replace(/^\d+\.\s(.+)$/gm, '<li>$1</li>');
        s = s.replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>');
        s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
        s = s.replace(/(?:^|\n)\|(.+)\|\n\|[-:| ]+\|\n((?:\|.+\|\n?)*)/gm, (match, header, body) => {
            const headers = header.split('|').map(h => h.trim()).filter(h => h);
            const rows = body.trim().split('\n').map(row =>
                '<tr>' + row.split('|').map(c => c.trim()).filter(c => c).map(c => '<td>' + c + '</td>').join('') + '</tr>'
            ).join('');
            return '<table><thead><tr>' + headers.map(h => '<th>' + h + '</th>').join('') + '</tr></thead><tbody>' + rows + '</tbody></table>';
        });
        s = s.replace(/\n\n/g, '</p><p>');
        s = '<p>' + s + '</p>';
        s = s.replace(/(<li>.*?<\/li>(\s*<li>.*?<\/li>)*)/g, '<ul>$1</ul>');
        s = s.replace(/<p><\/p>/g, '');
        return s;
    }

    function renderAnswer(raw) {
        if (!raw) return '';
        const parts = [];
        let last = 0;
        const re = /<think>([\s\S]*?)<\/think>/g;
        let match;
        while ((match = re.exec(raw)) !== null) {
            if (match.index > last) parts.push({ type: 'text', content: raw.slice(last, match.index) });
            parts.push({ type: 'think', content: match[1].trim() });
            last = match.index + match[0].length;
        }
        if (last < raw.length) parts.push({ type: 'text', content: raw.slice(last) });
        if (!parts.length) return renderMarkdown(raw);

        let html = '';
        for (const p of parts) {
            if (p.type === 'think') {
                html += '<details class="think-block" open><summary>Thinking</summary><div class="think-content">' + esc(p.content) + '</div></details>';
            } else {
                html += renderMarkdown(p.content || '');
            }
        }
        return html;
    }

    function renderFooter(source, model, time) {
        const s = (source || '').toLowerCase();
        let cls = 'model', label = source || 'model';
        if (s.includes('cache')) { cls = 'cached'; label = 'cached'; }
        else if (s.includes('wikipedia')) { cls = 'wikipedia'; label = 'wiki'; }

        let h = '<div class="msg-footer">';
        h += '<span class="source-badge ' + cls + '">' + label + '</span>';
        if (model && !s.includes('cache')) h += '<span class="msg-time">' + esc(model) + '</span>';
        if (time) h += '<span class="msg-time">' + time + 'ms</span>';
        h += '<button class="copy-btn" title="Copy" data-text="">'
            + '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>'
            + '</button>';
        h += '</div>';
        return h;
    }

    /* ---------- conversation persistence ---------- */
    function saveConversations() {
        localStorage.setItem(LS_CONV, JSON.stringify(conversations));
        if (activeId) localStorage.setItem(LS_ACTIVE, activeId);
    }

    function loadConversations() {
        try {
            const data = JSON.parse(localStorage.getItem(LS_CONV) || '[]');
            conversations = Array.isArray(data) ? data : [];
        } catch { conversations = []; }
        activeId = localStorage.getItem(LS_ACTIVE) || null;
        if (activeId && !conversations.find(c => c.id === activeId)) activeId = null;
    }

    function getActive() {
        return conversations.find(c => c.id === activeId) || null;
    }

    function createConversation() {
        const c = { id: uid(), messages: [], createdAt: Date.now(), updatedAt: Date.now() };
        conversations.unshift(c);
        if (conversations.length > MAX_CONV) conversations.pop();
        activeId = c.id;
        saveConversations();
        return c;
    }

    function deleteConversation(id) {
        conversations = conversations.filter(c => c.id !== id);
        if (activeId === id) {
            activeId = conversations.length ? conversations[0].id : null;
        }
        saveConversations();
        renderConvList();
    }

    function pushMessage(role, content, opts) {
        const conv = getActive() || createConversation();
        const msg = { role, content, ...opts, ts: Date.now() };
        conv.messages.push(msg);
        conv.updatedAt = Date.now();
        saveConversations();
        renderConvList();
        return msg;
    }

    function updateLastMessage(updates) {
        const conv = getActive();
        if (!conv || !conv.messages.length) return;
        Object.assign(conv.messages[conv.messages.length - 1], updates);
        saveConversations();
    }

    /* ---------- render conversation list ---------- */
    function renderConvList() {
        if (!convList) return;
        if (!conversations.length) {
            convList.innerHTML = '<div class="conv-empty">No conversations yet</div>';
            return;
        }
        convList.innerHTML = conversations.map(c => {
            const active = c.id === activeId ? 'active' : '';
            const title = convTitle(c.messages);
            const date = c.messages.length ? timeAgo(c.messages[c.messages.length - 1].ts) : '';
            return '<div class="conv-item ' + active + '" data-id="' + c.id + '">'
                + '<span class="conv-item-title">' + esc(title) + '</span>'
                + '<span class="conv-item-date">' + date + '</span>'
                + '<button class="conv-item-delete" data-id="' + c.id + '" title="Delete">'
                + '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>'
                + '</button>'
                + '</div>';
        }).join('');

        convList.querySelectorAll('.conv-item').forEach(el => {
            el.addEventListener('click', (e) => {
                if (e.target.closest('.conv-item-delete')) return;
                switchConversation(el.dataset.id);
            });
        });
        convList.querySelectorAll('.conv-item-delete').forEach(el => {
            el.addEventListener('click', (e) => {
                e.stopPropagation();
                deleteConversation(el.dataset.id);
                if (!getActive()) newConversation();
                else loadConversation(getActive());
            });
        });
    }

    /* ---------- switch conversation ---------- */
    function switchConversation(id) {
        if (currentStreamEs) { currentStreamEs.close(); currentStreamEs = null; }
        activeId = id;
        saveConversations();
        renderConvList();
        loadConversation(getActive());
    }

    function newConversation() {
        if (currentStreamEs) { currentStreamEs.close(); currentStreamEs = null; }
        createConversation();
        renderConvList();
        clearChatUI();
        showWelcome();
    }

    function clearCurrentConversation() {
        const conv = getActive();
        if (conv) {
            conv.messages = [];
            conv.updatedAt = Date.now();
            saveConversations();
            renderConvList();
        }
        clearChatUI();
        showWelcome();
    }

    /* ---------- load conversation into UI ---------- */
    function loadConversation(conv) {
        clearChatUI();
        if (!conv || !conv.messages.length) { showWelcome(); return; }
        for (const msg of conv.messages) {
            if (msg.role === 'user') {
                addMsgDOM(msg.content, { isUser: true, save: false });
            } else {
                addMsgDOM(msg.content, {
                    save: false,
                    skipFooter: true,
                    source: msg.source,
                    model: msg.model,
                    time: msg.time,
                });
            }
        }
    }

    function clearChatUI() {
        chat.innerHTML = '';
    }

    function showWelcome() {
        if (welcome) chat.appendChild(welcome);
    }

    /* ---------- render DOM message ---------- */
    function addMsgDOM(text, opts) {
        opts = opts || {};
        const isUser = opts.isUser || false;
        const source = opts.source || '';
        const model = opts.model || '';
        const time = opts.time || 0;
        const save = opts.save !== false;
        const skipFooter = opts.skipFooter || false;

        if (welcome && welcome.parentNode) welcome.remove();

        const div = document.createElement('div');
        div.className = 'msg ' + (isUser ? 'user' : 'assistant');

        const avatar = document.createElement('div');
        avatar.className = 'msg-avatar';
        avatar.textContent = isUser ? 'U' : 'V';

        const body = document.createElement('div');
        body.className = 'msg-body';

        const txt = document.createElement('div');
        txt.className = 'msg-text';
        if (isUser) {
            txt.textContent = text;
        } else {
            txt.innerHTML = renderAnswer(text);
        }
        body.appendChild(txt);

        if (!isUser && !skipFooter) {
            const footerHtml = renderFooter(source, model, time);
            const temp = document.createElement('div');
            temp.innerHTML = footerHtml;
            body.appendChild(temp.firstElementChild);
            // wire copy
            const copyBtn = body.querySelector('.copy-btn');
            if (copyBtn) {
                copyBtn.dataset.text = text.replace(/<think>[\s\S]*?<\/think>/g, '').trim();
                copyBtn.addEventListener('click', copyHandler);
            }
        }

        div.appendChild(avatar);
        div.appendChild(body);
        chat.appendChild(div);
        scrollToBottom();
        return div;
    }

    function addMsg(text, opts) {
        opts = opts || {};
        const isUser = opts.isUser || false;
        const save = opts.save !== false;

        if (isUser && save) {
            pushMessage('user', text);
        }

        return addMsgDOM(text, opts);
    }

    /* ---------- copy handler ---------- */
    function copyHandler(e) {
        const btn = e.currentTarget;
        const text = btn.dataset.text;
        if (!text) return;
        navigator.clipboard.writeText(text).then(() => {
            btn.classList.add('copied');
            btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>';
            setTimeout(() => {
                btn.classList.remove('copied');
                btn.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>';
            }, 2000);
        }).catch(() => {});
    }

    /* ---------- typing indicator ---------- */
    function showTyping() {
        const d = document.createElement('div');
        d.className = 'msg assistant typing';
        d.id = 'typing-indicator';
        const a = document.createElement('div');
        a.className = 'msg-avatar';
        a.textContent = 'V';
        const b = document.createElement('div');
        b.className = 'msg-body';
        const t = document.createElement('div');
        t.className = 'msg-text';
        t.textContent = 'Thinking';
        b.appendChild(t);
        d.appendChild(a);
        d.appendChild(b);
        chat.appendChild(d);
        scrollToBottom();
    }

    function removeTyping() {
        const el = document.getElementById('typing-indicator');
        if (el) el.remove();
    }

    /* ---------- scroll ---------- */
    function scrollToBottom() {
        if (userScrolledUp) return;
        chat.scrollTop = chat.scrollHeight;
    }

    chat.addEventListener('scroll', () => {
        const threshold = 60;
        userScrolledUp = chat.scrollHeight - chat.scrollTop - chat.clientHeight > threshold;
    });

    /* ---------- send ---------- */
    async function send() {
        const q = input.value.trim();
        if (!q || loading) return;

        let question = q;
        if (currentMode === 'study') question = 'Explain this in detail: ' + q;
        else if (currentMode === 'debug') question = 'Debug this step by step, identify the cause and suggest fixes: ' + q;

        loading = true;
        sendBtn.disabled = true;
        if (stopBtn) stopBtn.classList.add('visible');

        // Ensure we have an active conversation
        if (!getActive()) newConversation();

        pushMessage('user', q);
        addMsgDOM(q, { isUser: true, save: false });

        input.value = '';
        input.style.height = 'auto';

        showTyping();

        try {
            await sendStream(question);
        } catch (e) {
            removeTyping();
            addMsgDOM('Connection error: ' + e.message + '. Check that the backend is running on port 8080.', { source: 'error', skipFooter: false });
        }

        loading = false;
        sendBtn.disabled = false;
        if (stopBtn) stopBtn.classList.remove('visible');
        currentStreamEs = null;
        input.focus();
        pollStats();
    }

    async function sendStream(q) {
        const url = API + '/ask/stream?question=' + encodeURIComponent(q);

        removeTyping();
        const msgDiv = addMsgDOM('', { save: false, skipFooter: true });
        const txt = msgDiv.querySelector('.msg-text');
        const body = msgDiv.querySelector('.msg-body');
        const start = Date.now();
        let raw = '';

        // Store footer data to add later
        let footerAdded = false;

        return new Promise((resolve, reject) => {
            const es = new EventSource(url);
            currentStreamEs = es;
            let done = false;

            const finish = (err) => {
                if (done) return;
                done = true;
                es.close();
                if (currentStreamEs === es) currentStreamEs = null;

                const elapsed = Date.now() - start;

                if (err) {
                    txt.textContent = 'Stream error: ' + err;
                    if (!footerAdded) {
                        const footerHtml = renderFooter('error', '', 0);
                        const temp = document.createElement('div');
                        temp.innerHTML = footerHtml;
                        body.appendChild(temp.firstElementChild);
                        footerAdded = true;
                    }
                    reject(err);
                    return;
                }

                // Save to conversation
                const cleanText = raw.replace(/<think>[\s\S]*?<\/think>/g, '').trim();
                updateLastMessage({ content: raw, source: 'local model', time: elapsed });

                // Add footer
                if (!footerAdded && body) {
                    const footerHtml = renderFooter('local model', '', elapsed);
                    const temp = document.createElement('div');
                    temp.innerHTML = footerHtml;
                    body.appendChild(temp.firstElementChild);
                    const copyBtn = body.querySelector('.copy-btn');
                    if (copyBtn) {
                        copyBtn.dataset.text = cleanText;
                        copyBtn.addEventListener('click', copyHandler);
                    }
                    footerAdded = true;
                }

                renderConvList();
                resolve();
            };

            es.onmessage = function(event) {
                if (done) return;
                let payload;
                try { payload = JSON.parse(event.data); }
                catch { return; }
                if (payload === '[DONE]') { finish(); return; }
                if (!payload) return;
                raw += payload;
                txt.innerHTML = renderAnswer(raw);
                scrollToBottom();
            };

            es.onerror = function() {
                finish('Connection lost');
            };

            setTimeout(() => finish('Timed out'), 120_000);
        });
    }

    /* ---------- STT ---------- */
    let sttStream = null;
    let sttRecorder = null;
    let sttChunks = [];
    let sttStart = 0;

    function micIcon(which) {
        if (which === 'recording') {
            return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a3 3 0 00-3 3v7a3 3 0 006 0V5a3 3 0 00-3-3z"/><path d="M19 10v2a7 7 0 01-14 0v-2"/><line x1="12" y1="19" x2="12" y2="22"/><circle cx="12" cy="12" r="8" fill="red" fill-opacity="0.2"/></svg>';
        }
        return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a3 3 0 00-3 3v7a3 3 0 006 0V5a3 3 0 00-3-3z"/><path d="M19 10v2a7 7 0 01-14 0v-2"/><line x1="12" y1="19" x2="12" y2="22"/></svg>';
    }

    async function startSTT() {
        if (micBtn.classList.contains('recording') || micBtn.classList.contains('loading')) return;
        try {
            sttStream = await navigator.mediaDevices.getUserMedia({
                audio: { channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false }
            });
        } catch (e) {
            addMsgDOM('Microphone access denied.', { source: 'error', skipFooter: false });
            return;
        }

        sttChunks = [];
        sttStart = Date.now();

        const track = sttStream.getAudioTracks()[0];
        if (track) {
            console.log('[STT] Audio track settings:', JSON.stringify(track.getSettings()));
            console.log('[STT] Audio track capabilities:', JSON.stringify(track.getCapabilities()));
        }

        try {
            const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
                ? 'audio/webm;codecs=opus'
                : MediaRecorder.isTypeSupported('audio/ogg;codecs=opus')
                    ? 'audio/ogg;codecs=opus' : '';
            sttRecorder = new MediaRecorder(sttStream, { mimeType: mime || undefined, audioBitsPerSecond: 96000 });
        } catch { sttRecorder = new MediaRecorder(sttStream); }

        sttRecorder.ondataavailable = e => { if (e.data.size > 0) sttChunks.push(e.data); };
        sttRecorder.start(250);

        micBtn.classList.add('recording');
        micBtn.innerHTML = micIcon('recording');
        micBtn.title = 'Stop recording';
        console.log('[STT] Recording...');
    }

    async function stopSTT() {
        if (!sttRecorder || sttRecorder.state === 'inactive') return;
        micBtn.classList.remove('recording');
        micBtn.classList.add('loading');
        micBtn.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>';

        const recorder = sttRecorder;
        sttRecorder = null;
        const stream = sttStream;
        sttStream = null;

        const duration = Date.now() - sttStart;
        console.log('[STT] Duration: ' + duration + 'ms');

        const blob = await new Promise(resolve => {
            recorder.onstop = async () => {
                await new Promise(r => setTimeout(r, 150));
                const b = new Blob(sttChunks, { type: recorder.mimeType || 'audio/webm' });
                console.log('[STT] Recorded ' + (b.size / 1024).toFixed(0) + 'KB');
                resolve(b);
            };
            if (recorder.state === 'recording') recorder.requestData();
            recorder.stop();
        });

        stream.getTracks().forEach(t => t.stop());
        console.log({ duration, size: blob.size, type: blob.type, chunks: sttChunks.length });

        try {
            const ext = recorder.mimeType && recorder.mimeType.includes('ogg') ? 'ogg' : 'webm';
            const formData = new FormData();
            formData.append('file', blob, 'recording.' + ext);
            const res = await api('/stt', { method: 'POST', body: formData }, 45_000);
            if (!res.ok) { const err = await res.json(); throw new Error(err.error || 'STT failed'); }
            const data = await res.json();
            if (data.text) {
                input.value = data.text;
                input.style.height = 'auto';
                input.style.height = Math.min(input.scrollHeight, 120) + 'px';
                sendBtn.disabled = false;
                input.focus();
            }
        } catch (e) {
            addMsgDOM('Speech-to-text error: ' + e.message, { source: 'error', skipFooter: false });
        }
        micBtn.classList.remove('loading');
        micBtn.innerHTML = micIcon();
        micBtn.title = 'Speak your question';
    }

    micBtn.addEventListener('click', () => {
        if (micBtn.classList.contains('recording')) stopSTT();
        else if (!micBtn.classList.contains('loading')) startSTT();
    });

    /* ---------- events ---------- */
    sendBtn.addEventListener('click', send);

    input.addEventListener('input', () => {
        sendBtn.disabled = !input.value.trim();
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 120) + 'px';
    });

    input.addEventListener('keydown', e => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
    });

    modeBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            modeBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentMode = btn.dataset.mode;
            if (modeLabel) modeLabel.textContent = modeNames[currentMode] || 'Quick Answer';
            const hint = document.querySelector('.topbar-hint');
            if (hint) hint.textContent = modeHints[currentMode] || 'Ask anything';
        });
    });

    newChatBtn.addEventListener('click', newConversation);
    clearBtn.addEventListener('click', clearCurrentConversation);
    stopBtn.addEventListener('click', () => {
        if (currentStreamEs) { currentStreamEs.close(); currentStreamEs = null; }
        stopBtn.classList.remove('visible');
    });

    document.querySelectorAll('.suggestion').forEach(btn => {
        btn.addEventListener('click', () => {
            input.value = btn.dataset.q;
            input.style.height = 'auto';
            input.style.height = Math.min(input.scrollHeight, 120) + 'px';
            sendBtn.disabled = false;
            input.focus();
        });
    });

    /* ---------- health / stats ---------- */
    async function pollHealth() {
        try {
            const data = await (await api('/health')).json();
            const ok = data.status === 'healthy';
            statusDot.className = 'status-dot ' + (ok ? 'online' : 'offline');
            if (statusLabel) statusLabel.textContent = ok ? 'Online' : 'Error';
        } catch {
            statusDot.className = 'status-dot offline';
            if (statusLabel) statusLabel.textContent = 'Offline';
        }
    }

    async function pollStats() {
        try {
            const data = await (await api('/stats')).json();
            statModel.textContent = data.model || '—';
            statReqs.textContent = (data.total_requests || 0) + ' req';
            statCache.textContent = (data.answer_cache_size || 0) + ' cached';
        } catch { /* ignore */ }
    }

    /* ---------- init ---------- */
    loadConversations();
    renderConvList();
    const active = getActive();
    if (active) {
        loadConversation(active);
    }
    pollHealth();
    pollStats();
    setInterval(pollHealth, 10_000);
    setInterval(pollStats, 30_000);
    input.focus();
})();
