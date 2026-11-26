package com.dev.HiddenBATHAuto.dto.productmaster;

import java.util.List;
import java.util.Map;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Condition;

/** Millimetres and counts are integers. Marks use a 1000 x 1000 relative drawing surface. */
public final class ProductDiagramDtos {
  private ProductDiagramDtos() {}
  /** Jackson normally truncates floating numbers for int fields; reject them for manufacturing dimensions. */
  public static final class StrictInteger extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
    @Override public Integer deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
      if(p.currentToken()!=com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)
        throw com.fasterxml.jackson.databind.exc.InvalidFormatException.from(p,"치수·좌표는 소수 없이 정수 mm로 입력해 주세요.",p.getText(),Integer.class);
      return p.getIntValue();
    }
  }
  public record Ref(String questionKey, String fieldKey) {}
  public record Dimension(String mode, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) Integer value, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) Integer min, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) Integer max,
      boolean minInclusive, boolean maxInclusive, Ref source) {}
  public record Mark(String kind, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int x1, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int y1, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int x2, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int y2, String text) {}
  public record Variant(String key, String fieldKey, String name, String shape,
      Dimension width, Dimension height, List<Mark> marks, List<Condition> conditions,
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) Integer sizeStep) {
    // Old configurations and saved order snapshots have no sizeStep: preserve 1mm behaviour.
    public Variant { if (sizeStep == null) sizeStep = 1; }
    public Variant(String key, String fieldKey, String name, String shape,
        Dimension width, Dimension height, List<Mark> marks, List<Condition> conditions) {
      this(key, fieldKey, name, shape, width, height, marks, conditions, 1);
    }
  }
  public record Config(String revision, String mode, Ref width, Ref height, List<Variant> variants,
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) Map<String,String> fieldLabels) {
    public Config(String revision,String mode,Ref width,Ref height,List<Variant> variants) {this(revision,mode,width,height,variants,null);}
  }
  public record Placement(String id, String variantKey, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int x, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int y, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int width, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int height) {}
  /** snapshot is always replaced by the saved server definition, never trusted from the browser. */
  public record Layout(int schemaVersion, String revision, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int width, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StrictInteger.class) int height,
      List<Placement> items, Config snapshot) {}
}
