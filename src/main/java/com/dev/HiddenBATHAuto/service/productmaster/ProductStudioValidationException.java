package com.dev.HiddenBATHAuto.service.productmaster;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable request paths let the editor display errors next to the relevant input. */
public class ProductStudioValidationException extends IllegalArgumentException {
  private final Map<String, String> fieldErrors;

  public ProductStudioValidationException(Map<String, String> errors) {
    super(errors.values().stream().findFirst().orElse("입력값을 확인해 주세요."));
    this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
  }

  public Map<String, String> getFieldErrors() {
    return fieldErrors;
  }
}
