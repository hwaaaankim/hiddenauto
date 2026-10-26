package com.dev.HiddenBATHAuto.service.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import java.math.BigDecimal;
import java.util.*;

/** Preserve high precision coefficients and thresholds across JSON's JavaScript number boundary. */
public final class ProductPricingWire {
  private ProductPricingWire() {}

  public static Object response(Object value) {
    if (value == null) return null;
    if (value instanceof Axis axis) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("fieldKey", axis.fieldKey());
      result.put("operator", axis.operator());
      result.put("operators", axis.operators());
      result.put("outsidePolicy", axis.outsidePolicy());
      result.put("headers", axis.headers() == null ? null
          : axis.headers().stream().map(n -> n == null ? null : n.toPlainString()).toList());
      return result;
    }
    if (value instanceof OptionRangePricing pricing) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("groupKey", pricing.groupKey());
      result.put("axis", response(pricing.axis()));
      result.put("amounts", pricing.amounts() == null ? null
          : pricing.amounts().stream().map(n -> n == null ? null : n.toPlainString()).toList());
      result.put("aboveAmount", pricing.aboveAmount() == null ? null : pricing.aboveAmount().toPlainString());
      return result;
    }
    if (value instanceof Map<?, ?> source) {
      Map<String, Object> result = new LinkedHashMap<>();
      source.forEach((key, item) -> result.put(key.toString(), response(item)));
      return result;
    }
    if (value instanceof Collection<?> items) return items.stream().map(ProductPricingWire::response).toList();
    if (value.getClass().isRecord()) {
      Map<String, Object> result = new LinkedHashMap<>();
      try {
        for (java.lang.reflect.RecordComponent component : value.getClass().getRecordComponents()) {
          Object item = component.getAccessor().invoke(value);
          if (value instanceof Term term && "rates".equals(component.getName()) && term.rates() != null) {
            Map<String, String> rates = new LinkedHashMap<>();
            term.rates().forEach((key, rate) -> rates.put(key, rate == null ? null : rate.toPlainString()));
            result.put("rates", rates);
          } else if (value instanceof Term term && term.role() == TermRole.FACTOR
              && ("amount".equals(component.getName()) || "values".equals(component.getName()))) {
            result.put(component.getName(), decimals(item));
          } else if (value instanceof TermResult term
              && ("baseAmount".equals(component.getName())
                  || term.role() == TermRole.FACTOR && "amount".equals(component.getName()))) {
            result.put(component.getName(), decimals(item));
          } else if ((value instanceof MultiplierResult || value instanceof FactorResult)
              && "value".equals(component.getName())) {
            result.put(component.getName(), decimals(item));
          } else result.put(component.getName(), response(item));
        }
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("가격계산 응답을 생성할 수 없습니다.", ex);
      }
      return result;
    }
    return value;
  }

  /** Convert coefficient leaves without converting established monetary total fields. */
  private static Object decimals(Object value) {
    if (value instanceof BigDecimal number) return number.toPlainString();
    if (value instanceof Collection<?> items) return items.stream().map(ProductPricingWire::decimals).toList();
    return value;
  }
}
