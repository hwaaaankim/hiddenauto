package com.dev.HiddenBATHAuto.rag.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class RagUploadedFileTextExtractor {

    private static final int MAX_TEXT_LENGTH = 120_000;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 비어 있습니다.");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (lower.endsWith(".xlsx")) {
                return truncate(extractXlsx(bytes));
            }
            if (lower.endsWith(".xls")) {
                throw new IllegalArgumentException("구형 xls 파일은 현재 기본 파서로 분석할 수 없습니다. xlsx 또는 csv로 저장해서 업로드해 주세요.");
            }
            if (lower.endsWith(".csv")) {
                return truncate(decodeText(bytes, StandardCharsets.UTF_8));
            }
            return truncate(decodeText(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("파일 텍스트 추출 실패: " + e.getMessage(), e);
        }
    }

    private String decodeText(byte[] bytes, Charset charset) {
        String text = new String(bytes, charset);
        if (text.indexOf('\uFFFD') >= 0) {
            text = new String(bytes, Charset.forName("MS949"));
        }
        return text;
    }

    private String extractXlsx(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = unzip(bytes);
        List<String> sharedStrings = readSharedStrings(entries.get("xl/sharedStrings.xml"));
        StringBuilder out = new StringBuilder();
        entries.keySet().stream()
                .filter(name -> name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
                .sorted()
                .forEach(name -> {
                    try {
                        out.append("\n[시트: ").append(name).append("]\n");
                        out.append(readSheet(entries.get(name), sharedStrings)).append('\n');
                    } catch (Exception e) {
                        out.append("\n[시트 분석 실패: ").append(name).append(" / ").append(e.getMessage()).append("]\n");
                    }
                });
        String result = out.toString().trim();
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("xlsx에서 읽을 수 있는 셀 텍스트가 없습니다.");
        }
        return result;
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zis.transferTo(bos);
                entries.put(entry.getName(), bos.toByteArray());
            }
        }
        return entries;
    }

    private List<String> readSharedStrings(byte[] xml) throws Exception {
        List<String> values = new ArrayList<>();
        if (xml == null) return values;
        Document doc = parseXml(xml);
        NodeList sis = doc.getElementsByTagName("si");
        for (int i = 0; i < sis.getLength(); i++) {
            values.add(textContent((Element) sis.item(i)).trim());
        }
        return values;
    }

    private String readSheet(byte[] xml, List<String> sharedStrings) throws Exception {
        Document doc = parseXml(xml);
        NodeList rows = doc.getElementsByTagName("row");
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows.getLength(); r++) {
            Element row = (Element) rows.item(r);
            NodeList cells = row.getElementsByTagName("c");
            List<String> rowValues = new ArrayList<>();
            for (int c = 0; c < cells.getLength(); c++) {
                Element cell = (Element) cells.item(c);
                rowValues.add(readCell(cell, sharedStrings));
            }
            String line = String.join("\t", rowValues).trim();
            if (StringUtils.hasText(line)) out.append(line).append('\n');
            if (out.length() > MAX_TEXT_LENGTH) break;
        }
        return out.toString();
    }

    private String readCell(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList inline = cell.getElementsByTagName("is");
            if (inline.getLength() > 0) return textContent((Element) inline.item(0)).trim();
        }
        String value = firstChildText(cell, "v");
        if ("s".equals(type) && StringUtils.hasText(value)) {
            try {
                int idx = Integer.parseInt(value.trim());
                if (idx >= 0 && idx < sharedStrings.size()) return sharedStrings.get(idx);
            } catch (Exception ignored) {}
        }
        return value == null ? "" : value.trim();
    }

    private Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private String firstChildText(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return "";
        Node node = nodes.item(0);
        return node == null ? "" : node.getTextContent();
    }

    private String textContent(Element element) {
        return element == null ? "" : element.getTextContent();
    }

    private String truncate(String value) {
        if (value == null) return "";
        if (value.length() <= MAX_TEXT_LENGTH) return value;
        return value.substring(0, MAX_TEXT_LENGTH) + "\n... 파일 내용이 길어 일부만 분석했습니다 ...";
    }
}
