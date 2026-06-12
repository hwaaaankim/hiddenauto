package com.dev.HiddenBATHAuto.rag.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class RagFileKnowledgeParser {

    private final RagExcelKnowledgeExtractor excelExtractor;

    public RagFileKnowledgeParser(RagExcelKnowledgeExtractor excelExtractor) {
        this.excelExtractor = excelExtractor;
    }

    public RagUploadedKnowledgeDocument parse(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();

        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return excelExtractor.extract(file);
        }

        if (isPlainTextLike(lower, contentType)) {
            String text = readText(file);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("parser", "RagFileKnowledgeParser");
            metadata.put("textLength", text.length());
            return new RagUploadedKnowledgeDocument(filename, contentType, "TEXT_FILE", text, metadata);
        }

        if (contentType != null && contentType.startsWith("image/")) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("parser", "RagFileKnowledgeParser");
            metadata.put("imageOnly", true);
            metadata.put("notice", "이미지 내용 자동 판독은 현재 텍스트 RAG 파서 범위 밖입니다. 이미지 설명을 메시지에 함께 입력해야 합니다.");
            return new RagUploadedKnowledgeDocument(
                    filename,
                    contentType,
                    "BINARY_IMAGE",
                    "[이미지 파일 업로드됨] 파일명: " + filename + "\n이미지 내용은 자동 텍스트 추출되지 않았습니다. 사용자의 설명 메시지와 함께 의미를 확인해야 합니다.",
                    metadata
            );
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("parser", "RagFileKnowledgeParser");
        metadata.put("unsupported", true);
        metadata.put("notice", "지원하지 않는 파일 형식입니다. xlsx/xls/txt/csv/md/json/html 파일을 권장합니다.");
        return new RagUploadedKnowledgeDocument(
                filename,
                contentType,
                "UNSUPPORTED_FILE",
                "[지원하지 않는 파일 형식] 파일명: " + filename + "\n파일 내용을 학습하려면 엑셀 또는 텍스트 기반 파일로 변환해 주세요.",
                metadata
        );
    }

    private boolean isPlainTextLike(String lower, String contentType) {
        if (lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".md")
                || lower.endsWith(".json") || lower.endsWith(".html") || lower.endsWith(".htm")) {
            return true;
        }
        return StringUtils.hasText(contentType)
                && (contentType.startsWith("text/") || contentType.contains("json") || contentType.contains("csv"));
    }

    private String readText(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String utf8 = new String(bytes, StandardCharsets.UTF_8);
            if (utf8.indexOf('\uFFFD') < 0) return utf8;
            return new String(bytes, Charset.forName("MS949"));
        } catch (Exception e) {
            throw new IllegalStateException("텍스트 파일 읽기 실패: " + e.getMessage(), e);
        }
    }
}
