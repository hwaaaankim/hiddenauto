(function (window, document) {
    'use strict';

    const pm = window.HiddenBathProductMaster;
    if (!pm) return;

    let policy = {
        maxFileSizeBytes: 10 * 1024 * 1024,
        maxFilesPerOwner: 30,
        allowedContentTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
    };
    let queueSequence = 0;

    async function loadPolicy() {
        try {
            const response = await pm.request('/admin/api/product-master/image-policy');
            if (response.data) {
                policy = Object.assign({}, policy, response.data);
            }
        } catch (error) {
            pm.toast('이미지 제한 설정을 불러오지 못해 기본 제한을 사용합니다.', 'warning');
        }
        return Object.assign({}, policy);
    }

    function createManager(config) {
        const root = config && config.root;
        if (!root) throw new Error('이미지 관리 영역을 찾을 수 없습니다.');

        const mode = root.querySelector('[data-pm-image-mode]');
        const panel = root.querySelector('[data-pm-image-panel]');
        const input = root.querySelector('[data-pm-image-input]');
        const dropzone = root.querySelector('[data-pm-image-dropzone]');
        const list = root.querySelector('[data-pm-image-list]');
        const count = root.querySelector('[data-pm-image-count]');
        const policyText = root.querySelector('[data-pm-image-policy]');
        let existing = [];
        let queued = [];

        function render() {
            const total = existing.length + queued.length;
            if (count) count.textContent = total + '장';
            if (policyText) {
                policyText.textContent = 'JPG·PNG·GIF·WEBP · 장당 '
                    + formatBytes(policy.maxFileSizeBytes) + ' · 최대 ' + policy.maxFilesPerOwner + '장';
            }

            const addMode = total > 0 || (mode && mode.value === 'ADD');
            if (mode) mode.value = addMode ? 'ADD' : 'NONE';
            if (panel) {
                panel.classList.toggle('pm-is-open', addMode);
                panel.setAttribute('aria-hidden', addMode ? 'false' : 'true');
                if (addMode) panel.removeAttribute('inert');
                else panel.setAttribute('inert', '');
            }

            if (!list) return;
            const persistedHtml = existing.map(function (image) {
                return '<article class="pm-image-item" data-pm-existing-image-id="' + image.id + '">'
                    + '<div class="pm-image-thumb"><img src="' + pm.escapeHtml(image.contentPath) + '" alt="'
                    + pm.escapeHtml(image.originalFilename) + '" loading="lazy"></div>'
                    + '<div class="pm-image-meta"><strong title="' + pm.escapeHtml(image.originalFilename) + '">'
                    + pm.escapeHtml(image.originalFilename) + '</strong><span>등록됨 · ' + formatBytes(image.fileSize) + '</span></div>'
                    + '<button class="pm-image-remove" type="button" data-pm-remove-existing="' + image.id
                    + '" aria-label="' + pm.escapeHtml(image.originalFilename) + ' 등록 해제"><i class="ri-close-line"></i></button>'
                    + '</article>';
            }).join('');
            const queuedHtml = queued.map(function (item) {
                return '<article class="pm-image-item pm-is-queued" data-pm-queued-image-id="' + item.id + '">'
                    + '<div class="pm-image-thumb"><img src="' + pm.escapeHtml(item.previewUrl) + '" alt="'
                    + pm.escapeHtml(item.file.name) + '"></div>'
                    + '<div class="pm-image-meta"><strong title="' + pm.escapeHtml(item.file.name) + '">'
                    + pm.escapeHtml(item.file.name) + '</strong><span>저장 대기 · ' + formatBytes(item.file.size) + '</span></div>'
                    + '<button class="pm-image-remove" type="button" data-pm-remove-queued="' + item.id
                    + '" aria-label="' + pm.escapeHtml(item.file.name) + ' 선택 해제"><i class="ri-close-line"></i></button>'
                    + '</article>';
            }).join('');
            list.innerHTML = persistedHtml + queuedHtml || '<div class="pm-image-list-empty">아직 등록할 이미지가 없습니다.</div>';
            bindRemoveEvents();
        }

        async function removeExisting(imageId) {
            const image = existing.find(function (item) { return Number(item.id) === Number(imageId); });
            if (!image) return;
            const confirmed = await pm.confirm(
                '이미지 등록을 해제하시겠습니까?',
                image.originalFilename + ' 파일 연결이 제거됩니다. 제품 코드와 제품 사양은 바뀌지 않습니다.',
                '이미지 해제'
            );
            if (!confirmed) return;
            try {
                const response = await pm.request('/admin/api/product-master/images/' + image.id, {method: 'DELETE'});
                existing = existing.filter(function (item) { return Number(item.id) !== Number(image.id); });
                if (!existing.length && !queued.length && mode) mode.value = 'NONE';
                render();
                if (typeof config.onDeleted === 'function') config.onDeleted(image);
                pm.toast(response.message || '이미지 등록을 해제했습니다.');
            } catch (error) {
                await pm.alert('error', '이미지 해제 실패', error.message);
            }
        }

        function removeQueued(queueId) {
            const target = queued.find(function (item) { return item.id === queueId; });
            if (target) URL.revokeObjectURL(target.previewUrl);
            queued = queued.filter(function (item) { return item.id !== queueId; });
            if (!existing.length && !queued.length && mode) mode.value = 'NONE';
            render();
        }

        function bindRemoveEvents() {
            list.querySelectorAll('[data-pm-remove-existing]').forEach(function (button) {
                button.addEventListener('click', function () {
                    removeExisting(Number(button.dataset.pmRemoveExisting));
                });
            });
            list.querySelectorAll('[data-pm-remove-queued]').forEach(function (button) {
                button.addEventListener('click', function () {
                    removeQueued(button.dataset.pmRemoveQueued);
                });
            });
        }

        async function addFiles(fileList) {
            const candidates = Array.from(fileList || []);
            if (!candidates.length) return;
            const accepted = [];
            const errors = [];
            candidates.forEach(function (file) {
                if (file.type && !policy.allowedContentTypes.includes(file.type)) {
                    errors.push(file.name + ': JPG, PNG, GIF, WEBP 형식만 가능합니다.');
                    return;
                }
                if (!file.size || file.size > policy.maxFileSizeBytes) {
                    errors.push(file.name + ': 파일 크기는 0보다 크고 ' + formatBytes(policy.maxFileSizeBytes) + ' 이하여야 합니다.');
                    return;
                }
                const duplicate = queued.some(function (item) {
                    return item.file.name === file.name
                        && item.file.size === file.size
                        && item.file.lastModified === file.lastModified;
                });
                if (!duplicate) accepted.push(file);
            });

            const freeSlots = Math.max(0, policy.maxFilesPerOwner - existing.length - queued.length);
            if (accepted.length > freeSlots) {
                errors.push('이미지는 한 그룹 또는 옵션값에 최대 ' + policy.maxFilesPerOwner + '장까지 등록할 수 있습니다.');
                accepted.splice(freeSlots);
            }
            accepted.forEach(function (file) {
                queueSequence += 1;
                queued.push({
                    id: 'pm-image-queue-' + queueSequence,
                    file: file,
                    previewUrl: URL.createObjectURL(file)
                });
            });
            if (input) input.value = '';
            if (mode && queued.length) mode.value = 'ADD';
            render();
            if (errors.length) {
                await pm.alert('warning', '일부 이미지를 추가하지 못했습니다.', errors.slice(0, 5).join('\n'));
            }
        }

        async function handleModeChange() {
            if (!mode || mode.value === 'ADD') {
                render();
                return;
            }
            if (existing.length) {
                mode.value = 'ADD';
                render();
                await pm.alert('info', '등록된 이미지가 있습니다.', '각 이미지의 X 버튼으로 등록을 해제한 뒤 “이미지 없음”을 선택할 수 있습니다.');
                return;
            }
            if (queued.length) {
                const confirmed = await pm.confirm(
                    '선택한 이미지를 모두 해제하시겠습니까?',
                    '아직 저장하지 않은 이미지 ' + queued.length + '장이 목록에서 제거됩니다.',
                    '선택 해제'
                );
                if (!confirmed) {
                    mode.value = 'ADD';
                    render();
                    return;
                }
                clearQueued();
            }
            render();
        }

        function clearQueued() {
            queued.forEach(function (item) { URL.revokeObjectURL(item.previewUrl); });
            queued = [];
        }

        function reset(images) {
            clearQueued();
            existing = Array.isArray(images) ? images.slice() : [];
            if (mode) mode.value = existing.length ? 'ADD' : 'NONE';
            render();
        }

        if (mode) mode.addEventListener('change', handleModeChange);
        if (input) input.addEventListener('change', function () { addFiles(input.files); });
        if (dropzone) {
            dropzone.addEventListener('click', function () { if (input) input.click(); });
            dropzone.addEventListener('keydown', function (event) {
                if ((event.key === 'Enter' || event.key === ' ') && input) {
                    event.preventDefault();
                    input.click();
                }
            });
            ['dragenter', 'dragover'].forEach(function (name) {
                dropzone.addEventListener(name, function (event) {
                    event.preventDefault();
                    dropzone.classList.add('pm-is-dragover');
                });
            });
            ['dragleave', 'drop'].forEach(function (name) {
                dropzone.addEventListener(name, function (event) {
                    event.preventDefault();
                    dropzone.classList.remove('pm-is-dragover');
                });
            });
            dropzone.addEventListener('drop', function (event) { addFiles(event.dataTransfer.files); });
        }

        reset([]);
        return {
            reset: reset,
            queuedFiles: function () { return queued.map(function (item) { return item.file; }); },
            existingImages: function () { return existing.slice(); },
            totalCount: function () { return existing.length + queued.length; },
            isAddMode: function () { return Boolean(mode && mode.value === 'ADD'); },
            render: render
        };
    }

    function appendFiles(formData, files, fieldName) {
        (files || []).forEach(function (file) {
            formData.append(fieldName || 'images', file, file.name);
        });
        return formData;
    }

    function formatBytes(bytes) {
        const value = Number(bytes || 0);
        if (value < 1024) return value + ' B';
        if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB';
        return (value / (1024 * 1024)).toFixed(value >= 10 * 1024 * 1024 ? 0 : 1) + ' MB';
    }

    window.ProductMasterImages = {
        loadPolicy: loadPolicy,
        createManager: createManager,
        appendFiles: appendFiles,
        formatBytes: formatBytes
    };
})(window, document);
