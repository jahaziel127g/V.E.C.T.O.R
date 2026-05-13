(function() {
    'use strict';

    // Auto-detect API URL
    const API_BASE = `${window.location.protocol}//${window.location.hostname}:8080/api`;
    const STORAGE_KEY = 'vector_chat_history';
    const MAX_STORAGE_MESSAGES = 100;
    
    const TYPING_CLASS = 'typing';
    const MSG_USER = 'user';
    const MSG_ASSISTANT = 'assistant';

    const el = {
        input: document.getElementById('question-input'),
        sendBtn: document.getElementById('send-btn'),
        chat: document.getElementById('chat-container'),
        streamToggle: document.getElementById('stream-toggle'),
        modelName: document.getElementById('model-name'),
        totalRequests: document.getElementById('total-requests'),
        cachedCount: document.getElementById('cached-count'),
        status: document.getElementById('status'),
        clearBtn: document.getElementById('clear-chat-btn')
    };

    let resizeTimeout;

    // Simple markdown parser
    function parseMarkdown(text) {
        if (!text) return '';
        let html = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/^### (.*$)/gm, '<h3>$1</h3>')
            .replace(/^## (.*$)/gm, '<h2>$1</h2>')
            .replace(/^# (.*$)/gm, '<h1>$1</h1>')
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
            .replace(/`(.*?)`/g, '<code>$1</code>')
            .replace(/^\* (.*$)/gm, '<li>$1</li>')
            .replace(/^- (.*$)/gm, '<li>$1</li>')
            .replace(/^\d+\. (.*$)/gm, '<li>$1</li>')
            .replace(/^> (.*$)/gm, '<blockquote>$1</blockquote>')
            .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
            .replace(/\n/g, '<br>');
        return html.replace(/(<li>.*<\/li>)(<br>)?/g, '<ul>$1</ul>');
    }

    // Load chat history from localStorage
    function loadChatHistory() {
        try {
            const data = localStorage.getItem(STORAGE_KEY);
            if (data) {
                const messages = JSON.parse(data);
                // Clear welcome message
                el.chat.innerHTML = '';
                // Re-render messages
                messages.forEach(msg => {
                    renderMessage(msg.content, msg.isUser, msg.model, msg.time, false);
                });
            }
        } catch (e) {
            console.error('Failed to load chat history:', e);
        }
    }

    // Save message to localStorage
    function saveMessage(content, isUser, model, time) {
        try {
            let messages = [];
            const data = localStorage.getItem(STORAGE_KEY);
            if (data) {
                messages = JSON.parse(data);
            }
            messages.push({ content, isUser, model, time });
            // Keep only last MAX_STORAGE_MESSAGES
            if (messages.length > MAX_STORAGE_MESSAGES) {
                messages = messages.slice(-MAX_STORAGE_MESSAGES);
            }
            localStorage.setItem(STORAGE_KEY, JSON.stringify(messages));
        } catch (e) {
            console.error('Failed to save message:', e);
        }
    }

    // Clear chat history
    function clearChatHistory() {
        try {
            localStorage.removeItem(STORAGE_KEY);
            el.chat.innerHTML = `
                <div class="welcome-message">
                    <div class="welcome-icon">🤖</div>
                    <h2>Hello! I'm V.E.C.T.O.R</h2>
                    <p>Your AI assistant. Ask me anything!</p>
                </div>
            `;
        } catch (e) {
            console.error('Failed to clear chat:', e);
        }
    }

    // Render a message
    function renderMessage(content, isUser, model, time, save = true) {
        // Remove welcome message if exists
        const welcome = el.chat.querySelector('.welcome-message');
        if (welcome) welcome.remove();

        const div = document.createElement('div');
        div.className = `message ${isUser ? MSG_USER : MSG_ASSISTANT}`;
        
        const icon = document.createElement('div');
        icon.className = 'message-icon';
        icon.textContent = isUser ? '👤' : '🤖';
        
        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        if (isUser) {
            contentDiv.textContent = content;
        } else {
            contentDiv.innerHTML = parseMarkdown(content);
        }
        bubble.appendChild(contentDiv);

        if (model || time) {
            const meta = document.createElement('div');
            meta.className = 'message-meta';
            const parts = [];
            if (model) parts.push(model);
            if (time) parts.push(`${time}ms`);
            meta.textContent = parts.join(' • ');
            bubble.appendChild(meta);
        }
        
        div.appendChild(icon);
        div.appendChild(bubble);
        el.chat.appendChild(div);
        
        el.chat.scrollTop = el.chat.scrollHeight;
        
        if (save) {
            saveMessage(content, isUser, model, time);
        }
    }

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
            el.status.classList.remove('error', 'offline');
        } catch (e) {
            el.status.textContent = 'Offline';
            el.status.classList.add('error', 'offline');
        }
    }

    function setLoading(loading) {
        el.sendBtn.disabled = loading;
        el.input.disabled = loading;
        if (loading) {
            el.sendBtn.classList.add('loading');
        } else {
            el.sendBtn.classList.remove('loading');
        }
    }

    async function sendQuestion() {
        const question = el.input.value.trim();
        if (!question) return;

        setLoading(true);
        renderMessage(question, true);
        el.input.value = '';
        el.input.style.height = '24px';

        try {
            if (el.streamToggle.checked) {
                await handleStreaming(question);
            } else {
                await handleNormal(question);
            }
        } catch (e) {
            renderMessage(`Error: ${e.message}`, false);
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
        renderMessage(data.answer, false, data.model, data.processing_time_ms);
    }

    async function handleStreaming(question) {
        const msgDiv = document.createElement('div');
        msgDiv.className = `message ${MSG_ASSISTANT}`;
        
        const icon = document.createElement('div');
        icon.className = 'message-icon';
        icon.textContent = '🤖';
        
        const bubble = document.createElement('div');
        bubble.className = `message-bubble ${TYPING_CLASS}`;
        
        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';
        
        bubble.appendChild(contentDiv);
        msgDiv.appendChild(icon);
        msgDiv.appendChild(bubble);
        el.chat.appendChild(msgDiv);

        // Remove welcome if exists
        const welcome = el.chat.querySelector('.welcome-message');
        if (welcome) welcome.remove();

        const res = await fetch(`${API_BASE}/ask/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question })
        });

        if (!res.ok) {
            bubble.classList.remove(TYPING_CLASS);
            throw new Error(`HTTP ${res.status}`);
        }

        const start = Date.now();
        let answer = '';

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
                            bubble.classList.remove(TYPING_CLASS);
                            const meta = document.createElement('div');
                            meta.className = 'message-meta';
                            meta.textContent = `streaming • ${Date.now() - start}ms`;
                            bubble.appendChild(meta);
                            // Save to localStorage
                            saveMessage(answer, false, 'gemma3:1b-it-qat', Date.now() - start);
                            return;
                        }
                        answer += data;
                        contentDiv.innerHTML = parseMarkdown(answer);
                    }
                }
                el.chat.scrollTop = el.chat.scrollHeight;
            }
        } catch (e) {
            bubble.classList.remove(TYPING_CLASS);
            renderMessage('Stream error: ' + e.message, false);
        }
    }

    function handleResize() {
        clearTimeout(resizeTimeout);
        resizeTimeout = setTimeout(() => {
            el.input.style.height = 'auto';
            el.input.style.height = Math.min(el.input.scrollHeight, 150) + 'px';
        }, 100);
    }

    // Event listeners
    el.sendBtn.addEventListener('click', sendQuestion);
    el.input.addEventListener('keydown', e => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendQuestion();
        }
    });
    el.input.addEventListener('input', handleResize);
    el.clearBtn.addEventListener('click', () => {
        if (confirm('Clear all chat history?')) {
            clearChatHistory();
        }
    });

    // Initialize
    loadChatHistory();
    checkHealth();
    updateStats();
    setInterval(checkHealth, 10000);
    setInterval(updateStats, 30000);
})();