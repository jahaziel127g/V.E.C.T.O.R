(function() {
    'use strict';

    // Simple markdown parser
    function parseMarkdown(text) {
        if (!text) return '';
        let html = text
            // Escape HTML first
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            // Headers
            .replace(/^### (.*$)/gm, '<h3>$1</h3>')
            .replace(/^## (.*$)/gm, '<h2>$1</h2>')
            .replace(/^# (.*$)/gm, '<h1>$1</h1>')
            // Bold
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/__(.*?)__/g, '<strong>$1</strong>')
            // Italic
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            .replace(/_(.*?)_/g, '<em>$1</em>')
            // Code blocks
            .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
            // Inline code
            .replace(/`(.*?)`/g, '<code>$1</code>')
            // Lists
            .replace(/^\* (.*$)/gm, '<li>$1</li>')
            .replace(/^- (.*$)/gm, '<li>$1</li>')
            .replace(/^\d+\. (.*$)/gm, '<li>$1</li>')
            // Blockquotes
            .replace(/^> (.*$)/gm, '<blockquote>$1</blockquote>')
            // Links
            .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
            // Line breaks
            .replace(/\n/g, '<br>');
        
        // Wrap consecutive <li> in <ul>
        html = html.replace(/(<li>.*<\/li>)(<br>)?/g, '<ul>$1</ul>');
        return html;
    }

    // Auto-detect API URL from current page
const API_BASE = `${window.location.protocol}//${window.location.hostname}:8080/api`;
    const TYPING_CLASS = 'typing';
    const MSG_USER = 'user';
    const MSG_ASSISTANT = 'assistant';
    const MAX_INPUT_HEIGHT = 150;

    const el = {
        input: document.getElementById('question-input'),
        sendBtn: document.getElementById('send-btn'),
        chat: document.getElementById('chat-container'),
        streamToggle: document.getElementById('stream-toggle'),
        modelName: document.getElementById('model-name'),
        totalRequests: document.getElementById('total-requests'),
        cachedCount: document.getElementById('cached-count'),
        status: document.getElementById('status')
    };

    let resizeTimeout;

    async function updateStats() {
        try {
            const data = await (await fetch(`${API_BASE}/stats`)).json();
            el.modelName.textContent = data.model || '-';
            el.totalRequests.textContent = data.total_requests || 0;
            el.cachedCount.textContent = (data.answer_cache_size || 0) + (data.wiki_cache_size || 0);
        } catch (e) {
            console.error('Stats error:', e);
        }
    }

    async function checkHealth() {
        try {
            const data = await (await fetch(`${API_BASE}/health`)).json();
            el.status.textContent = data.status === 'healthy' ? 'Online' : 'Error';
            el.status.classList.remove('error');
        } catch (e) {
            el.status.textContent = 'Offline';
            el.status.classList.add('error');
        }
    }

    function addMessage(content, isUser, model, time) {
        const div = document.createElement('div');
        div.className = isUser ? `message ${MSG_USER}` : `message ${MSG_ASSISTANT}`;
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        
        if (isUser) {
            contentDiv.textContent = content;
        } else {
            contentDiv.innerHTML = parseMarkdown(content);
        }
        div.appendChild(contentDiv);

        if (model || time) {
            const meta = document.createElement('div');
            meta.className = 'meta';
            meta.textContent = [model, time ? `${time}ms` : null].filter(Boolean).join(' • ');
            div.appendChild(meta);
        }

        el.chat.appendChild(div);
        el.chat.scrollTop = el.chat.scrollHeight;
    }

    function setLoading(loading) {
        el.sendBtn.disabled = loading;
        el.input.disabled = loading;
    }

    async function sendQuestion() {
        const question = el.input.value.trim();
        if (!question) return;

        setLoading(true);
        addMessage(question, true);
        el.input.value = '';
        el.input.style.height = '48px';

        try {
            if (el.streamToggle.checked) {
                await handleStreaming(question);
            } else {
                await handleNormal(question);
            }
        } catch (e) {
            addMessage(`Error: ${e.message}`, false);
        }

        setLoading(false);
        el.input.focus();
        updateStats();
    }

    async function handleNormal(question) {
        const res = await fetch(`${API_BASE}/ask`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question })
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const data = await res.json();
        addMessage(data.answer, false, data.model, data.processing_time_ms);
    }

    async function handleStreaming(question) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `message ${MSG_ASSISTANT} ${TYPING_CLASS}`;
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        msgDiv.appendChild(contentDiv);
        el.chat.appendChild(msgDiv);

        const res = await fetch(`${API_BASE}/ask/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question })
        });

        if (!res.ok) {
            msgDiv.classList.remove(TYPING_CLASS);
            throw new Error(`HTTP ${res.status}`);
        }

        const start = Date.now();
        let answer = '';
        let error = null;

        try {
            const reader = res.body.getReader();
            const decoder = new TextDecoder();

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                const text = decoder.decode(value);
                const lines = text.split('\n');

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const data = line.slice(6);
                        if (data === '[DONE]') {
                            msgDiv.classList.remove(TYPING_CLASS);
                            const meta = document.createElement('div');
                            meta.className = 'meta';
                            meta.textContent = `streaming • ${Date.now() - start}ms`;
                            msgDiv.appendChild(meta);
                            return;
                        }
                        answer += data;
                        try { contentDiv.innerHTML = parseMarkdown(answer); } 
                        catch { contentDiv.textContent = answer; }
                    }
                }
                el.chat.scrollTop = el.chat.scrollHeight;
            }
        } catch (e) {
            error = e;
            msgDiv.classList.remove(TYPING_CLASS);
            addMessage('Stream error: ' + e.message, false);
        }
    }

    function handleResize() {
        clearTimeout(resizeTimeout);
        resizeTimeout = setTimeout(() => {
            el.input.style.height = 'auto';
            el.input.style.height = Math.min(el.input.scrollHeight, MAX_INPUT_HEIGHT) + 'px';
        }, 100);
    }

    el.sendBtn.addEventListener('click', sendQuestion);
    el.input.addEventListener('keydown', e => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendQuestion();
        }
    });
    el.input.addEventListener('input', handleResize);

    checkHealth();
    updateStats();
    setInterval(checkHealth, 10000);
    setInterval(updateStats, 30000);
})();