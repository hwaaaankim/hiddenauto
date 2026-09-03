(function (window) {
    'use strict';

    // 외부 CDN이나 서버 라이브러리에 의존하지 않는 QR Code Model 2 생성기입니다.
    // 현재 제품 공개 URL 길이에 충분한 Version 1~10, 오류복원 M, Byte mode를 지원합니다.
    const RS_BLOCKS_M = {
        1: [[1, 26, 16]],
        2: [[1, 44, 28]],
        3: [[1, 70, 44]],
        4: [[2, 50, 32]],
        5: [[2, 67, 43]],
        6: [[4, 43, 27]],
        7: [[4, 49, 31]],
        8: [[2, 60, 38], [2, 61, 39]],
        9: [[3, 58, 36], [2, 59, 37]],
        10: [[4, 69, 43], [1, 70, 44]]
    };

    const ALIGNMENT_POSITIONS = {
        1: [],
        2: [6, 18],
        3: [6, 22],
        4: [6, 26],
        5: [6, 30],
        6: [6, 34],
        7: [6, 22, 38],
        8: [6, 24, 42],
        9: [6, 26, 46],
        10: [6, 28, 50]
    };

    const EXP = new Array(512).fill(0);
    const LOG = new Array(256).fill(0);
    let fieldValue = 1;
    for (let fieldIndex = 0; fieldIndex < 255; fieldIndex += 1) {
        EXP[fieldIndex] = fieldValue;
        LOG[fieldValue] = fieldIndex;
        fieldValue <<= 1;
        if (fieldValue & 0x100) fieldValue ^= 0x11d;
    }
    for (let fieldIndex = 255; fieldIndex < EXP.length; fieldIndex += 1) {
        EXP[fieldIndex] = EXP[fieldIndex - 255];
    }

    class BitBuffer {
        constructor() {
            this.bits = [];
        }

        put(value, length) {
            for (let bit = length - 1; bit >= 0; bit -= 1) {
                this.bits.push(((value >>> bit) & 1) === 1);
            }
        }

        get length() {
            return this.bits.length;
        }

        toBytes() {
            const bytes = new Array(Math.ceil(this.bits.length / 8)).fill(0);
            this.bits.forEach(function (value, index) {
                if (value) bytes[Math.floor(index / 8)] |= 0x80 >>> (index % 8);
            });
            return bytes;
        }
    }

    function expandBlocks(version) {
        const result = [];
        RS_BLOCKS_M[version].forEach(function (entry) {
            const count = entry[0];
            for (let index = 0; index < count; index += 1) {
                result.push({total: entry[1], data: entry[2]});
            }
        });
        return result;
    }

    function utf8Bytes(text) {
        if (window.TextEncoder) return Array.from(new TextEncoder().encode(text));
        const encoded = unescape(encodeURIComponent(text));
        return Array.from(encoded).map(function (character) {
            return character.charCodeAt(0);
        });
    }

    function chooseVersion(byteLength) {
        for (let version = 1; version <= 10; version += 1) {
            const totalData = expandBlocks(version).reduce(function (sum, block) {
                return sum + block.data;
            }, 0);
            const countBits = version < 10 ? 8 : 16;
            if (4 + countBits + byteLength * 8 <= totalData * 8) return version;
        }
        throw new Error('QR에 넣을 주소가 너무 깁니다. 공개 주소를 200자 이내로 줄여 주세요.');
    }

    function buildDataCodewords(bytes, version, totalDataCodewords) {
        const buffer = new BitBuffer();
        buffer.put(0x4, 4);
        buffer.put(bytes.length, version < 10 ? 8 : 16);
        bytes.forEach(function (value) {
            buffer.put(value, 8);
        });

        const capacity = totalDataCodewords * 8;
        if (buffer.length > capacity) throw new Error('QR 데이터 용량을 초과했습니다.');
        buffer.put(0, Math.min(4, capacity - buffer.length));
        while (buffer.length % 8 !== 0) buffer.put(0, 1);

        const data = buffer.toBytes();
        let pad = true;
        while (data.length < totalDataCodewords) {
            data.push(pad ? 0xec : 0x11);
            pad = !pad;
        }
        return data;
    }

    function multiplyPolynomial(left, right) {
        const result = new Array(left.length + right.length - 1).fill(0);
        for (let leftIndex = 0; leftIndex < left.length; leftIndex += 1) {
            for (let rightIndex = 0; rightIndex < right.length; rightIndex += 1) {
                const leftValue = left[leftIndex];
                const rightValue = right[rightIndex];
                if (leftValue !== 0 && rightValue !== 0) {
                    result[leftIndex + rightIndex] ^= EXP[LOG[leftValue] + LOG[rightValue]];
                }
            }
        }
        return result;
    }

    function generatorPolynomial(degree) {
        let generator = [1];
        for (let index = 0; index < degree; index += 1) {
            generator = multiplyPolynomial(generator, [1, EXP[index]]);
        }
        return generator;
    }

    function errorCorrection(data, degree) {
        const generator = generatorPolynomial(degree);
        const result = data.concat(new Array(degree).fill(0));
        for (let index = 0; index < data.length; index += 1) {
            const factor = result[index];
            if (factor === 0) continue;
            const factorLog = LOG[factor];
            for (let offset = 0; offset < generator.length; offset += 1) {
                const generatorValue = generator[offset];
                if (generatorValue !== 0) {
                    result[index + offset] ^= EXP[factorLog + LOG[generatorValue]];
                }
            }
        }
        return result.slice(result.length - degree);
    }

    function interleaveCodewords(data, blocks) {
        const dataBlocks = [];
        const errorBlocks = [];
        let offset = 0;
        let maxDataLength = 0;
        let maxErrorLength = 0;

        blocks.forEach(function (block) {
            const blockData = data.slice(offset, offset + block.data);
            offset += block.data;
            const blockError = errorCorrection(blockData, block.total - block.data);
            dataBlocks.push(blockData);
            errorBlocks.push(blockError);
            maxDataLength = Math.max(maxDataLength, blockData.length);
            maxErrorLength = Math.max(maxErrorLength, blockError.length);
        });

        const result = [];
        for (let index = 0; index < maxDataLength; index += 1) {
            dataBlocks.forEach(function (block) {
                if (index < block.length) result.push(block[index]);
            });
        }
        for (let index = 0; index < maxErrorLength; index += 1) {
            errorBlocks.forEach(function (block) {
                if (index < block.length) result.push(block[index]);
            });
        }
        return result;
    }

    function bchRemainder(value, polynomial) {
        let working = value;
        const polynomialDegree = bchDegree(polynomial);
        while (bchDegree(working) >= polynomialDegree) {
            working ^= polynomial << (bchDegree(working) - polynomialDegree);
        }
        return working;
    }

    function bchDegree(value) {
        let degree = 0;
        let working = value;
        while (working !== 0) {
            degree += 1;
            working >>>= 1;
        }
        return degree;
    }

    function setupFinder(matrix, row, column) {
        const size = matrix.length;
        for (let rowOffset = -1; rowOffset <= 7; rowOffset += 1) {
            for (let columnOffset = -1; columnOffset <= 7; columnOffset += 1) {
                const targetRow = row + rowOffset;
                const targetColumn = column + columnOffset;
                if (targetRow < 0 || targetRow >= size || targetColumn < 0 || targetColumn >= size) continue;
                const inPattern = rowOffset >= 0 && rowOffset <= 6
                    && columnOffset >= 0 && columnOffset <= 6;
                const dark = inPattern && (
                    rowOffset === 0 || rowOffset === 6
                    || columnOffset === 0 || columnOffset === 6
                    || (rowOffset >= 2 && rowOffset <= 4 && columnOffset >= 2 && columnOffset <= 4)
                );
                matrix[targetRow][targetColumn] = dark;
            }
        }
    }

    function setupAlignment(matrix, version) {
        const positions = ALIGNMENT_POSITIONS[version];
        positions.forEach(function (row) {
            positions.forEach(function (column) {
                if (matrix[row][column] !== null) return;
                for (let rowOffset = -2; rowOffset <= 2; rowOffset += 1) {
                    for (let columnOffset = -2; columnOffset <= 2; columnOffset += 1) {
                        matrix[row + rowOffset][column + columnOffset] = (
                            Math.abs(rowOffset) === 2
                            || Math.abs(columnOffset) === 2
                            || (rowOffset === 0 && columnOffset === 0)
                        );
                    }
                }
            });
        });
    }

    function setupTiming(matrix) {
        const size = matrix.length;
        for (let row = 8; row < size - 8; row += 1) {
            if (matrix[row][6] === null) matrix[row][6] = row % 2 === 0;
        }
        for (let column = 8; column < size - 8; column += 1) {
            if (matrix[6][column] === null) matrix[6][column] = column % 2 === 0;
        }
    }

    function setupFormat(matrix, maskPattern) {
        const size = matrix.length;
        const formatData = maskPattern; // Error correction M의 format bit는 00입니다.
        const formatBits = ((formatData << 10) | bchRemainder(formatData << 10, 0x537)) ^ 0x5412;

        for (let index = 0; index < 15; index += 1) {
            const dark = ((formatBits >>> index) & 1) === 1;
            if (index < 6) matrix[index][8] = dark;
            else if (index < 8) matrix[index + 1][8] = dark;
            else matrix[size - 15 + index][8] = dark;

            if (index < 8) matrix[8][size - index - 1] = dark;
            else if (index < 9) matrix[8][15 - index] = dark;
            else matrix[8][15 - index - 1] = dark;
        }
        matrix[size - 8][8] = true;
    }

    function setupVersion(matrix, version) {
        if (version < 7) return;
        const size = matrix.length;
        const versionBits = (version << 12) | bchRemainder(version << 12, 0x1f25);
        for (let index = 0; index < 18; index += 1) {
            const dark = ((versionBits >>> index) & 1) === 1;
            matrix[Math.floor(index / 3)][index % 3 + size - 11] = dark;
            matrix[index % 3 + size - 11][Math.floor(index / 3)] = dark;
        }
    }

    function maskZero(row, column) {
        return (row + column) % 2 === 0;
    }

    function mapData(matrix, codewords) {
        const size = matrix.length;
        let row = size - 1;
        let direction = -1;
        let byteIndex = 0;
        let bitIndex = 7;

        for (let column = size - 1; column > 0; column -= 2) {
            if (column === 6) column -= 1;
            while (true) {
                for (let offset = 0; offset < 2; offset += 1) {
                    const targetColumn = column - offset;
                    if (matrix[row][targetColumn] !== null) continue;
                    let dark = false;
                    if (byteIndex < codewords.length) {
                        dark = ((codewords[byteIndex] >>> bitIndex) & 1) === 1;
                    }
                    if (maskZero(row, targetColumn)) dark = !dark;
                    matrix[row][targetColumn] = dark;
                    bitIndex -= 1;
                    if (bitIndex < 0) {
                        byteIndex += 1;
                        bitIndex = 7;
                    }
                }
                row += direction;
                if (row < 0 || row >= size) {
                    row -= direction;
                    direction = -direction;
                    break;
                }
            }
        }
    }

    function createMatrix(text) {
        const bytes = utf8Bytes(String(text || ''));
        if (bytes.length === 0) throw new Error('QR에 넣을 주소가 없습니다.');
        const version = chooseVersion(bytes.length);
        const blocks = expandBlocks(version);
        const totalData = blocks.reduce(function (sum, block) { return sum + block.data; }, 0);
        const data = buildDataCodewords(bytes, version, totalData);
        const codewords = interleaveCodewords(data, blocks);
        const size = version * 4 + 17;
        const matrix = Array.from({length: size}, function () {
            return new Array(size).fill(null);
        });

        setupFinder(matrix, 0, 0);
        setupFinder(matrix, size - 7, 0);
        setupFinder(matrix, 0, size - 7);
        setupAlignment(matrix, version);
        setupTiming(matrix);
        setupFormat(matrix, 0);
        setupVersion(matrix, version);
        mapData(matrix, codewords);
        return matrix;
    }

    function createSvg(text, options) {
        const config = Object.assign({quietZone: 4, dark: '#17243c', light: '#ffffff'}, options || {});
        const matrix = createMatrix(text);
        const quiet = Math.max(0, Number(config.quietZone) || 0);
        const viewSize = matrix.length + quiet * 2;
        const commands = [];
        matrix.forEach(function (row, rowIndex) {
            row.forEach(function (dark, columnIndex) {
                if (dark) commands.push('M' + (columnIndex + quiet) + ' ' + (rowIndex + quiet) + 'h1v1h-1z');
            });
        });
        return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + viewSize + ' ' + viewSize + '"'
            + ' role="img" aria-label="제품 상세 QR 코드" shape-rendering="crispEdges">'
            + '<rect width="100%" height="100%" fill="' + config.light + '"/>'
            + '<path d="' + commands.join('') + '" fill="' + config.dark + '"/>'
            + '</svg>';
    }

    function render(element, text, options) {
        if (!element) throw new Error('QR을 표시할 영역이 없습니다.');
        const svg = createSvg(text, options);
        element.innerHTML = svg;
        return svg;
    }

    window.ProductMasterQr = {
        createMatrix: createMatrix,
        createSvg: createSvg,
        render: render
    };
})(window);
