/* 诊前咨询 / 病例研究：AJAX 发送 + 轮询新消息（无框架） */
(function () {
    var container = document.getElementById('chatMessages');
    if (!container) {
        return;
    }

    var convId = container.dataset.conv;
    var endpoint = container.dataset.endpoint || '/consultations';
    var isDoctor = container.dataset.isDoctor === 'true';
    var isCase = endpoint === '/cases';
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    var csrf = csrfMeta ? csrfMeta.content : '';
    var csrfHeader = csrfHeaderMeta && csrfHeaderMeta.content ? csrfHeaderMeta.content : 'X-CSRF-TOKEN';
    var typing = document.getElementById('chatTyping');
    var empty = document.getElementById('chatEmpty');
    var errorBox = document.getElementById('chatError');
    var lastId = Number(container.dataset.lastId || 0);
    var polling = false;

    function esc(s) {
        return (s === null || s === undefined ? '' : String(s)).replace(/[&<>"']/g, function (c) {
            return {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c];
        });
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
        if (polling) return;
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
                    if (list.some(function (m) { return m.senderType === 'AI'; })) hideTyping();
                }
            })
            .catch(function () { /* ignore */ })
            .finally(function () { polling = false; });
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
            showTyping();
            var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
            headers[csrfHeader] = csrf;
            fetch(endpoint + '/' + convId + '/messages.json', {
                method: 'POST',
                headers: headers,
                body: new URLSearchParams({content: content, action: action})
            })
                .then(function (res) { return res.json(); })
                .then(function (data) {
                    if (!data.ok) {
                        showError(data.error || '发送失败');
                        hideTyping();
                    } else {
                        poll();
                    }
                })
                .catch(function () {
                    showError('发送失败');
                    hideTyping();
                });
        });
    }

    if (empty) empty.style.display = container.children.length ? 'none' : '';
    scrollToBottom();
    window.setInterval(poll, 3000);
})();
