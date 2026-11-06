package com.dev.HiddenBATHAuto.mcp;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only, catalog-checked connection wrapper and legacy ProductMaster SELECT helpers.
 * Generic project tools use live metadata and the full configured project database/schema. */
final class ProductMasterMcpDatabase {
  static final List<String> TABLES = List.of("tb_pm_product", "tb_pm_product_component",
      "tb_pm_studio_input", "tb_pm_attribute_group", "tb_pm_attribute_value");
  static final String PRODUCT_COLUMNS = "id, product_name, catalog_code, non_standard, status, "
      + "row_version, updated_at, production_hours, unit_price";
  private final String url, username, password, expectedDatabase;

  ProductMasterMcpDatabase(String url, String username, String password, String expectedDatabase) {
    this.url = url; this.username = username; this.password = password;
    this.expectedDatabase = expectedDatabase;
  }

  interface Read<T> { T run(Connection connection) throws Exception; }

  <T> T read(Read<T> operation) throws Exception {
    try (Connection c = DriverManager.getConnection(url, username, password)) {
      c.setReadOnly(true);
      c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      c.setAutoCommit(false);
      try {
        String actual = c.getCatalog();
        if (actual == null || !actual.equals(expectedDatabase))
          throw new SQLException("MCP database does not match expected-database.");
        return operation.run(c);
      } finally {
        c.rollback();
      }
    }
  }

  void checkConnection() throws Exception {
    read(c -> { query(c, "SELECT 1 AS connection_ok"); return null; });
  }

  static List<Map<String, Object>> query(Connection c, String sql, Object... parameters) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setQueryTimeout(8);
      ps.setMaxRows(2001);
      for (int i = 0; i < parameters.length; i++) ps.setObject(i + 1, parameters[i]);
      try (ResultSet rs = ps.executeQuery()) {
        ResultSetMetaData md = rs.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
          if (rows.size() == 2000) throw new SQLException("MCP result exceeds 2000 rows; narrow the query.");
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 1; i <= md.getColumnCount(); i++) {
            Object v = rs.getObject(i);
            if (v instanceof java.sql.Clob clob) {
              if (clob.length() > 8388608) throw new SQLException("Saved JSON exceeds the 8 MiB read limit.");
              v = clob.getSubString(1, (int) clob.length());
            }
            if (v instanceof String text && text.length() > 8388608)
              throw new SQLException("Saved text exceeds the 8 MiB read limit.");
            // Preserve decimals and timestamps exactly through the JavaScript number boundary.
            if (v instanceof BigDecimal n) v = n.toPlainString();
            else if (v instanceof java.sql.Timestamp || v instanceof java.sql.Date
                || v instanceof java.time.temporal.TemporalAccessor) v = v.toString();
            row.put(md.getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), v);
          }
          rows.add(row);
        }
        return rows;
      }
    }
  }

  static Map<String, Object> product(Connection c, long id) throws SQLException {
    List<Map<String, Object>> rows = query(c, "SELECT " + PRODUCT_COLUMNS
        + ", studio_definition_json, studio_process_json, studio_price_json FROM tb_pm_product WHERE id = ?", id);
    if (rows.isEmpty()) throw new IllegalArgumentException("Product not found: " + id);
    return rows.get(0);
  }

  static List<Map<String, Object>> components(Connection c, long id) throws SQLException {
    return query(c, "SELECT c.id, c.group_id, c.value_id, c.sort_order, g.group_code, "
        + "g.customer_label, g.non_standard, g.price_impact, g.system_role, v.value_code "
        + "FROM tb_pm_product_component c JOIN tb_pm_attribute_group g ON g.id=c.group_id "
        + "LEFT JOIN tb_pm_attribute_value v ON v.id=c.value_id "
        + "WHERE c.product_id=? ORDER BY c.sort_order, c.id", id);
  }

  static List<Map<String, Object>> inputs(Connection c, long id) throws SQLException {
    return query(c, "SELECT group_id, field_key, number_value, text_value "
        + "FROM tb_pm_studio_input WHERE product_id=? ORDER BY group_id, field_key", id);
  }

  static List<Map<String, Object>> schema(Connection c, String table) throws SQLException {
    if (!TABLES.contains(table)) throw new IllegalArgumentException("Table is outside the ProductMaster allowlist.");
    return query(c, "SELECT column_name, column_type, is_nullable, column_key "
        + "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? "
        + "ORDER BY ordinal_position", table);
  }
}
