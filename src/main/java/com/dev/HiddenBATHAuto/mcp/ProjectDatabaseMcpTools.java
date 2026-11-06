package com.dev.HiddenBATHAuto.mcp;

import static com.dev.HiddenBATHAuto.mcp.ProjectDatabaseMcpReader.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

/** Whole-project database tools plus the existing ProductMaster-specific diagnosis tools. */
final class ProjectDatabaseMcpTools implements McpToolProvider {
  private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ProjectDatabaseMcpTools.class.getName());
  private static final Set<String> NAMES = Set.of("db_connections", "db_tables", "db_table_schema", "db_query", "db_read_cell");
  private final Map<String, Scope> connections;
  private final McpToolProvider productTools;
  private final ObjectMapper mapper;
  private final ProjectDatabaseMcpReader reader;

  ProjectDatabaseMcpTools(Map<String, Scope> connections, McpToolProvider productTools, ObjectMapper mapper) {
    this.connections = Collections.unmodifiableMap(new LinkedHashMap<>(connections)); this.productTools = productTools;
    this.mapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    reader = new ProjectDatabaseMcpReader(this.mapper);
  }
  public boolean hasTool(String name) { return NAMES.contains(name) || productTools.hasTool(name); }
  private static Map<String, Object> string(int max) { return Map.of("type", "string", "maxLength", max); }
  private static Map<String, Object> integer(int min, int max) { return Map.of("type", "integer", "minimum", min, "maximum", max); }
  private static Map<String, Object> choice(String... values) { return Map.of("type", "string", "enum", values); }
  private static Map<String, Object> object(Map<String, Object> properties, String... required) {
    return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
  }
  private static Map<String, Object> array(Object items, int max) { return Map.of("type", "array", "items", items, "maxItems", max); }
  private static Map<String, Object> tool(String name, String description, Map<String, Object> properties, String... required) {
    return Map.of("name", name, "description", description, "inputSchema", object(properties, required),
        "annotations", Map.of("readOnlyHint", true, "destructiveHint", false, "idempotentHint", true, "openWorldHint", false),
        "securitySchemes", List.of(Map.of("type", "noauth")));
  }
  public List<Map<String, Object>> definitions() {
    Map<String, Object> connection = choice(connections.keySet().toArray(String[]::new));
    Map<String, Object> filter = object(Map.of("column", string(200),
        "op", choice("EQ", "NE", "GT", "GE", "LT", "LE", "IN", "CONTAINS", "IS_NULL", "NOT_NULL"),
        "value", Map.of("type", List.of("string", "number", "boolean", "null")),
        "values", array(Map.of("type", List.of("string", "number", "boolean")), 100)), "column", "op");
    List<Map<String, Object>> out = new ArrayList<>(List.of(
        tool("db_connections", "Discover explicitly configured project DB aliases and live database/schema/vendor. "
            + "Use the requested alias explicitly. dev/www/RAG are separate data sources. No credentials or URLs returned.", Map.of()),
        tool("db_tables", "List ALL user tables/views in one configured database/schema, including orders, tasks, members, companies, AS, logs and RAG if present. "
            + "No ProductMaster table allowlist. Case-sensitive table names are returned exactly. Paginate to inspect the full schema.",
            Map.of("connection", connection, "search", string(120), "offset", integer(0, 1000000), "limit", integer(1, 200)), "connection"),
        tool("db_table_schema", "Read live columns/types, primary key, indexes and declared foreign keys in both directions for ANY discovered table. "
            + "Credential column names remain visible but values are redacted. Missing DB foreign keys do not mean no application relationships.",
            Map.of("connection", connection, "table", string(160)), "connection", "table"),
        tool("db_query", "Read bounded rows/joins/aggregates from ANY discovered project tables. No SQL strings or expressions accepted. "
            + "Main table alias=t0, successive joins=t1..t4. Column refs use t0.id, t1.task_id, etc. "
            + "Filters are AND. Select columns or grouped aggregates; SUM/AVG numeric only. Join can multiply rows; check relationship cardinality. "
            + "Output columns map c0,c1,... to source refs. Decimal amounts and unsafe large integers preserve precision as strings. "
            + "Long cells return previews; use db_read_cell. Rows are saved DB data; unsaved browser drafts are unavailable.",
            Map.of("connection", connection, "table", string(160), "columns", array(string(200), 50),
                "joins", array(object(Map.of("table", string(160), "type", choice("LEFT", "INNER"), "left", string(200), "right", string(200)), "table", "left", "right"), 4),
                "filters", array(filter, 30),
                "aggregates", array(object(Map.of("function", choice("COUNT", "COUNT_DISTINCT", "SUM", "AVG", "MIN", "MAX"), "column", string(200)), "function", "column"), 10),
                "groupBy", array(string(200), 20),
                "orderBy", array(object(Map.of("column", string(200), "direction", choice("ASC", "DESC")), "column"), 20),
                "offset", integer(0, 1000000), "limit", integer(1, 200)), "connection", "table"),
        tool("db_read_cell", "Read one saved text/JSON cell from ANY discovered table using its actual row key. "
            + "mode=text pages characters (up to 8000); mode=json applies RFC 6901 pointer then pages arrays (up to 50 items). "
            + "Supply all primary key columns. No-PK tables require identifying columns matching exactly one row. "
            + "Credential/binary bodies are not exposed; credential keys in parsed JSON are redacted before paging.",
            Map.of("connection", connection, "table", string(160), "column", string(160),
                "key", Map.of("type", "object", "minProperties", 1, "maxProperties", 8,
                    "additionalProperties", Map.of("type", List.of("string", "number", "boolean"))),
                "mode", choice("text", "json"), "pointer", string(512), "offset", integer(0, 8388608), "limit", integer(1, 8000)),
            "connection", "table", "column", "key")));
    out.addAll(productTools.definitions()); return List.copyOf(out);
  }

  public Map<String, Object> call(String name, JsonNode arguments) {
    if (!NAMES.contains(name)) return productTools.call(name, arguments);
    try {
      JsonNode args = arguments.isMissingNode() ? mapper.createObjectNode() : arguments;
      require(args.isObject(), "arguments must be an object.");
      Object data;
      if (name.equals("db_connections")) {
        keys(args); List<Map<String, Object>> sources = new ArrayList<>();
        for (Scope scope : connections.values()) sources.add(scope.database().read(c -> Map.of("connection", scope.alias(),
            "database", c.getCatalog(), "schema", scope.schema(), "vendor", c.getMetaData().getDatabaseProductName(),
            "scope", "All tables/views in this configured database/schema", "readOnly", true)));
        data = Map.of("connections", sources, "primaryProductMasterConnection", "dev", "credentialColumnValues", "redacted");
      } else {
        String alias = text(args, "connection", null, 32); Scope scope = connections.get(alias);
        require(scope != null, "Unknown/disabled connection. Run db_connections first.");
        Object result = scope.database().read(c -> switch (name) {
          case "db_tables" -> {
            keys(args, "connection", "search", "offset", "limit");
            String search = text(args, "search", "", 120).toLowerCase(Locale.ROOT);
            List<Map<String, Object>> all = reader.tables(c, scope).stream()
                .filter(t -> t.get("name").toString().toLowerCase(Locale.ROOT).contains(search)).toList();
            int start = ProjectDatabaseMcpReader.integer(args, "offset", 0, 0, 1000000), limit = ProjectDatabaseMcpReader.integer(args, "limit", 100, 1, 200);
            require(start <= all.size(), "offset exceeds table count.");
            int end = Math.min(start + limit, all.size()); Map<String, Object> page = new LinkedHashMap<>();
            page.put("tables", all.subList(start, end)); page.put("totalTables", all.size());
            page.put("hasMore", end < all.size()); page.put("nextOffset", end < all.size() ? end : null); yield page;
          }
          case "db_table_schema" -> { keys(args, "connection", "table"); yield reader.schema(c, scope, text(args, "table", null, 160)); }
          case "db_query" -> reader.query(c, scope, args);
          case "db_read_cell" -> reader.readCell(c, scope, args);
          default -> throw new IllegalArgumentException("Unknown tool.");
        });
        data = Map.of("connection", alias, "schema", scope.schema(), "result", result);
      }
      Map<String, Object> out = Map.of("readAt", Instant.now().toString(), "data", data);
      return Map.of("isError", false, "structuredContent", out,
          "content", List.of(Map.of("type", "text", "text", mapper.writeValueAsString(out))));
    } catch (IllegalArgumentException e) {
      return failure(e.getMessage() == null ? "Invalid database request." : e.getMessage());
    } catch (Exception e) {
      LOG.warning("Project MCP read failed: tool=" + name + ", type=" + e.getClass().getSimpleName()
          + (e instanceof java.sql.SQLException sql ? ", SQLState=" + sql.getSQLState() : ""));
      return failure("Database read failed. Check SELECT grants, connection/schema, column types and the 8-second query timeout locally. No data were modified.");
    }
  }
  private static Map<String, Object> failure(String message) {
    return Map.of("isError", true, "content", List.of(Map.of("type", "text", "text", message)));
  }
}
