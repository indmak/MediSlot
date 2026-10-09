/* 诊前咨询 / 病例研究：SSE 流式发送 + 轮询新消息（无框架） */
(function () {
    var container = document.getElementById('chatMessages');
    if (!container) {
        return;
    }

    var convId = container.dataset.conv;
    var endpoint = container.dataset.endpoint || '/consultations';
    var isDoctor = container.dataset.isDoctor === 'true';
    var isCase = endpoint === '/cases';
    var SELF = container.dataset.self || (isDoctor ? 'DOCTOR' : 'PATIENT');
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    var csrf = csrfMeta ? csrfMeta.content : '';
    var csrfHeader = csrfHeaderMeta && csrfHeaderMeta.content ? csrfHeaderMeta.content : 'X-CSRF-TOKEN';
    var typing = document.getElementById('chatTyping');
    var empty = document.getElementById('chatEmpty');
    var errorBox = document.getElementById('chatError');
    var lastId = Number(container.dataset.lastId || 0);
    var polling = false;
    var streaming = false;

    function esc(s) {
        return (s === null || s === undefined ? '' : String(s)).replace(/[&<>"']/g, function (c) {
            return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c];
        });
    }

    function nowTime() {
        var d = new Date();
        function p(n) { return (n < 10 ? '0' : '') + n; }
        return p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
    }

    function badgeClass(status) {
        if (status === 'APPROVED') return 'badge-completed';
        if (status === 'REJECTED') return 'badge-cancelled';
        if (status === 'ADJUSTED') return 'badge-checked';
        return 'badge-pending';
    }

    function renderMessage(m) {
        var wrap = document.createElement('div');
        wrap.className = 'chat-msg ' + (m.senderType === 'PATIENT' ? 'chat-msg--patient'
            : m.senderType === 'DOCTOR' ? 'chat-msg--doctor'
                : m.senderType === 'AI' ? 'chat-msg--ai' : 'chat-msg--system');
        wrap.dataset.id = m.id;

        var meta = document.createElement('div');
        meta.className = 'chat-msg__meta';
        meta.innerHTML = esc(m.senderLabel) + ' · ' + esc(m.time)
            + (m.messageType === 'DIRECTIVE' ? ' <span class="badge-status badge-pending">医生指令</span>' : '');
        wrap.appendChild(meta);

        if (m.messageType === 'DRAFT') {
            var draft = document.createElement('div');
            draft.className = 'chat-draft';
            var html = '<div class="chat-draft__head"><span>AI 草稿</span><span class="badge-status '
                + badgeClass(m.reviewStatus) + '">' + esc(m.reviewStatusLabel || '待核实') + '</span></div>'
                + '<div class="chat-draft__body">' + esc(m.content) + '</div>';
            if (m.reviewNote) {
                html += '<div class="chat-draft__note">医生备注：' + esc(m.reviewNote) + '</div>';
            }
            draft.innerHTML = html;
            if (!isCase && isDoctor && m.reviewStatus === 'PENDING') {
                var form = document.createElement('form');
                form.className = 'chat-draft__review';
                form.method = 'post';
                form.action = endpoint + '/' + convId + '/drafts/' + m.id + '/review';
                form.innerHTML = '<input type="hidden" name="_csrf" value="' + esc(csrf) + '">'
                    + '<input class="form-control form-control-sm" type="text" name="note" placeholder="核实备注（反对/调整时可填）">'
                    + '<button class="btn btn-sm btn-primary" type="submit" name="status" value="APPROVED">同意</button>'
                    + '<button class="btn btn-sm btn-danger-outline" type="submit" name="status" value="REJECTED">反对</button>'
                    + '<button class="btn btn-sm btn-outline-primary" type="submit" name="status" value="ADJUSTED">调整并重生成</button>';
                draft.appendChild(form);
            }
            wrap.appendChild(draft);
        } else {
            var body = document.createElement('div');
            body.className = 'chat-msg__body';
            body.textContent = m.content;
            wrap.appendChild(body);
        }
        return wrap;
    }

    function scrollToBottom() {
        window.scrollTo(0, document.body.scrollHeight);
    }

    function showTyping() {
        if (typing) typing.style.display = 'block';
    }

    function hideTyping() {
        if (typing) typing.style.display = 'none';
    }

    function showError(msg) {
        if (!errorBox) return;
        errorBox.textContent = msg;
        errorBox.style.display = 'block';
        window.setTimeout(function () {
            errorBox.style.display = 'none';
        }, 4000);
    }

    function poll() {
        if (polling || streaming) return;
        polling = true;
        fetch(endpoint + '/' + convId + '/messages.json?after=' + lastId, {headers: {'Accept': 'application/json'}})
            .then(function (res) {
                return res.ok ? res.json() : [];
            })
            .then(function (list) {
                if (list && list.length) {
                    list.forEach(function (m) {
                        container.appendChild(renderMessage(m));
                        if (m.id > lastId) lastId = m.id;
                    });
                    if (empty) empty.style.display = 'none';
                    scrollToBottom();
                }
            })
            .catch(function () { /* ignore */ })
            .finally(function () { polling = false; });
    }

    function createAiBubble() {
        var wrap = document.createElement('div');
        wrap.className = 'chat-msg chat-msg--ai';
        var meta = document.createElement('div');
        meta.className = 'chat-msg__meta';
        meta.textContent = 'AI 助手 · ' + nowTime();
        wrap.appendChild(meta);
        var body = document.createElement('div');
        body.className = 'chat-msg__body';
        wrap.appendChild(body);
        return wrap;
    }

    function handleEvent(evt, data, ctx) {
        var obj;
        try {
            obj = JSON.parse(data);
        } catch (e) {
            return;
        }
        if (evt === 'start') {
            if (obj.humanMessageId && obj.humanMessageId > lastId) lastId = obj.humanMessageId;
        } else if (evt === 'delta') {
            if (obj.text) {
                ctx.aiText += obj.text;
                if (!ctx.bubble) {
                    ctx.bubble = createAiBubble();
                    container.appendChild(ctx.bubble);
                    if (empty) empty.style.display = 'none';
                }
                ctx.bubble.querySelector('.chat-msg__body').textContent = ctx.aiText;
                scrollToBottom();
            }
        } else if (evt === 'done') {
            ctx.aiMessageId = obj.aiMessageId || null;
            if (ctx.aiMessageId && ctx.aiMessageId > lastId) lastId = ctx.aiMessageId;
        } else if (evt === 'error') {
            ctx.error = obj.message || 'AI 暂时不可用';
        }
    }

    var form = document.getElementById('chatForm');
    if (form) {
        var textarea = form.querySelector('textarea');
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var content = textarea.value.trim();
            if (!content) return;
            var action = (e.submitter && e.submitter.value) ? e.submitter.value : 'chat';
            textarea.value = '';

            // 乐观渲染自己的消息
            container.appendChild(renderMessage({
                id: 'tmp-' + Date.now(),
                senderType: SELF,
                senderLabel: SELF === 'DOCTOR' ? '医生' : '患者',
                messageType: (SELF === 'DOCTOR' && action === 'directive') ? 'DIRECTIVE' : 'CHAT',
                content: content,
                reviewStatus: null, reviewStatusLabel: null, reviewNote: null,
                time: nowTime()
            }));
            if (empty) empty.style.display = 'none';
            scrollToBottom();
            showTyping();
            streaming = true;

            var ctx = {aiText: '', bubble: null, aiMessageId: null, error: null};
            var headers = {'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'text/event-stream'};
            headers[csrfHeader] = csrf;

            fetch(endpoint + '/' + convId + '/messages/stream', {
                method: 'POST',
                headers: headers,
                body: new URLSearchParams({content: content, action: action})
            }).then(function (res) {
                if (!res.ok || !res.body) {
                    throw new Error('bad');
                }
                var reader = res.body.getReader();
                var decoder = new TextDecoder();
                var buffer = '';

                function pump() {
                    return reader.read().then(function (r) {
                        if (r.done) return;
                        buffer += decoder.decode(r.value, {stream: true});
                        var blocks = buffer.split('\n\n');
                        buffer = blocks.pop();
                        blocks.forEach(function (block) {
                            var evt = 'message', data = '';
                            block.split('\n').forEach(function (line) {
                                if (line.indexOf('event:') === 0) evt = line.slice(6).trim();
                                else if (line.indexOf('data:') === 0) data += line.slice(5).trim();
                            });
                            if (data) handleEvent(evt, data, ctx);
                        });
                        return pump();
                    });
                }
                return pump();
            }).catch(function () {
                ctx.error = ctx.error || '发送失败';
            }).finally(function () {
                streaming = false;
                hideTyping();
                // 医生指令：流式完成后替换为草稿卡（带核实按钮）
                if (action === 'directive' && ctx.bubble && ctx.aiMessageId) {
                    var draft = renderMessage({
                        id: ctx.aiMessageId,
                        senderType: 'AI', senderLabel: 'AI 助手', messageType: 'DRAFT',
                        content: ctx.aiText, reviewStatus: 'PENDING', reviewStatusLabel: '待核实',
                        reviewNote: null, time: nowTime()
                    });
                    ctx.bubble.replaceWith(draft);
                }
                if (ctx.error) showError(ctx.error);
                poll();
            });
        });
    }

    if (empty) empty.style.display = container.children.length ? 'none' : '';
    scrollToBottom();
    window.setInterval(poll, 3000);
})();
