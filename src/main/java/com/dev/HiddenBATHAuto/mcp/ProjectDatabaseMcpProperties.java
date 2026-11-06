package com.dev.HiddenBATHAuto.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Uses the original prefix so an already deployed pm-mcp environment file remains valid. */
@ConfigurationProperties(prefix = "hiddenbath.productmaster-mcp")
public class ProjectDatabaseMcpProperties {
  private Map<String, Target> connections = new LinkedHashMap<>();
  public Map<String, Target> getConnections() { return connections; }
  public void setConnections(Map<String, Target> connections) { this.connections = connections; }

  public static class Target {
    private boolean enabled;
    private String dbUrl = "", dbUsername = "", dbPassword = "", expectedDatabase = "", schema = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getDbUrl() { return dbUrl; }
    public void setDbUrl(String v) { dbUrl = v; }
    public String getDbUsername() { return dbUsername; }
    public void setDbUsername(String v) { dbUsername = v; }
    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String v) { dbPassword = v; }
    public String getExpectedDatabase() { return expectedDatabase; }
    public void setExpectedDatabase(String v) { expectedDatabase = v; }
    public String getSchema() { return schema; }
    public void setSchema(String v) { schema = v; }
  }
}
