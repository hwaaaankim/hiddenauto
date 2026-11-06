package com.dev.HiddenBATHAuto.mcp;

import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.list;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioEngine.map;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.Definition;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.PricingGroup;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Answer;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Variant;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingCompatibility;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingCoverage;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingEngine;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingWire;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProductMasterMcpTools implements McpToolProvider {
  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ProductMasterMcpTools.class.getName());
  private final ProductMasterMcpDatabase db;
  private final ObjectMapper mapper;
  private static final Set<String> NAMES = Set.of("pm_info", "pm_schema", "pm_find_products",
      "pm_product_snapshot", "pm_read_product_json", "pm_group_snapshot", "pm_check_saved_pricing");

  ProductMasterMcpTools(ProductMasterMcpDatabase db, ObjectMapper mapper) {
    this.db = db; this.mapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  }
  public boolean hasTool(String name) { return NAMES.contains(name); }
  private static Map<String, Object> integer(long min, long max) {
    return Map.of("type", "integer", "minimum", min, "maximum", max);
  }
  private static Map<String, Object> string(int max) { return Map.of("type", "string", "maxLength", max); }
  private static Map<String, Object> enumeration(String... values) { return Map.of("type", "string", "enum", values); }
  private static Map<String, Object> tool(String name, String description, Map<String, Object> properties, String... required) {
    return Map.of("name", name, "description", description,
        "inputSchema", Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false),
        "annotations", Map.of("readOnlyHint", true, "destructiveHint", false, "idempotentHint", true, "openWorldHint", false),
        "securitySchemes", List.of(Map.of("type", "noauth")));
  }
  public List<Map<String, Object>> definitions() {
    Map<String, Object> id = integer(1, Long.MAX_VALUE);
    return List.of(
        tool("pm_info", "Read the connected DB name and diagnostic scope. Private tunnel only. No credentials returned.", Map.of()),
        tool("pm_schema", "Read actual columns and foreign keys of an allowlisted ProductMaster table. No arbitrary table or SQL.",
            Map.of("table", enumeration(ProductMasterMcpDatabase.TABLES.toArray(String[]::new))), "table"),
        tool("pm_find_products", "Search product name/catalog code. Keyset pagination by id, at most 50 rows. Start here to resolve product IDs.",
            Map.of("query", string(120), "afterId", integer(0, Long.MAX_VALUE), "limit", integer(1, 50))),
        tool("pm_product_snapshot", "Read product metadata, component links, stored inputs and JSON sizes in one DB snapshot. Includes row_version.",
            Map.of("productId", id), "productId"),
        tool("pm_read_product_json", "Read the exact saved definition/process/pricing JSON, preserving opaque keys. "
            + "JSON Pointer example /questions or /terms/0/branches/0/values. Arrays are paged; no JSON is rewritten. "
            + "Use smaller pointers/pages if the response is too large.",
            Map.of("productId", id, "document", enumeration("definition", "process", "pricing"),
                "pointer", string(512), "offset", integer(0, 20000), "limit", integer(1, 50)), "productId", "document"),
        tool("pm_group_snapshot", "Read actual group settings and one page of master options. "
            + "These common master options can differ from the product-local process JSON.",
            Map.of("groupId", id, "afterValueId", integer(0, Long.MAX_VALUE), "limit", integer(1, 50)), "groupId"),
        tool("pm_check_saved_pricing", "Run the existing pure pricing engine on SAVED data only. "
            + "action=validate performs whole-path registration checks; action=evaluate tests supplied answers. "
            + "Requires expectedVersion from the product snapshot, to detect concurrent edits. "
            + "Unsaved browser drafts cannot be read. Does not save formulas or test answers. "
            + "Returns diagnostic results even if the saved pricing fingerprint is stale; do not present them as an order quote.",
            Map.of("productId", id, "expectedVersion", integer(0, Long.MAX_VALUE),
                "action", enumeration("validate", "evaluate"),
                "answers", Map.of("type", "object", "additionalProperties", Map.of("type", "object"))),
            "productId", "expectedVersion", "action"));
  }

  public Map<String, Object> call(String name, JsonNode arguments) {
    try {
      JsonNode args = arguments.isMissingNode() ? mapper.createObjectNode() : arguments;
      checkArguments(name, args);
      Object data = switch (name) {
        case "pm_info" -> db.read(c -> Map.of("database", c.getCatalog(), "readOnly", true,
            "tables", ProductMasterMcpDatabase.TABLES, "transport", "private-loopback-streamable-http",
            "dataScope", "Saved ProductMaster only; no orders, customers, credentials or unsaved browser drafts."));
        case "pm_schema" -> db.read(c -> {
          String table = text(args, "table", null, 80);
          List<Map<String, Object>> columns = ProductMasterMcpDatabase.schema(c, table);
          List<Map<String, Object>> keys = ProductMasterMcpDatabase.query(c,
              "SELECT column_name, referenced_table_name, referenced_column_name "
                  + "FROM information_schema.key_column_usage WHERE table_schema=DATABASE() "
                  + "AND table_name=? AND referenced_table_name IS NOT NULL ORDER BY ordinal_position", table);
          return Map.of("database", c.getCatalog(), "table", table, "columns", columns, "foreignKeys", keys);
        });
        case "pm_find_products" -> db.read(c -> {
          String search = text(args, "query", "", 120).replace("!", "!!").replace("%", "!%").replace("_", "!_");
          int limit = (int) number(args, "limit", 20, 1, 50);
          List<Map<String, Object>> rows = ProductMasterMcpDatabase.query(c,
              "SELECT " + ProductMasterMcpDatabase.PRODUCT_COLUMNS + " FROM tb_pm_product WHERE id>? "
                  + "AND (product_name LIKE ? ESCAPE '!' OR catalog_code LIKE ? ESCAPE '!') ORDER BY id LIMIT ?",
              number(args, "afterId", 0, 0, Long.MAX_VALUE), "%" + search + "%", "%" + search + "%", limit + 1);
          boolean more = rows.size() > limit; List<Map<String, Object>> page = rows.stream().limit(limit).toList();
          return Map.of("rows", page, "hasMore", more, "nextAfterId", more ? page.get(page.size()-1).get("id") : 0);
        });
        case "pm_product_snapshot" -> db.read(c -> {
          long id = number(args, "productId", -1, 1, Long.MAX_VALUE);
          Map<String, Object> product = ProductMasterMcpDatabase.product(c, id);
          Map<String, Object> documents = new LinkedHashMap<>();
          for (String document : List.of("definition", "process", "pricing")) {
            String column = column(document); Object raw = product.remove(column);
            documents.put(document, Map.of("column", column, "present", raw != null && !raw.toString().isBlank(),
                "characters", raw == null ? 0 : raw.toString().length()));
          }
          return Map.of("database", c.getCatalog(), "product", product, "documents", documents,
              "components", ProductMasterMcpDatabase.components(c, id), "inputs", ProductMasterMcpDatabase.inputs(c, id));
        });
        case "pm_read_product_json" -> db.read(c -> {
          Map<String, Object> product = ProductMasterMcpDatabase.product(c, number(args, "productId", -1, 1, Long.MAX_VALUE));
          String document = text(args, "document", null, 16), col = column(document);
          Object raw = product.get(col);
          if (raw == null || raw.toString().isBlank()) throw new IllegalArgumentException("No saved " + document + " JSON.");
          String pointer = text(args, "pointer", "", 512);
          if (!pointer.isEmpty() && !pointer.startsWith("/")) throw new IllegalArgumentException("Use an RFC 6901 JSON Pointer.");
          JsonNode selected = mapper.readTree(raw.toString()).at(pointer);
          if (selected.isMissingNode()) throw new IllegalArgumentException("JSON Pointer not found.");
          Map<String, Object> out = identity(product);
          out.put("document", document); out.put("column", col); out.put("pointer", pointer);
          if (selected.isArray()) {
            int start = (int) number(args, "offset", 0, 0, 20000), limit = (int) number(args, "limit", 20, 1, 50);
            if (start > selected.size()) throw new IllegalArgumentException("offset exceeds the array length.");
            List<JsonNode> page = new ArrayList<>();
            for (int i = start; i < Math.min(selected.size(), start + limit); i++) page.add(selected.get(i));
            out.put("value", page); out.put("totalItems", selected.size()); out.put("offset", start);
            out.put("hasMore", start + page.size() < selected.size());
          } else { out.put("value", selected); }
          return out;
        });
        case "pm_group_snapshot" -> db.read(c -> {
          long id = number(args, "groupId", -1, 1, Long.MAX_VALUE);
          List<Map<String, Object>> groups = ProductMasterMcpDatabase.query(c,
              "SELECT id, group_code, customer_label, management_label, production_label, system_role, "
                  + "studio_control, non_standard, ask_question, price_impact, active, studio_fields_json, "
                  + "question_text, customer_guide, row_version, updated_at FROM tb_pm_attribute_group WHERE id=?", id);
          if (groups.isEmpty()) throw new IllegalArgumentException("Group not found.");
          int limit = (int) number(args, "limit", 20, 1, 50);
          List<Map<String, Object>> values = ProductMasterMcpDatabase.query(c,
              "SELECT id, value_code, customer_label, management_label, production_label, active, sort_order, "
                  + "row_version, updated_at FROM tb_pm_attribute_value WHERE group_id=? AND id>? ORDER BY id LIMIT ?",
              id, number(args, "afterValueId", 0, 0, Long.MAX_VALUE), limit + 1);
          boolean more = values.size() > limit; List<Map<String, Object>> page = values.stream().limit(limit).toList();
          return Map.of("group", groups.get(0), "values", page, "hasMore", more,
              "nextAfterValueId", more ? page.get(page.size()-1).get("id") : 0);
        });
        case "pm_check_saved_pricing" -> checkPricing(args);
        default -> throw new IllegalArgumentException("Unknown tool.");
      };
      Map<String, Object> out = Map.of("readAt", Instant.now().toString(), "data", data);
      return Map.of("content", List.of(Map.of("type", "text", "text", mapper.writeValueAsString(out))),
          "structuredContent", out, "isError", false);
    } catch (IllegalArgumentException e) {
      return failure(e.getMessage() == null ? "Invalid tool arguments or saved configuration." : e.getMessage());
    } catch (Exception e) {
      LOG.warning("ProductMaster MCP read failed: tool=" + name + ", type=" + e.getClass().getSimpleName()
          + (e instanceof java.sql.SQLException sql ? ", SQLState=" + sql.getSQLState() : ""));
      return failure("Unable to read/interpret saved ProductMaster data. Check the dedicated SELECT account, schema and saved JSON locally.");
    }
  }

  private record Saved(Map<String, Object> product, Process process, Definition pricing,
      List<PricingGroup> groups, Map<String, Answer> fixed) {}

  private Object checkPricing(JsonNode args) throws Exception {
    long id = number(args, "productId", -1, 1, Long.MAX_VALUE);
    Saved saved = db.read(c -> load(c, id));
    long version = number(args, "expectedVersion", -1, 0, Long.MAX_VALUE);
    if (((Number) saved.product().get("row_version")).longValue() != version)
      throw new IllegalArgumentException("Product changed. Read the current snapshot and retry with its row_version.");
    String action = text(args, "action", null, 16);
    boolean current = ProductPricingCompatibility.fingerprint(saved.process(), saved.fixed())
        .equals(saved.pricing().schemaFingerprint());
    Map<String, Object> result = identity(saved.product());
    result.put("diagnosticOnly", true); result.put("fingerprintMatches", current);
    if (action.equals("validate")) {
      if (args.has("answers")) throw new IllegalArgumentException("answers is only supported for action=evaluate.");
      result.put("validation", ProductPricingCoverage.validateRegistration(saved.process(), saved.groups(), saved.pricing(), saved.fixed(), false));
    } else if (action.equals("evaluate")) {
      if (!args.path("answers").isObject()) throw new IllegalArgumentException("evaluate requires an answers object, keyed by the saved group/field keys.");
      Map<String, Answer> answers = mapper.convertValue(args.get("answers"), new TypeReference<Map<String, Answer>>() {});
      answers.putAll(saved.fixed());
      result.put("calculation", ProductPricingWire.response(ProductPricingEngine.calculate(saved.process(), saved.groups(), saved.pricing(), answers)));
    } else throw new IllegalArgumentException("action must be validate or evaluate.");
    return result;
  }

  private Saved load(Connection c, long id) throws Exception {
    Map<String, Object> product = ProductMasterMcpDatabase.product(c, id);
    if (product.get("studio_process_json") == null || product.get("studio_price_json") == null)
      throw new IllegalArgumentException("A saved process and pricing formula are required.");
    Process process = mapper.readValue(product.get("studio_process_json").toString(), Process.class);
    Definition price = mapper.readValue(product.get("studio_price_json").toString(), Definition.class);
    List<Variant> variants = mapper.readValue(product.get("studio_definition_json").toString(), new TypeReference<List<Variant>>() {});
    Set<Long> impact = new LinkedHashSet<>(); Map<String, Answer> fixed = new LinkedHashMap<>();
    // Match ProductPricingService.impactGroups: current component links own price-impact membership.
    for (Map<String, Object> component : ProductMasterMcpDatabase.components(c, id))
      if (truth(component.get("price_impact")) && !"SUBCATEGORY".equals(component.get("system_role")))
        impact.add(((Number) component.get("group_id")).longValue());
    for (Variant variant : variants) {
      List<Map<String, Object>> rows = ProductMasterMcpDatabase.query(c,
          "SELECT group_code, non_standard, price_impact, system_role FROM tb_pm_attribute_group WHERE id=?", variant.groupId());
      if (rows.isEmpty()) throw new IllegalArgumentException("A saved product references a missing group.");
      Map<String, Object> group = rows.get(0);
      if (truth(group.get("non_standard"))) continue;
      List<String> choices = ProductMasterMcpDatabase.query(c,
          "SELECT id, value_code FROM tb_pm_attribute_value WHERE group_id=? ORDER BY sort_order, id", variant.groupId())
          .stream().filter(v -> list(variant.valueIds()).contains(((Number) v.get("id")).longValue()))
          .map(v -> v.get("value_code").toString()).toList();
      fixed.put(group.get("group_code").toString(), new Answer(choices, map(variant.inputs())));
    }
    return new Saved(product, process, price, ProductPricingEngine.groups(process, impact), fixed);
  }
  private static boolean truth(Object v) { return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue() != 0 || "1".equals(v); }
  private static Map<String, Object> identity(Map<String, Object> product) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (String key : List.of("id", "product_name", "catalog_code", "row_version", "updated_at")) out.put(key, product.get(key));
    return out;
  }
  private static String column(String document) {
    return switch (document) {
      case "definition" -> "studio_definition_json";
      case "process" -> "studio_process_json";
      case "pricing" -> "studio_price_json";
      default -> throw new IllegalArgumentException("document must be definition, process or pricing.");
    };
  }
  private static Map<String, Object> failure(String message) {
    return Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", message)));
  }
  private void checkArguments(String name, JsonNode args) {
    if (!args.isObject()) throw new IllegalArgumentException("arguments must be an object.");
    Map<String, Object> definition = definitions().stream().filter(d -> d.get("name").equals(name)).findFirst().orElseThrow();
    JsonNode schema = mapper.valueToTree(definition.get("inputSchema"));
    args.fieldNames().forEachRemaining(key -> {
      if (!schema.path("properties").has(key)) throw new IllegalArgumentException("Unknown argument: " + key);
    });
    schema.path("required").forEach(key -> {
      if (!args.has(key.asText()) || args.get(key.asText()).isNull()) throw new IllegalArgumentException("Missing argument: " + key.asText());
    });
    args.fields().forEachRemaining(entry -> {
      JsonNode s = schema.path("properties").get(entry.getKey()), v = entry.getValue();
      String type = s.path("type").asText();
      if (type.equals("string")) {
        text(args, entry.getKey(), null, s.path("maxLength").asInt(512));
        if (s.has("enum")) {
          boolean found = false; for (JsonNode option : s.get("enum")) if (option.equals(v)) found = true;
          if (!found) throw new IllegalArgumentException("Invalid option: " + entry.getKey());
        }
      } else if (type.equals("integer")) number(args, entry.getKey(), -1, s.path("minimum").asLong(), s.path("maximum").asLong());
      else if (type.equals("object") && !v.isObject()) throw new IllegalArgumentException(entry.getKey() + " must be an object.");
    });
  }
  private static long number(JsonNode args, String key, long fallback, long min, long max) {
    if (!args.has(key)) {
      if (fallback < min || fallback > max) throw new IllegalArgumentException("Missing integer: " + key);
      return fallback;
    }
    JsonNode v = args.get(key);
    if (!v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() < min || v.longValue() > max)
      throw new IllegalArgumentException("Invalid integer: " + key);
    return v.longValue();
  }
  private static String text(JsonNode args, String key, String fallback, int max) {
    if (!args.has(key)) {
      if (fallback == null) throw new IllegalArgumentException("Missing string: " + key);
      return fallback;
    }
    JsonNode v = args.get(key);
    if (!v.isTextual() || v.asText().length() > max) throw new IllegalArgumentException("Invalid string: " + key);
    return v.asText();
  }
}
