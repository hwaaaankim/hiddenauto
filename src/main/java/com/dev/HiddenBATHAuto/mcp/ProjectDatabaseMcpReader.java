package com.dev.HiddenBATHAuto.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** Metadata-validated SELECT builder. Input never becomes an SQL expression or function name. */
final class ProjectDatabaseMcpReader {
  record Scope(String alias, ProductMasterMcpDatabase database, String schema) {}
  record Column(String name, int jdbcType, String type, boolean sensitive) {}
  record Table(String name, LinkedHashMap<String, Column> columns, List<String> primaryKey) {}
  record Ref(String alias, Column column) {}
  private static final Pattern SECRET = Pattern.compile("(^|_)(password|passwd|pwd|secret|token|api_key|apikey|private_key|credential|credentials|authorization|cookie|session_id|otp|verification_code|reset_code|auth_key|signing_key)(_|$)", Pattern.CASE_INSENSITIVE);
  private final ObjectMapper mapper;
  ProjectDatabaseMcpReader(ObjectMapper mapper) { this.mapper = mapper; }

  private static String namespace(Scope s) { return s.schema() == null || s.schema().isBlank() ? null : s.schema(); }
  private static String quote(Connection c, String identifier) throws SQLException {
    String q = c.getMetaData().getIdentifierQuoteString().trim();
    if (q.isEmpty()) throw new SQLException("Database driver has no identifier quoting support.");
    return q + identifier.replace(q, q + q) + q;
  }
  private static String sqlTable(Connection c, Scope s, String table) throws SQLException {
    String schema = namespace(s);
    // Catalog is explicit even when a MariaDB connection user can see more than one database.
    return quote(c, schema == null ? c.getCatalog() : schema) + "." + quote(c, table);
  }
  private static String pattern(DatabaseMetaData md, String literal) throws SQLException {
    String esc = md.getSearchStringEscape();
    return literal.replace(esc, esc + esc).replace("_", esc + "_").replace("%", esc + "%");
  }
  private static boolean binary(int type) {
    return Set.of(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB).contains(type);
  }
  private static boolean binary(Column column) { return binary(column.jdbcType()) && !"uuid".equalsIgnoreCase(column.type()); }
  private static boolean numeric(int type) {
    return Set.of(Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.DECIMAL,
        Types.NUMERIC, Types.REAL, Types.FLOAT, Types.DOUBLE).contains(type);
  }
  private static boolean secret(String name, int type) {
    return SECRET.matcher(name.replaceAll("([a-z])([A-Z])", "$1_$2")).find() && !numeric(type) && type != Types.BOOLEAN && type != Types.BIT;
  }
  static void keys(JsonNode node, String... allowed) {
    require(node.isObject(), "Expected an object."); Set<String> set = Set.of(allowed);
    node.fieldNames().forEachRemaining(k -> require(set.contains(k), "Unknown argument: " + k));
  }
  static void require(boolean yes, String message) { if (!yes) throw new IllegalArgumentException(message); }
  static String text(JsonNode node, String key, String fallback, int max) {
    if (!node.has(key)) { require(fallback != null, "Missing string: " + key); return fallback; }
    JsonNode value = node.get(key);
    require(value.isTextual() && value.textValue().length() <= max, "Invalid string: " + key);
    return value.textValue();
  }
  static int integer(JsonNode node, String key, int fallback, int min, int max) {
    if (!node.has(key)) return fallback;
    JsonNode value = node.get(key);
    require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= min && value.intValue() <= max,
        "Invalid integer: " + key); return value.intValue();
  }
  private static List<JsonNode> array(JsonNode node, String key, int max) {
    if (!node.has(key)) return List.of(); JsonNode value = node.get(key);
    require(value.isArray() && value.size() <= max, "Invalid array: " + key);
    List<JsonNode> out = new ArrayList<>(); value.forEach(out::add); return out;
  }
  private static List<String> strings(JsonNode node, String key, int max) {
    return array(node, key, max).stream().map(n -> {
      require(n.isTextual() && n.textValue().length() <= 200, "Expected a column name string."); return n.textValue();
    }).toList();
  }

  List<Map<String, Object>> tables(Connection c, Scope scope) throws SQLException {
    List<Map<String, Object>> out = new ArrayList<>();
    try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), namespace(scope), "%",
        new String[]{"TABLE", "VIEW", "BASE TABLE", "MATERIALIZED VIEW"})) {
      while (rs.next()) {
        String catalog = rs.getString("TABLE_CAT"), schema = rs.getString("TABLE_SCHEM");
        if (catalog != null && !catalog.equals(c.getCatalog())) continue;
        if (namespace(scope) != null && !namespace(scope).equals(schema)) continue;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", rs.getString("TABLE_NAME")); row.put("type", rs.getString("TABLE_TYPE"));
        row.put("schema", schema); out.add(row);
      }
    }
    out.sort(Comparator.comparing(r -> r.get("name").toString())); return out;
  }

  private Table table(Connection c, Scope scope, String name) throws SQLException {
    require(tables(c, scope).stream().anyMatch(t -> name.equals(t.get("name"))), "Table not found in this connection/schema: " + name);
    DatabaseMetaData md = c.getMetaData(); LinkedHashMap<String, Column> cols = new LinkedHashMap<>();
    try (ResultSet rs = md.getColumns(c.getCatalog(), namespace(scope), pattern(md, name), "%")) {
      while (rs.next()) {
        if (!name.equals(rs.getString("TABLE_NAME"))) continue;
        String col = rs.getString("COLUMN_NAME"); int type = rs.getInt("DATA_TYPE");
        cols.put(col, new Column(col, type, rs.getString("TYPE_NAME"), secret(col, type)));
      }
    }
    require(!cols.isEmpty(), "No visible columns for table.");
    TreeMap<Short, String> pk = new TreeMap<>();
    try (ResultSet rs = md.getPrimaryKeys(c.getCatalog(), namespace(scope), name)) {
      while (rs.next()) pk.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
    }
    return new Table(name, cols, List.copyOf(pk.values()));
  }

  Map<String, Object> schema(Connection c, Scope scope, String name) throws SQLException {
    Table table = table(c, scope, name); List<Map<String, Object>> cols = new ArrayList<>();
    try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), namespace(scope), pattern(c.getMetaData(), name), "%")) {
      while (rs.next()) {
        if (!name.equals(rs.getString("TABLE_NAME"))) continue;
        String col = rs.getString("COLUMN_NAME"); Column detail = table.columns().get(col);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", col); row.put("type", detail.type()); row.put("jdbcType", detail.jdbcType());
        row.put("size", rs.getInt("COLUMN_SIZE")); row.put("nullable", rs.getInt("NULLABLE"));
        row.put("ordinal", rs.getInt("ORDINAL_POSITION")); row.put("redacted", detail.sensitive()); cols.add(row);
      }
    }
    List<Map<String, Object>> imports = foreignKeys(c, scope, name, false), exports = foreignKeys(c, scope, name, true);
    List<Map<String, Object>> indexes = new ArrayList<>();
    try (ResultSet rs = c.getMetaData().getIndexInfo(c.getCatalog(), namespace(scope), name, false, true)) {
      while (rs.next()) if (rs.getString("INDEX_NAME") != null) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", rs.getString("INDEX_NAME")); row.put("column", rs.getString("COLUMN_NAME"));
        row.put("unique", !rs.getBoolean("NON_UNIQUE")); row.put("position", rs.getShort("ORDINAL_POSITION")); indexes.add(row);
      }
    }
    return Map.of("table", name, "columns", cols, "primaryKey", table.primaryKey(), "indexes", indexes,
        "foreignKeys", imports, "referencedBy", exports,
        "relationshipNote", "Only declared DB foreign keys are shown; empty results do not prove no application relationship.");
  }
  private List<Map<String, Object>> foreignKeys(Connection c, Scope scope, String table, boolean exported) throws SQLException {
    List<Map<String, Object>> out = new ArrayList<>(); DatabaseMetaData md = c.getMetaData();
    try (ResultSet rs = exported ? md.getExportedKeys(c.getCatalog(), namespace(scope), table)
        : md.getImportedKeys(c.getCatalog(), namespace(scope), table)) {
      while (rs.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String key : List.of("FK_NAME", "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME",
            "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME")) row.put(key, rs.getString(key));
        row.put("position", rs.getShort("KEY_SEQ")); out.add(row);
      }
    }
    return out;
  }

  private static Ref ref(String value, Map<String, Table> tables) {
    int dot = value.indexOf('.'); String alias = dot < 0 ? "t0" : value.substring(0, dot);
    String col = dot < 0 ? value : value.substring(dot + 1); Table table = tables.get(alias);
    require(table != null && table.columns().containsKey(col), "Unknown column reference: " + value);
    return new Ref(alias, table.columns().get(col));
  }
  private static String sqlRef(Connection c, Ref ref) throws SQLException { return ref.alias() + "." + quote(c, ref.column().name()); }
  private static String textCast(Connection c, String expression) throws SQLException {
    String vendor = c.getMetaData().getDatabaseProductName();
    String type = vendor.contains("PostgreSQL") ? "TEXT" : vendor.contains("MariaDB") || vendor.contains("MySQL") ? "CHAR" : "VARCHAR";
    return "CAST(" + expression + " AS " + type + ")";
  }
  private static boolean textLike(int type) {
    return Set.of(Types.CLOB, Types.NCLOB, Types.CHAR, Types.NCHAR, Types.VARCHAR, Types.NVARCHAR,
        Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.OTHER, Types.SQLXML, Types.ARRAY).contains(type);
  }
  private static void usable(Ref ref) {
    require(!ref.column().sensitive(), "Credential columns cannot be used in filters, joins, sorting or aggregates.");
    require(!binary(ref.column()), "Binary columns cannot be used in this operation.");
  }
  private static Object parameter(Column col, JsonNode value) {
    require(value != null && value.isValueNode() && !value.isNull(), "Use IS_NULL/NOT_NULL for null values.");
    try {
      if (numeric(col.jdbcType())) return new BigDecimal(value.asText());
      if (col.jdbcType() == Types.BOOLEAN || col.jdbcType() == Types.BIT) {
        require(value.isBoolean() || Set.of("0", "1", "true", "false").contains(value.asText()), "Invalid boolean filter.");
        return Set.of("1", "true").contains(value.asText());
      }
      if ("uuid".equalsIgnoreCase(col.type())) return UUID.fromString(value.asText());
      if (col.jdbcType() == Types.DATE) return java.sql.Date.valueOf(value.asText());
      if (col.jdbcType() == Types.TIMESTAMP_WITH_TIMEZONE || "timestamptz".equalsIgnoreCase(col.type())) return OffsetDateTime.parse(value.asText());
      if (col.jdbcType() == Types.TIMESTAMP) return Timestamp.valueOf(value.asText().replace('T', ' '));
      require(value.asText().length() <= 4000, "Filter value is too long."); return value.asText();
    } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Filter value does not match column type: " + col.name()); }
  }

  private static String where(Connection c, List<JsonNode> filters, Map<String, Table> aliases, List<Object> params) throws SQLException {
    List<String> parts = new ArrayList<>();
    for (JsonNode f : filters) {
      keys(f, "column", "op", "value", "values"); Ref col = ref(text(f, "column", null, 200), aliases); usable(col);
      String op = text(f, "op", null, 24), sql = sqlRef(c, col);
      if (op.equals("IS_NULL") || op.equals("NOT_NULL")) {
        require(!f.has("value") && !f.has("values"), "Null operators do not take a value.");
        parts.add(sql + (op.equals("IS_NULL") ? " IS NULL" : " IS NOT NULL"));
      } else if (op.equals("IN")) {
        List<JsonNode> values = array(f, "values", 100); require(!values.isEmpty() && !f.has("value"), "IN requires values.");
        values.forEach(v -> params.add(parameter(col.column(), v)));
        parts.add(sql + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")");
      } else if (op.equals("CONTAINS")) {
        require(!f.has("values") && f.has("value") && f.get("value").isTextual(), "CONTAINS requires a string value.");
        String v = text(f, "value", null, 4000).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        parts.add(textCast(c, sql) + " LIKE ? ESCAPE '!'"); params.add("%" + v + "%");
      } else {
        String operation = switch (op) { case "EQ" -> "="; case "NE" -> "<>"; case "GT" -> ">";
          case "GE" -> ">="; case "LT" -> "<"; case "LE" -> "<=";
          default -> throw new IllegalArgumentException("Unsupported filter operator."); };
        require(!f.has("values"), "This operator requires value, not values.");
        parts.add(sql + " " + operation + " ?"); params.add(parameter(col.column(), f.get("value")));
      }
    }
    return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
  }

  Map<String, Object> query(Connection c, Scope scope, JsonNode args) throws Exception {
    keys(args, "connection", "table", "columns", "joins", "filters", "aggregates", "groupBy", "orderBy", "offset", "limit");
    String name = text(args, "table", null, 160); Table main = table(c, scope, name);
    Map<String, Table> aliases = new LinkedHashMap<>(); aliases.put("t0", main);
    StringBuilder from = new StringBuilder(sqlTable(c, scope, name) + " t0");
    for (JsonNode join : array(args, "joins", 4)) {
      keys(join, "table", "type", "left", "right"); String alias = "t" + aliases.size();
      Table joined = table(c, scope, text(join, "table", null, 160)); aliases.put(alias, joined);
      Ref left = ref(text(join, "left", null, 200), aliases), right = ref(text(join, "right", null, 200), aliases);
      usable(left); usable(right);
      require(!left.alias().equals(right.alias()) && (left.alias().equals(alias) || right.alias().equals(alias)), "Join must link the new table to a preceding table.");
      String type = text(join, "type", "LEFT", 8); require(Set.of("LEFT", "INNER").contains(type), "Only LEFT and INNER joins are supported.");
      from.append(" ").append(type).append(" JOIN ").append(sqlTable(c, scope, joined.name())).append(" ").append(alias)
          .append(" ON ").append(sqlRef(c, left)).append("=").append(sqlRef(c, right));
    }
    List<JsonNode> aggregates = array(args, "aggregates", 10);
    List<String> group = strings(args, "groupBy", 20), selected = strings(args, "columns", 50);
    require(new HashSet<>(group).size() == group.size(), "Duplicate groupBy column.");
    if (selected.isEmpty()) selected = aggregates.isEmpty() ? main.columns().keySet().stream().map(k -> "t0." + k).toList() : group;
    require(selected.size() <= 200 && (!selected.isEmpty() || !aggregates.isEmpty()), "Select columns or aggregates.");
    require(aggregates.isEmpty() || new HashSet<>(selected).equals(new HashSet<>(group)), "Aggregated queries must select exactly groupBy columns.");
    List<String> selectSql = new ArrayList<>(); List<Map<String, Object>> descriptions = new ArrayList<>();
    int index = 0;
    for (String field : selected) {
      Ref r = ref(field, aliases); String label = "c" + index++;
      String expression = sqlRef(c, r);
      if (r.column().sensitive()) expression = "NULL";
      else if (binary(r.column())) expression = "CASE WHEN " + expression + " IS NULL THEN NULL ELSE '[BINARY: body omitted]' END";
      else if (textLike(r.column().jdbcType())) expression = "SUBSTRING(" + textCast(c, expression) + ",1,1025)";
      selectSql.add(expression + " AS " + label);
      descriptions.add(Map.of("key", label, "source", field, "redacted", r.column().sensitive()));
    }
    for (JsonNode aggregate : aggregates) {
      keys(aggregate, "function", "column"); String function = text(aggregate, "function", null, 20), field = text(aggregate, "column", null, 200);
      require(Set.of("COUNT", "COUNT_DISTINCT", "SUM", "AVG", "MIN", "MAX").contains(function), "Unsupported aggregate.");
      String expression;
      if (field.equals("*")) { require(function.equals("COUNT"), "Only COUNT accepts *."); expression = "*"; }
      else { Ref r = ref(field, aliases); usable(r);
        if (Set.of("SUM", "AVG").contains(function)) require(numeric(r.column().jdbcType()), "SUM/AVG require a numeric column.");
        expression = sqlRef(c, r);
      }
      String label = "c" + index++;
      selectSql.add((function.equals("COUNT_DISTINCT") ? "COUNT(DISTINCT " : function + "(") + expression + ") AS " + label);
      descriptions.add(Map.of("key", label, "source", function + "(" + field + ")", "redacted", false));
    }
    List<Object> params = new ArrayList<>(); String filterSql = where(c, array(args, "filters", 30), aliases, params);
    List<String> groupSql = new ArrayList<>();
    for (String field : group) { Ref r = ref(field, aliases); usable(r); groupSql.add(sqlRef(c, r)); }
    require(!aggregates.isEmpty() || group.isEmpty(), "groupBy requires aggregates.");
    List<String> ordering = new ArrayList<>(); Set<String> ordered = new LinkedHashSet<>();
    for (JsonNode sort : array(args, "orderBy", 20)) {
      keys(sort, "column", "direction"); String col = text(sort, "column", null, 200), direction = text(sort, "direction", "ASC", 4);
      require(Set.of("ASC", "DESC").contains(direction), "Invalid sort direction.");
      require(aggregates.isEmpty() || group.contains(col), "Aggregate ordering accepts groupBy columns only.");
      Ref r = ref(col, aliases); usable(r); ordering.add(sqlRef(c, r) + " " + direction); ordered.add(col);
    }
    boolean stable = true;
    if (aggregates.isEmpty()) {
      for (Map.Entry<String, Table> entry : aliases.entrySet()) {
        if (entry.getValue().primaryKey().isEmpty()) stable = false;
        for (String pk : entry.getValue().primaryKey()) {
          String field = entry.getKey() + "." + pk; Ref r = ref(field, aliases);
          if (r.column().sensitive() || binary(r.column())) { stable = false; continue; }
          if (ordered.add(field)) ordering.add(sqlRef(c, r) + " ASC");
        }
      }
    } else for (String field : group) if (ordered.add(field)) ordering.add(sqlRef(c, ref(field, aliases)) + " ASC");
    int offset = integer(args, "offset", 0, 0, 1000000), limit = integer(args, "limit", 50, 1, 200);
    String sql = "SELECT " + String.join(",", selectSql) + " FROM " + from + filterSql
        + (groupSql.isEmpty() ? "" : " GROUP BY " + String.join(",", groupSql))
        + (ordering.isEmpty() ? "" : " ORDER BY " + String.join(",", ordering)) + " LIMIT ? OFFSET ?";
    params.add(limit + 1); params.add(offset); List<Map<String, Object>> rows = new ArrayList<>();
    try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < descriptions.size(); i++) row.put(descriptions.get(i).get("key").toString(),
            Boolean.TRUE.equals(descriptions.get(i).get("redacted")) ? "[REDACTED]" : cell(rs, i + 1));
        rows.add(row);
      }
    }
    boolean more = rows.size() > limit; List<Map<String, Object>> page = rows.stream().limit(limit).toList();
    Map<String, Object> out = new LinkedHashMap<>(); out.put("columns", descriptions); out.put("rows", page);
    out.put("offset", offset); out.put("hasMore", more); out.put("nextOffset", more ? offset + limit : null);
    out.put("orderingStable", stable); out.put("snapshotScope", "One request only; concurrent writes can change subsequent pages.");
    return out;
  }

  private static PreparedStatement prepare(Connection c, String sql, List<Object> parameters) throws SQLException {
    PreparedStatement ps = c.prepareStatement(sql);
    try {
      ps.setQueryTimeout(8); ps.setMaxRows(201); ps.setFetchSize(100);
      for (int i = 0; i < parameters.size(); i++) ps.setObject(i + 1, parameters.get(i));
      return ps;
    } catch (SQLException ex) { ps.close(); throw ex; }
  }
  private Object cell(ResultSet rs, int index) throws Exception {
    if ("uuid".equalsIgnoreCase(rs.getMetaData().getColumnTypeName(index))) {
      Object uuid = rs.getObject(index); return uuid == null ? null : uuid.toString();
    }
    int type = rs.getMetaData().getColumnType(index);
    if (binary(type)) return rs.getObject(index) == null ? null : "[BINARY: body omitted]";
    if (Set.of(Types.CLOB, Types.NCLOB, Types.CHAR, Types.NCHAR, Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR).contains(type)) {
      try (Reader reader = rs.getCharacterStream(index)) {
        if (reader == null) return null; String value = read(reader, 1025);
        if (value.length() > 1024) return preview(value);
        return redactJsonText(value);
      }
    }
    Object value = rs.getObject(index); if (value == null) return null;
    if (value instanceof BigDecimal n) return n.toPlainString();
    if (value instanceof Long n && (n > 9007199254740991L || n < -9007199254740991L)) return n.toString();
    if (value instanceof Number || value instanceof Boolean) return value;
    String text = value.toString();
    return text.length() > 1024 ? preview(text) : redactJsonText(text);
  }
  private static Map<String, Object> preview(String value) {
    String start = value.stripLeading(); boolean json = start.startsWith("{") || start.startsWith("[");
    return Map.of("preview", json ? "[JSON: read with db_read_cell mode=json]" : value.substring(0, 1024),
        "truncated", true, "nextCharacterOffset", json ? 0 : 1024, "readWith", "db_read_cell");
  }
  private String redactJsonText(String text) throws Exception {
    String start = text.stripLeading(); if (!start.startsWith("{") && !start.startsWith("[")) return text;
    try { return mapper.writeValueAsString(redact(mapper.readTree(text))); }
    catch (com.fasterxml.jackson.core.JsonProcessingException ex) { return text; }
  }
  private JsonNode redact(JsonNode node) {
    if (node.isObject()) {
      ObjectNode copy = ((ObjectNode) node).deepCopy();
      copy.fieldNames().forEachRemaining(k -> { if (SECRET.matcher(k.replaceAll("([a-z])([A-Z])", "$1_$2")).find()) copy.put(k, "[REDACTED]"); else copy.set(k, redact(copy.get(k))); });
      return copy;
    }
    if (node.isArray()) { com.fasterxml.jackson.databind.node.ArrayNode copy = mapper.createArrayNode(); node.forEach(v -> copy.add(redact(v))); return copy; }
    return node;
  }
  private static String read(Reader reader, int limit) throws Exception {
    StringBuilder out = new StringBuilder(); char[] buffer = new char[4096]; int n;
    while (out.length() < limit && (n = reader.read(buffer, 0, Math.min(buffer.length, limit - out.length()))) != -1) out.append(buffer, 0, n);
    return out.toString();
  }

  Map<String, Object> readCell(Connection c, Scope scope, JsonNode args) throws Exception {
    keys(args, "connection", "table", "column", "key", "mode", "pointer", "offset", "limit");
    Table table = table(c, scope, text(args, "table", null, 160)); String col = text(args, "column", null, 160);
    require(table.columns().containsKey(col), "Unknown column."); Column column = table.columns().get(col);
    require(!column.sensitive() && !binary(column), "Credential/binary cell bodies are not exposed.");
    JsonNode key = args.path("key"); require(key.isObject() && key.size() >= 1 && key.size() <= 8, "Provide a row key object.");
    List<String> conditions = new ArrayList<>(); List<Object> params = new ArrayList<>();
    key.fields().forEachRemaining(entry -> {
      require(table.columns().containsKey(entry.getKey()), "Unknown row key column.");
      Column keyCol = table.columns().get(entry.getKey()); usable(new Ref("t0", keyCol));
      try { conditions.add(quote(c, entry.getKey()) + "=?"); } catch (SQLException ex) { throw new IllegalArgumentException("Cannot quote key column."); }
      params.add(parameter(keyCol, entry.getValue()));
    });
    require(table.primaryKey().stream().allMatch(key::has), "Provide all primary key columns.");
    String mode = text(args, "mode", "text", 8); require(Set.of("text", "json").contains(mode), "mode must be text or json.");
    String sql = "SELECT SUBSTRING(" + textCast(c, quote(c, col)) + ",1,8388609) FROM " + sqlTable(c, scope, table.name()) + " WHERE " + String.join(" AND ", conditions) + " LIMIT 2";
    Object value; int offset = integer(args, "offset", 0, 0, 8388608), limit = integer(args, "limit", mode.equals("json") ? 20 : 4000, 1, mode.equals("json") ? 50 : 8000);
    Map<String, Object> out = new LinkedHashMap<>();
    try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
      require(rs.next(), "Row not found.");
      try (Reader reader = rs.getCharacterStream(1)) {
        if (reader == null) value = null;
        else if (mode.equals("json")) {
          String full = read(reader, 8388609); require(full.length() <= 8388608, "JSON exceeds the 8 MiB cell limit.");
          JsonNode selected = redact(mapper.readTree(full)).at(text(args, "pointer", "", 512));
          require(!selected.isMissingNode(), "JSON Pointer not found.");
          if (selected.isArray()) {
            require(offset <= selected.size(), "offset exceeds JSON array length."); List<JsonNode> page = new ArrayList<>();
            for (int i = offset; i < Math.min(selected.size(), offset + limit); i++) page.add(selected.get(i));
            value = page; out.put("totalItems", selected.size()); out.put("hasMore", offset + page.size() < selected.size());
          } else { require(offset == 0, "offset is only supported for JSON arrays."); value = selected; }
        } else {
          require(!args.has("pointer"), "pointer requires mode=json.");
          // Inspect complete bounded text before paging, so structured credential values never leak across chunk boundaries.
          String full = read(reader, 8388609); require(full.length() <= 8388608, "Text exceeds the 8 MiB cell limit.");
          full = redactJsonText(full); require(offset <= full.length(), "offset exceeds text length.");
          value = full.substring(offset, Math.min(full.length(), offset + limit)); out.put("hasMore", offset + limit < full.length()); out.put("totalCharacters", full.length());
        }
      }
      require(!rs.next(), "Row key is not unique; provide more identifying columns.");
    }
    out.put("value", value); out.put("offset", offset); out.put("table", table.name()); out.put("column", col); return out;
  }
}
