package com.dev.HiddenBATHAuto.service.productmaster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductStudioJson {
  private final ObjectMapper mapper;

  public String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("제품 설정을 저장할 수 없습니다.", e);
    }
  }

  public <T> T read(String value, Class<T> type) {
    try {
      return mapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("저장된 제품 설정을 읽을 수 없습니다.", e);
    }
  }

  public <T> T read(String value, com.fasterxml.jackson.core.type.TypeReference<T> type) {
    try {
      return mapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("저장된 설정을 읽을 수 없습니다.", e);
    }
  }

  public <T> List<T> list(String value, Class<T> type) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return mapper.readValue(
          value, mapper.getTypeFactory().constructCollectionType(List.class, type));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("저장된 제품 설정 목록을 읽을 수 없습니다.", e);
    }
  }
}
