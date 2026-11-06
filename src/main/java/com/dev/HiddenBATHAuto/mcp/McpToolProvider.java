package com.dev.HiddenBATHAuto.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

interface McpToolProvider {
  boolean hasTool(String name);
  List<Map<String, Object>> definitions();
  Map<String, Object> call(String name, JsonNode arguments);
}
