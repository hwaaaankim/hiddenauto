package com.dev.HiddenBATHAuto.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A separate loopback listener. Does not create/replace the application's DataSource or security chain. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hiddenbath.productmaster-mcp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ProjectDatabaseMcpProperties.class)
public class ProductMasterMcpConfiguration {
  @Bean(initMethod = "start", destroyMethod = "close")
  ProductMasterMcpServer productMasterMcpServer(
      ObjectMapper mapper, ProjectDatabaseMcpProperties connections,
      @Value("${hiddenbath.productmaster-mcp.port:8095}") int port,
      @Value("${hiddenbath.productmaster-mcp.db-url:}") String url,
      @Value("${hiddenbath.productmaster-mcp.db-username:}") String username,
      @Value("${hiddenbath.productmaster-mcp.db-password:}") String password,
      @Value("${hiddenbath.productmaster-mcp.expected-database:hiddenauto_dev}") String database,
      @Value("${hiddenbath.productmaster-mcp.allowed-origins:https://chatgpt.com}") String origins)
      throws Exception {
    if (port < 1 || port > 65535) throw new IllegalArgumentException("MCP port must be 1..65535.");
    if (!url.startsWith("jdbc:mariadb:") || username.isBlank() || password.isBlank()
        || username.equalsIgnoreCase("root") || database.isBlank()) {
      throw new IllegalArgumentException("MCP requires a separate MariaDB SELECT-only account and explicit database settings.");
    }
    Class.forName("org.mariadb.jdbc.Driver");
    Set<String> allowed = Arrays.stream(origins.split(",")).map(String::trim)
        .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    if (allowed.contains("*") || allowed.contains("null"))
      throw new IllegalArgumentException("MCP origins must be explicit origins, without wildcard or null.");
    ProductMasterMcpDatabase db = new ProductMasterMcpDatabase(url, username, password, database);
    db.checkConnection();
    Map<String, ProjectDatabaseMcpReader.Scope> scopes = new LinkedHashMap<>();
    scopes.put("dev", new ProjectDatabaseMcpReader.Scope("dev", db, ""));
    for (Map.Entry<String, ProjectDatabaseMcpProperties.Target> entry : connections.getConnections().entrySet()) {
      ProjectDatabaseMcpProperties.Target target = entry.getValue();
      if (!target.isEnabled()) continue;
      String alias = entry.getKey();
      if (!alias.matches("[a-z][a-z0-9_-]{0,31}") || scopes.containsKey(alias))
        throw new IllegalArgumentException("MCP connection alias is invalid or duplicated.");
      String extraUrl = target.getDbUrl(), extraUser = target.getDbUsername();
      if (extraUser.isBlank() || Set.of("root", "postgres").contains(extraUser.toLowerCase(java.util.Locale.ROOT))
          || target.getDbPassword().isBlank() || target.getExpectedDatabase().isBlank())
        throw new IllegalArgumentException("Each enabled MCP connection requires its own SELECT-only account and explicit database.");
      if (extraUrl.startsWith("jdbc:postgresql:")) {
        if (!target.getSchema().matches("[A-Za-z_][A-Za-z0-9_]{0,62}"))
          throw new IllegalArgumentException("PostgreSQL MCP connections require an explicit schema.");
        Class.forName("org.postgresql.Driver");
      } else {
        if (!extraUrl.startsWith("jdbc:mariadb:"))
          throw new IllegalArgumentException("MCP supports MariaDB and PostgreSQL connections.");
        if (!target.getSchema().isBlank()) throw new IllegalArgumentException("For MariaDB, select the database in db-url/expected-database and leave schema empty.");
      }
      ProductMasterMcpDatabase extra = new ProductMasterMcpDatabase(extraUrl, extraUser,
          target.getDbPassword(), target.getExpectedDatabase());
      extra.checkConnection();
      scopes.put(alias, new ProjectDatabaseMcpReader.Scope(alias, extra, target.getSchema()));
    }
    return new ProductMasterMcpServer(port, mapper,
        new ProjectDatabaseMcpTools(scopes, new ProductMasterMcpTools(db, mapper), mapper), allowed);
  }
}
