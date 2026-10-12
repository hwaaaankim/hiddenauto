package com.dev.HiddenBATHAuto.service.productmaster;

import com.dev.HiddenBATHAuto.config.productmaster.ProductDrawingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Correlates image -> OpenAI -> geometry -> editable XML without logging credentials or image blobs. */
public final class ProductDrawingAiDiagnostics {
  private static final Logger LOG = LoggerFactory.getLogger(ProductDrawingAiDiagnostics.class);
  private static final int CHUNK_SIZE = 4000;
  private static final Pattern API_KEY = Pattern.compile("sk-(?:proj-)?[A-Za-z0-9_-]{16,}");
  private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._-]{8,}");
  private static final Pattern IMAGE_BASE64 = Pattern.compile("(?i)data:image/[A-Za-z0-9.+-]+(?:;[^,\\s]*)?;base64,[A-Za-z0-9+/=_\\-\\r\\n]+");
  private final ProductDrawingProperties properties;
  private final ObjectMapper mapper;

  public ProductDrawingAiDiagnostics(ProductDrawingProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  public static String traceId(String candidate) {
    if (candidate != null && candidate.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
      return UUID.fromString(candidate).toString();
    return UUID.randomUUID().toString();
  }

  /** Always records small technical stage metadata. Supply no image, instruction or label text here. */
  public void event(String trace, String stage, JsonNode details) {
    LOG.info("[ProductDrawingAI][{}][{}] {}", traceIdOrUnknown(trace), safeStage(stage), boundedJson(details, 4096));
  }

  public void failure(String trace, String stage, int status, String code) {
    String reason = scrub(code == null ? "unknown" : code).replaceAll("[\\r\\n\\t]", " ");
    if (reason.length() > 500) reason = reason.substring(0, 500);
    LOG.warn("[ProductDrawingAI][{}][{}] status={} reason={}", traceIdOrUnknown(trace), safeStage(stage), status,
        reason);
  }

  /** Detailed request/response values are opt-in and bounded. Base64 images and credential fields are omitted. */
  public void payload(String trace, String stage, JsonNode payload) {
    if (!properties.isDiagnosticLoggingEnabled()) return;
    writeChunks(trace, stage, json(redact(payload)));
  }

  public void xml(String trace, String stage, String xml) {
    if (!properties.isDiagnosticLoggingEnabled()) return;
    writeChunks(trace, stage, scrub(xml == null ? "" : xml));
  }

  public static String sha256(byte[] data) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
    catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable"); }
  }

  private void writeChunks(String trace, String stage, String value) {
    int originalLength = value.length();
    int limit = properties.getDiagnosticLogMaxChars();
    String bounded = value.substring(0, Math.min(originalLength, limit));
    int chunks = Math.max(1, (bounded.length() + CHUNK_SIZE - 1) / CHUNK_SIZE);
    for (int i = 0; i < chunks; i++) {
      String part = bounded.substring(i * CHUNK_SIZE, Math.min(bounded.length(), (i + 1) * CHUNK_SIZE));
      // JSON-string encoding prevents CR/LF values from manufacturing separate log entries.
      LOG.info("[ProductDrawingAI][{}][{}] chunk={}/{} originalChars={} truncated={} data={}",
          traceIdOrUnknown(trace), safeStage(stage), i + 1, chunks, originalLength, originalLength > limit,
          json(TextNode.valueOf(part)));
    }
  }

  private String boundedJson(JsonNode value, int max) {
    String safe = json(redact(value));
    return safe.length() <= max ? safe : safe.substring(0, max) + "...[truncated]";
  }

  private JsonNode redact(JsonNode node) {
    if (node == null) return mapper.getNodeFactory().nullNode();
    if (node.isObject()) {
      ObjectNode result = mapper.createObjectNode();
      node.fields().forEachRemaining(entry -> {
        String key = entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (key.contains("apikey") || key.equals("authorization") || key.contains("password")
            || key.contains("secret") || key.contains("csrf") || key.equals("accesstoken") || key.equals("refreshtoken"))
          result.put(entry.getKey(), "[REDACTED]");
        else result.set(entry.getKey(), redact(entry.getValue()));
      });
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = mapper.createArrayNode();
      for (JsonNode item : node) result.add(redact(item));
      return result;
    }
    if (node.isTextual()) {
      String value = node.asText();
      if (value.regionMatches(true, 0, "data:image/", 0, 11))
        return TextNode.valueOf("[IMAGE_DATA_OMITTED chars=" + value.length() + "]");
      return TextNode.valueOf(scrub(value));
    }
    return node.deepCopy();
  }

  private String json(JsonNode node) {
    try { return mapper.writeValueAsString(node); }
    catch (java.io.IOException exception) { return "\"[serialization unavailable]\""; }
  }

  private static String scrub(String text) {
    String withoutImages = IMAGE_BASE64.matcher(text).replaceAll("[IMAGE_BASE64_OMITTED]");
    return BEARER.matcher(API_KEY.matcher(withoutImages).replaceAll("[REDACTED_API_KEY]")).replaceAll("Bearer [REDACTED]");
  }

  private static String traceIdOrUnknown(String value) {
    return value != null && value.matches("[a-fA-F0-9-]{36}") ? value : "unknown";
  }

  private static String safeStage(String value) {
    return value != null && value.matches("[A-Z0-9_]{1,60}") ? value : "STAGE";
  }
}
