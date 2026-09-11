/* imageUploadNormalizer.js
 * 배송완료/AS 결과 이미지 업로드용 브라우저 이미지 정규화 유틸.
 * HEIC/HEIF는 업로드 전에 JPEG로 변환하고, 이미 웹 호환 이미지인 경우 원본을 유지합니다.
 */
(function (window, document) {
    'use strict';

    const HEIC_CONVERTER_URLS = [
        'https://cdn.jsdelivr.net/npm/heic2any@0.0.4/dist/heic2any.min.js',
        'https://unpkg.com/heic2any@0.0.4/dist/heic2any.min.js'
    ];
    const JPEG_QUALITY = 0.94;
    const IMAGE_EXTENSIONS = new Set([
        'jpg', 'jpeg', 'jfif', 'png', 'gif', 'webp', 'bmp', 'heic', 'heif'
    ]);
    const HEIC_EXTENSIONS = new Set(['heic', 'heif']);
    const HEIC_MIME_TYPES = new Set([
        'image/heic', 'image/heif', 'image/heic-sequence', 'image/heif-sequence'
    ]);

    let heicConverterPromise = null;

    function getExtension(filename) {
        const name = String(filename || '').trim();
        const index = name.lastIndexOf('.');
        return index >= 0 && index < name.length - 1
            ? name.substring(index + 1).toLowerCase()
            : '';
    }

    function withoutExtension(filename) {
        const name = String(filename || 'image').trim() || 'image';
        const index = name.lastIndexOf('.');
        const base = index > 0 ? name.substring(0, index) : name;
        return base || 'image';
    }

    function isImageCandidate(file) {
        if (!file) return false;
        const type = String(file.type || '').trim().toLowerCase();
        if (type.startsWith('image/')) return true;
        return IMAGE_EXTENSIONS.has(getExtension(file.name));
    }

    function isHeicByNameOrMime(file) {
        if (!file) return false;
        const type = String(file.type || '').trim().toLowerCase();
        return HEIC_MIME_TYPES.has(type) || HEIC_EXTENSIONS.has(getExtension(file.name));
    }

    async function isHeicBySignature(file) {
        if (!file || typeof file.slice !== 'function') return false;

        try {
            const buffer = await file.slice(0, 32).arrayBuffer();
            const bytes = new Uint8Array(buffer);
            if (bytes.length < 12) return false;

            const text = String.fromCharCode.apply(null, Array.from(bytes));
            if (text.substring(4, 8) !== 'ftyp') return false;

            const brands = [
                'heic', 'heix', 'hevc', 'hevx', 'heim', 'heis',
                'hevm', 'hevs'
            ];
            return brands.some(brand => text.indexOf(brand) >= 0);
        } catch (ignored) {
            return false;
        }
    }

    async function isHeicFile(file) {
        if (isHeicByNameOrMime(file)) return true;
        return isHeicBySignature(file);
    }

    function createScriptLoader(url) {
        return new Promise((resolve, reject) => {
            const existing = Array.from(document.scripts || [])
                .find(script => script.src === url);

            if (existing) {
                if (typeof window.heic2any === 'function') {
                    resolve(window.heic2any);
                    return;
                }

                existing.addEventListener('load', () => {
                    if (typeof window.heic2any === 'function') resolve(window.heic2any);
                    else reject(new Error('HEIC 변환 모듈을 초기화하지 못했습니다.'));
                }, { once: true });
                existing.addEventListener('error', () => {
                    reject(new Error('HEIC 변환 모듈을 불러오지 못했습니다.'));
                }, { once: true });
                return;
            }

            const script = document.createElement('script');
            script.src = url;
            script.async = true;
            script.crossOrigin = 'anonymous';
            script.onload = () => {
                if (typeof window.heic2any === 'function') resolve(window.heic2any);
                else reject(new Error('HEIC 변환 모듈을 초기화하지 못했습니다.'));
            };
            script.onerror = () => reject(new Error('HEIC 변환 모듈을 불러오지 못했습니다.'));
            document.head.appendChild(script);
        });
    }

    async function loadHeicConverter() {
        if (typeof window.heic2any === 'function') {
            return window.heic2any;
        }

        if (heicConverterPromise) {
            return heicConverterPromise;
        }

        heicConverterPromise = (async () => {
            let lastError = null;

            for (const url of HEIC_CONVERTER_URLS) {
                try {
                    return await createScriptLoader(url);
                } catch (error) {
                    lastError = error;
                }
            }

            throw lastError || new Error('HEIC 변환 모듈을 불러오지 못했습니다.');
        })();

        try {
            return await heicConverterPromise;
        } catch (error) {
            heicConverterPromise = null;
            throw error;
        }
    }

    function toSingleBlob(value) {
        if (value instanceof Blob) return value;
        if (Array.isArray(value)) {
            const first = value.find(item => item instanceof Blob);
            if (first) return first;
        }
        return null;
    }

    function convertHeicNatively(file, quality) {
        return new Promise((resolve, reject) => {
            const objectUrl = URL.createObjectURL(file);
            const image = new Image();

            function cleanup() {
                URL.revokeObjectURL(objectUrl);
            }

            image.onload = () => {
                try {
                    const width = image.naturalWidth || image.width;
                    const height = image.naturalHeight || image.height;

                    if (!width || !height) {
                        cleanup();
                        reject(new Error('브라우저가 HEIC 이미지 크기를 해석하지 못했습니다.'));
                        return;
                    }

                    const canvas = document.createElement('canvas');
                    canvas.width = width;
                    canvas.height = height;

                    const context = canvas.getContext('2d');
                    if (!context) {
                        cleanup();
                        reject(new Error('이미지 변환용 Canvas를 생성하지 못했습니다.'));
                        return;
                    }

                    context.drawImage(image, 0, 0, width, height);
                    canvas.toBlob(blob => {
                        cleanup();
                        if (!blob || blob.size <= 0) {
                            reject(new Error('브라우저에서 JPEG 이미지 생성에 실패했습니다.'));
                            return;
                        }
                        resolve(blob);
                    }, 'image/jpeg', Number.isFinite(quality) ? quality : JPEG_QUALITY);
                } catch (error) {
                    cleanup();
                    reject(error);
                }
            };

            image.onerror = () => {
                cleanup();
                reject(new Error('현재 브라우저가 HEIC 원본을 직접 해석하지 못합니다.'));
            };

            image.src = objectUrl;
        });
    }

    async function convertHeicToJpeg(file, quality) {
        const normalizedQuality = Number.isFinite(quality) ? quality : JPEG_QUALITY;
        let blob = null;

        // iPhone/iPad Safari처럼 HEIC를 자체 디코딩할 수 있는 브라우저에서는
        // 외부 모듈 없이 Canvas로 곧바로 JPEG 재인코딩합니다.
        try {
            blob = await convertHeicNatively(file, normalizedQuality);
        } catch (nativeError) {
            // Windows Chrome/Edge처럼 HEIC 디코딩을 지원하지 않는 환경에서는
            // heic2any를 지연 로드하여 동일하게 JPEG로 변환합니다.
            const converter = await loadHeicConverter();
            const converted = await converter({
                blob: file,
                toType: 'image/jpeg',
                quality: normalizedQuality
            });
            blob = toSingleBlob(converted);
        }

        if (!blob || blob.size <= 0) {
            throw new Error('HEIC 이미지를 JPEG로 변환하지 못했습니다.');
        }

        const outputName = `${withoutExtension(file.name)}.jpg`;
        return new File([blob], outputName, {
            type: 'image/jpeg',
            lastModified: file.lastModified || Date.now()
        });
    }

    async function normalizeFile(file, options) {
        if (!file || !(file instanceof Blob)) {
            throw new Error('이미지 파일 정보가 올바르지 않습니다.');
        }

        if (!isImageCandidate(file)) {
            throw new Error(`${file.name || '선택 파일'}은(는) 이미지 파일이 아닙니다.`);
        }

        if (!(await isHeicFile(file))) {
            return {
                file,
                converted: false,
                originalName: file.name || 'image'
            };
        }

        try {
            const convertedFile = await convertHeicToJpeg(
                file,
                options && Number.isFinite(options.jpegQuality)
                    ? options.jpegQuality
                    : JPEG_QUALITY
            );

            return {
                file: convertedFile,
                converted: true,
                originalName: file.name || 'image.heic'
            };
        } catch (error) {
            const reason = error && error.message ? error.message : '알 수 없는 변환 오류';
            throw new Error(`${file.name || 'HEIC 이미지'} 변환 실패: ${reason}`);
        }
    }

    async function normalizeFiles(fileList, options) {
        const sourceFiles = Array.from(fileList || []);
        const result = [];
        const converted = [];
        const rejected = [];

        for (let index = 0; index < sourceFiles.length; index += 1) {
            const source = sourceFiles[index];

            if (!isImageCandidate(source)) {
                rejected.push({ file: source, reason: '이미지 파일이 아닙니다.' });
                continue;
            }

            if (options && typeof options.onProgress === 'function') {
                options.onProgress({
                    phase: 'start',
                    index,
                    total: sourceFiles.length,
                    file: source
                });
            }

            const normalized = await normalizeFile(source, options);
            result.push(normalized.file);

            if (normalized.converted) {
                converted.push({
                    originalName: normalized.originalName,
                    outputName: normalized.file.name
                });
            }

            if (options && typeof options.onProgress === 'function') {
                options.onProgress({
                    phase: 'done',
                    index,
                    total: sourceFiles.length,
                    file: source,
                    outputFile: normalized.file,
                    converted: normalized.converted
                });
            }
        }

        return {
            files: result,
            converted,
            rejected
        };
    }

    function replaceInputFiles(input, files) {
        if (!input) return;
        const dataTransfer = new DataTransfer();
        Array.from(files || []).forEach(file => dataTransfer.items.add(file));
        input.files = dataTransfer.files;
    }

    window.HiddenAutoImageUpload = Object.freeze({
        normalizeFile,
        normalizeFiles,
        replaceInputFiles,
        isHeicFile
    });

})(window, document);
