(function () {
    'use strict';

    function findTextRange(value, keyword) {
        const normalizedValue = value.toLocaleLowerCase();
        const normalizedKeyword = keyword.toLocaleLowerCase();
        const start = normalizedValue.indexOf(normalizedKeyword);

        return start < 0 ? null : { start: start, end: start + keyword.length };
    }

    function findNumberRange(value, keyword) {
        const keywordDigits = keyword.replace(/\D/g, '');
        if (!keywordDigits) {
            return findTextRange(value, keyword);
        }

        let valueDigits = '';
        const digitPositions = [];

        for (let index = 0; index < value.length; index += 1) {
            if (/\d/.test(value[index])) {
                valueDigits += value[index];
                digitPositions.push(index);
            }
        }

        const digitStart = valueDigits.indexOf(keywordDigits);
        if (digitStart < 0) {
            return null;
        }

        const lastDigitPosition = digitPositions[digitStart + keywordDigits.length - 1];
        return {
            start: digitPositions[digitStart],
            end: lastDigitPosition + 1
        };
    }

    function highlightElement(element) {
        const keyword = (element.dataset.searchKeyword || '').trim();
        const value = element.textContent || '';
        if (!keyword || !value || element.dataset.searchHighlightApplied === 'true') {
            return;
        }

        const range = element.dataset.searchMode === 'number'
            ? findNumberRange(value, keyword)
            : findTextRange(value, keyword);

        if (!range) {
            return;
        }

        const mark = document.createElement('mark');
        mark.className = 'member-search-highlight-mark';
        mark.textContent = value.slice(range.start, range.end);

        element.replaceChildren(
            document.createTextNode(value.slice(0, range.start)),
            mark,
            document.createTextNode(value.slice(range.end))
        );
        element.dataset.searchHighlightApplied = 'true';
    }

    function applySearchHighlights() {
        document.querySelectorAll('.member-search-highlight').forEach(highlightElement);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', applySearchHighlights, { once: true });
    } else {
        applySearchHighlights();
    }
})();
