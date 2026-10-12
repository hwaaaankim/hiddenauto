package com.dev.HiddenBATHAuto.service.productmaster;

import com.dev.HiddenBATHAuto.config.productmaster.ProductDrawingProperties;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductDrawingAiDtos.DraftResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Converts bounded interpreted geometry into editable cells, preserving usable partial results. */
@Component
public class ProductDrawingDraftRenderer {
  private static final Logger log = LoggerFactory.getLogger(ProductDrawingDraftRenderer.class);
  private static final Set<String> TYPES = Set.of("rect", "ellipse", "text", "line", "polyline");
  private static final Set<String> ARROWS = Set.of("none", "block", "open");
  private static final int MAX_INPUT_PAGES = 40;
  private static final int MAX_INPUT_NODES = 20_000;
  private static final int MAX_INPUT_DEPTH = 64;
  private static final int MAX_ISSUES = 64;
  private static final int MAX_WARNINGS = 12;
  private static final double MAX_GEOMETRY = 100_000;
  private static final double CLIP_TOLERANCE = 1;
  private static final String REVIEW_WARNING = "AI 초안입니다. 실제 치수·비율·문 방향·누락된 부품을 확인한 뒤 도면 등록을 눌러 저장해 주세요.";
  private final ProductDrawingProperties properties;

  public ProductDrawingDraftRenderer(ProductDrawingProperties properties) { this.properties = properties; }

  /** Existing callers keep the same API and JSON response contract. */
  public DraftResult render(JsonNode draft) { return renderDetailed(draft, "-").result(); }

  public record RenderIssue(String path, int pageIndex, int elementIndex,
      String code, String action, String reason) {}
  public record RenderStats(int inputPages, int outputPages, int inputShapes, int renderedShapes,
      int skippedShapes, int normalizedFields, int fittedPages, int clippedCoordinates, int omittedIssues) {}
  public record RenderOutcome(DraftResult result, RenderStats stats, List<RenderIssue> issues) {}

  public RenderOutcome renderDetailed(JsonNode draft, String traceId) {
    Context context = new Context(traceId);
    if (draft == null || !draft.isObject()) {
      context.issue("$", -1, -1, "OBJECT_REQUIRED", "REJECT", "Draft must be a JSON object.");
      throw failure(context);
    }
    validateInputTree(draft, context);
    JsonNode pages = draft.path("pages");
    if (!pages.isArray() || pages.isEmpty() || pages.size() > MAX_INPUT_PAGES) {
      context.issue("$.pages", -1, -1, "PAGE_STRUCTURE", "REJECT", "Pages must be an array with 1..40 items.");
      throw failure(context);
    }
    context.inputPages = pages.size();
    for (JsonNode page : pages) {
      if (page.isObject() && page.path("elements").isArray()) context.inputShapes += page.path("elements").size();
    }
    log.info("drawing_render_start traceId={} inputPages={} inputShapes={} maxPages={} maxShapesPerPage={}",
        context.traceId, context.inputPages, context.inputShapes,
        properties.getMaxDraftPages(), properties.getMaxDraftShapesPerPage());
    String summary = optionalText(draft.path("summary"), 1200, "$.summary", -1, -1, context);
    List<String> modelWarnings = modelWarnings(draft.path("warnings"), context);
    List<Page> renderedPages = new ArrayList<>();
    for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
      JsonNode page = pages.get(pageIndex);
      String pagePath = "$.pages[" + pageIndex + "]";
      if (pageIndex >= properties.getMaxDraftPages()) {
        if (page.isObject() && page.path("elements").isArray()) context.skippedShapes += page.path("elements").size();
        context.issue(pagePath, pageIndex, -1, "PAGE_OUTPUT_LIMIT", "TRUNCATE", "Page exceeds configured output page limit.");
        continue;
      }
      if (!page.isObject()) {
        context.issue(pagePath, pageIndex, -1, "PAGE_OBJECT_REQUIRED", "SKIP", "Page must be a JSON object.");
        continue;
      }
      JsonNode elements = page.path("elements");
      if (!elements.isArray() || elements.isEmpty()) {
        context.issue(pagePath + ".elements", pageIndex, -1, "ELEMENT_STRUCTURE", "SKIP", "Elements must be a non-empty array.");
        continue;
      }
      String name = optionalText(page.path("name"), 80, pagePath + ".name", pageIndex, -1, context).strip();
      if (name.isEmpty()) name = "AI 초안 " + (pageIndex + 1);
      double width;
      double height;
      try {
        width = pageDimension(page.path("width"), 1000, 320, pagePath + ".width", pageIndex, context);
        height = pageDimension(page.path("height"), 700, 240, pagePath + ".height", pageIndex, context);
      } catch (Rejected rejected) {
        context.skippedShapes += elements.size();
        context.issue(rejected.path, pageIndex, -1, rejected.code, "SKIP", rejected.reason);
        continue;
      }
      List<Shape> shapes = new ArrayList<>();
      for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
        String path = pagePath + ".elements[" + elementIndex + "]";
        if (elementIndex >= properties.getMaxDraftShapesPerPage()) {
          context.skippedShapes += elements.size() - elementIndex;
          context.issue(path, pageIndex, elementIndex, "SHAPE_OUTPUT_LIMIT", "TRUNCATE", "Remaining shapes exceed configured output shape limit.");
          break;
        }
        try {
          shapes.add(parseShape(elements.get(elementIndex), pageIndex, elementIndex, path, context));
        } catch (Rejected rejected) {
          context.skippedShapes++;
          context.issue(rejected.path, pageIndex, elementIndex, rejected.code, "SKIP", rejected.reason);
        }
      }
      if (shapes.isEmpty()) {
        context.issue(pagePath + ".elements", pageIndex, -1, "NO_VALID_PAGE_SHAPES", "SKIP", "Page contains no usable interpreted shapes.");
        continue;
      }
      shapes = fitPage(shapes, width, height, pageIndex, context);
      if (shapes.isEmpty()) {
        context.issue(pagePath + ".elements", pageIndex, -1, "NO_VISIBLE_PAGE_SHAPES", "SKIP", "Page has no visible geometry after boundary clipping.");
        continue;
      }
      renderedPages.add(new Page(pageIndex, name, width, height, shapes));
      context.renderedShapes += shapes.size();
    }
    context.outputPages = renderedPages.size();
    if (context.renderedShapes == 0) throw failure(context);
    String xml = renderXml(renderedPages);
    ProductDrawingXmlValidator.validate(xml);
    if (summary.isBlank()) summary = "이미지에서 해석한 도형 " + context.renderedShapes + "개로 편집 가능한 초안을 만들었습니다.";
    DraftResult result = new DraftResult(xml, warnings(context, modelWarnings), summary);
    RenderStats stats = context.stats();
    log.info("drawing_render_complete traceId={} inputPages={} outputPages={} inputShapes={} renderedShapes={} skippedShapes={} normalizedFields={} fittedPages={} clippedCoordinates={} omittedIssues={} xmlChars={}",
        context.traceId, stats.inputPages(), stats.outputPages(), stats.inputShapes(), stats.renderedShapes(),
        stats.skippedShapes(), stats.normalizedFields(), stats.fittedPages(), stats.clippedCoordinates(), stats.omittedIssues(), xml.length());
    return new RenderOutcome(result, stats, List.copyOf(context.issues));
  }

  private Shape parseShape(JsonNode element, int pageIndex, int elementIndex, String path, Context context) {
    if (!element.isObject()) throw reject(path, "SHAPE_OBJECT_REQUIRED", "Shape must be a JSON object.");
    JsonNode typeNode = element.path("type");
    if (!typeNode.isTextual() || typeNode.asText().length() > 16)
      throw reject(path + ".type", "SHAPE_TYPE", "Shape type must be rect, ellipse, text, line, or polyline.");
    String type = typeNode.asText().strip().toLowerCase(Locale.ROOT);
    if (!TYPES.contains(type)) throw reject(path + ".type", "SHAPE_TYPE", "Shape type must be rect, ellipse, text, line, or polyline.");
    boolean edge = type.equals("line") || type.equals("polyline");
    List<Point> points = new ArrayList<>();
    double x = 0, y = 0, width = 0, height = 0;
    if (edge) {
      JsonNode pointNodes = element.path("points");
      if (!pointNodes.isArray() || pointNodes.size() < 2 || pointNodes.size() > 24
          || (type.equals("line") && pointNodes.size() != 2))
        throw reject(path + ".points", "POINT_COUNT", "Line needs 2 points; polyline needs 2..24 points.");
      for (int i = 0; i < pointNodes.size(); i++) {
        JsonNode point = pointNodes.get(i);
        String pointPath = path + ".points[" + i + "]";
        if (!point.isObject()) throw reject(pointPath, "POINT_OBJECT_REQUIRED", "Point must be a JSON object.");
        points.add(new Point(geometry(point.path("x"), pointPath + ".x", pageIndex, elementIndex, context),
            geometry(point.path("y"), pointPath + ".y", pageIndex, elementIndex, context)));
      }
      Point first = points.get(0);
      if (points.stream().allMatch(point -> point.x == first.x && point.y == first.y))
        throw reject(path + ".points", "DEGENERATE_EDGE", "Edge needs at least two distinct finite points.");
    } else {
      x = geometry(element.path("x"), path + ".x", pageIndex, elementIndex, context);
      y = geometry(element.path("y"), path + ".y", pageIndex, elementIndex, context);
      width = geometry(element.path("width"), path + ".width", pageIndex, elementIndex, context);
      height = geometry(element.path("height"), path + ".height", pageIndex, elementIndex, context);
      if (width <= 0) throw reject(path + ".width", "POSITIVE_SIZE", "Vertex width must be finite and greater than zero.");
      if (height <= 0) throw reject(path + ".height", "POSITIVE_SIZE", "Vertex height must be finite and greater than zero.");
      if (width < 1) { width = 1; context.normalize(path + ".width", pageIndex, elementIndex, "MIN_SIZE", "Positive vertex width normalized to 1 pixel."); }
      if (height < 1) { height = 1; context.normalize(path + ".height", pageIndex, elementIndex, "MIN_SIZE", "Positive vertex height normalized to 1 pixel."); }
      if (element.path("points").isArray() && !element.path("points").isEmpty())
        context.normalize(path + ".points", pageIndex, elementIndex, "UNUSED_POINTS", "Vertex point list is unused and ignored.");
    }
    String label = optionalText(element.path("label"), 500, path + ".label", pageIndex, elementIndex, context);
    if (type.equals("text") && label.isBlank()) throw reject(path + ".label", "EMPTY_TEXT", "Text shape needs a non-empty plain-text label.");
    String stroke = color(element.path("strokeColor"), "#202020", false, path + ".strokeColor", pageIndex, elementIndex, context);
    String fill = color(element.path("fillColor"), "none", false, path + ".fillColor", pageIndex, elementIndex, context);
    String font = color(element.path("fontColor"), "#000000", true, path + ".fontColor", pageIndex, elementIndex, context);
    double strokeWidth = styleNumber(element.path("strokeWidth"), 1, 0.5, 6, path + ".strokeWidth", pageIndex, elementIndex, context);
    double fontSize = styleNumber(element.path("fontSize"), 18, 8, 48, path + ".fontSize", pageIndex, elementIndex, context);
    boolean dashed = element.path("dashed").isBoolean() && element.path("dashed").asBoolean();
    if (!element.path("dashed").isBoolean()) context.normalize(path + ".dashed", pageIndex, elementIndex, "DEFAULT_BOOLEAN", "Dashed style defaulted to false.");
    String startArrow = arrow(element.path("startArrow"), path + ".startArrow", pageIndex, elementIndex, context);
    String endArrow = arrow(element.path("endArrow"), path + ".endArrow", pageIndex, elementIndex, context);
    double rotation = rotation(element.path("rotation"), path + ".rotation", pageIndex, elementIndex, context);
    if (edge && rotation != 0) {
      rotation = 0;
      context.normalize(path + ".rotation", pageIndex, elementIndex, "UNUSED_EDGE_ROTATION", "Edge uses point geometry; rotation is ignored.");
    }
    if (label.isBlank() && stroke.equals("none") && (edge || fill.equals("none")))
      throw reject(path + ".strokeColor", "INVISIBLE_SHAPE", "Shape has neither visible geometry style nor label.");
    return new Shape(elementIndex, type, label, x, y, width, height, List.copyOf(points),
        stroke, fill, font, strokeWidth, fontSize, dashed, startArrow, endArrow, rotation);
  }

  private List<Shape> fitPage(List<Shape> shapes, double width, double height, int pageIndex, Context context) {
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    for (Shape shape : shapes) {
      if (shape.edge()) {
        for (Point point : shape.points) { minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); }
      } else {
        double radians = Math.toRadians(shape.rotation);
        double halfWidth = Math.abs(Math.cos(radians)) * shape.width / 2 + Math.abs(Math.sin(radians)) * shape.height / 2;
        double halfHeight = Math.abs(Math.sin(radians)) * shape.width / 2 + Math.abs(Math.cos(radians)) * shape.height / 2;
        double centerX = shape.x + shape.width / 2, centerY = shape.y + shape.height / 2;
        minX = Math.min(minX, centerX - halfWidth); minY = Math.min(minY, centerY - halfHeight);
        maxX = Math.max(maxX, centerX + halfWidth); maxY = Math.max(maxY, centerY + halfHeight);
      }
    }
    boolean fit = minX < -CLIP_TOLERANCE || minY < -CLIP_TOLERANCE
        || maxX > width + CLIP_TOLERANCE || maxY > height + CLIP_TOLERANCE;
    double scale = 1, translateX = 0, translateY = 0;
    if (fit) {
      double spanX = maxX - minX, spanY = maxY - minY;
      scale = Math.min(1, Math.min((width - 40) / Math.max(1, spanX), (height - 40) / Math.max(1, spanY)));
      translateX = (width - spanX * scale) / 2 - minX * scale;
      translateY = (height - spanY * scale) / 2 - minY * scale;
      context.fittedPages++;
      context.normalize("$.pages[" + pageIndex + "].elements", pageIndex, -1, "PAGE_FIT",
          "Geometry uniformly fitted to finite canvas width=" + decimal(width) + ", height=" + decimal(height) + ".");
    }
    List<Shape> result = new ArrayList<>();
    for (Shape shape : shapes) {
      int before = context.clippedCoordinates;
      double x = shape.x, y = shape.y, shapeWidth = shape.width, shapeHeight = shape.height;
      List<Point> points = shape.points;
      if (shape.edge()) {
        points = new ArrayList<>();
        for (Point point : shape.points) points.add(new Point(clip(point.x * scale + translateX, width, context), clip(point.y * scale + translateY, height, context)));
      } else if (shape.rotation != 0) {
        // The rotated bounds are fitted above. Clipping the unrotated box would distort vertical labels.
        x = shape.x * scale + translateX; y = shape.y * scale + translateY;
        shapeWidth = shape.width * scale; shapeHeight = shape.height * scale;
      } else {
        double left = clip(shape.x * scale + translateX, width, context);
        double top = clip(shape.y * scale + translateY, height, context);
        double right = clip((shape.x + shape.width) * scale + translateX, width, context);
        double bottom = clip((shape.y + shape.height) * scale + translateY, height, context);
        x = left; y = top; shapeWidth = right - left; shapeHeight = bottom - top;
      }
      if (context.clippedCoordinates > before)
        context.normalize("$.pages[" + pageIndex + "].elements[" + shape.index + "].geometry", pageIndex, shape.index,
            "BOUNDARY_CLIP", "Coordinates clipped to finite page bounds within 1 pixel tolerance.");
      // Validity must use the exact precision that is written to XML, not the pre-rounded doubles.
      if (shape.edge()) {
        points = points.stream().map(point -> new Point(quantize(point.x), quantize(point.y))).toList();
      } else {
        x = quantize(x); y = quantize(y); shapeWidth = quantize(shapeWidth); shapeHeight = quantize(shapeHeight);
      }
      if ((!shape.edge() && (shapeWidth <= 0 || shapeHeight <= 0))
          || (shape.edge() && allSame(points))) {
        context.skippedShapes++;
        boolean clipped = context.clippedCoordinates > before;
        context.issue("$.pages[" + pageIndex + "].elements[" + shape.index + "].geometry", pageIndex, shape.index,
            clipped ? "CLIPPED_EMPTY_GEOMETRY" : "OUTPUT_PRECISION_EMPTY_GEOMETRY", "SKIP",
            clipped ? "Page clipping and final output precision leave no visible geometry."
                : "Final 4-decimal output precision leaves no visible geometry.");
        continue;
      }
      result.add(new Shape(shape.index, shape.type, shape.label, x, y, shapeWidth, shapeHeight, List.copyOf(points),
          shape.stroke, shape.fill, shape.font, shape.strokeWidth, shape.fontSize, shape.dashed, shape.startArrow, shape.endArrow, shape.rotation));
    }
    return result;
  }

  private static boolean allSame(List<Point> points) {
    Point first = points.get(0);
    return points.stream().allMatch(point -> point.x == first.x && point.y == first.y);
  }

  private static double clip(double coordinate, double maximum, Context context) {
    double clipped = Math.max(0, Math.min(maximum, coordinate));
    if (Math.abs(clipped - coordinate) > 0.0000001) context.clippedCoordinates++;
    return clipped;
  }

  private double geometry(JsonNode value, String path, int page, int element, Context context) {
    double number = numeric(value, path, page, element, context);
    if (!Double.isFinite(number)) throw reject(path, "NON_FINITE_GEOMETRY", "Geometry must contain finite numeric coordinates.");
    if (Math.abs(number) > MAX_GEOMETRY) throw reject(path, "GEOMETRY_BOUND", "Absolute geometry coordinate or size must not exceed 100000.");
    return number;
  }

  private double pageDimension(JsonNode value, double fallback, double minimum, String path, int page, Context context) {
    if (value.isMissingNode() || value.isNull()
        || (value.isNumber() && Double.isFinite(value.asDouble()) && value.asDouble() <= 0)) {
      context.normalize(path, page, -1, "DEFAULT_PAGE_SIZE", "Missing or non-positive page dimension uses a bounded default.");
      return fallback;
    }
    double number = geometry(value, path, page, -1, context);
    double normalized = Math.max(minimum, Math.min(2400, number));
    if (normalized != number) context.normalize(path, page, -1, "PAGE_SIZE_BOUND", "Page dimension bounded to " + decimal(minimum) + "..2400.");
    return normalized;
  }

  private double numeric(JsonNode value, String path, int page, int element, Context context) {
    if (value.isNumber()) return value.asDouble();
    if (value.isTextual() && value.asText().length() <= 64) {
      try {
        double number = Double.parseDouble(value.asText().strip());
        context.normalize(path, page, element, "NUMERIC_STRING", "Numeric string parsed; finite numeric bounds are checked separately.");
        return number;
      } catch (NumberFormatException ignored) { /* Never place the untrusted value in an issue. */ }
    }
    throw reject(path, "NUMBER_REQUIRED", "Geometry field requires a finite numeric value.");
  }

  private double styleNumber(JsonNode value, double fallback, double min, double max,
      String path, int page, int element, Context context) {
    double number = value.isNumber() ? value.asDouble() : Double.NaN;
    if (!Double.isFinite(number) || number <= 0) {
      context.normalize(path, page, element, "DEFAULT_STYLE_NUMBER", "Missing, zero, or invalid style number uses a bounded default.");
      return fallback;
    }
    double normalized = Math.max(min, Math.min(max, number));
    if (normalized != number) context.normalize(path, page, element, "STYLE_NUMBER_BOUND", "Style number bounded to " + decimal(min) + ".." + decimal(max) + ".");
    return normalized;
  }

  private String color(JsonNode value, String fallback, boolean font, String path, int page, int element, Context context) {
    if (value.isMissingNode() || value.isNull() || (value.isTextual() && value.asText().isBlank())) {
      context.normalize(path, page, element, "DEFAULT_COLOR", "Missing color uses a fixed safe default."); return fallback;
    }
    if (!value.isTextual() || value.asText().length() > 16)
      throw reject(path, "COLOR_FORMAT", "Color must be none, transparent, #RGB, or #RRGGBB.");
    String color = value.asText().strip().toLowerCase(Locale.ROOT);
    if (color.equals("none") || color.equals("transparent")) {
      if (font) { context.normalize(path, page, element, "VISIBLE_FONT_COLOR", "Invisible font color uses fixed visible black."); return "#000000"; }
      if (color.equals("transparent")) context.normalize(path, page, element, "TRANSPARENT_COLOR", "Transparent color normalized to native none.");
      return "none";
    }
    if (color.matches("#[a-f0-9]{3}")) {
      color = "#" + color.charAt(1) + color.charAt(1) + color.charAt(2) + color.charAt(2) + color.charAt(3) + color.charAt(3);
      context.normalize(path, page, element, "SHORT_HEX_COLOR", "Three-digit hex color expanded to six-digit hex.");
    }
    if (!color.matches("#[a-f0-9]{6}")) throw reject(path, "COLOR_FORMAT", "Color must be none, transparent, #RGB, or #RRGGBB.");
    return color.toUpperCase(Locale.ROOT);
  }

  private String arrow(JsonNode value, String path, int page, int element, Context context) {
    if (value.isTextual()) {
      String arrow = value.asText().strip().toLowerCase(Locale.ROOT);
      if (ARROWS.contains(arrow)) return arrow;
    }
    context.normalize(path, page, element, "DEFAULT_ARROW", "Unsupported or missing arrow defaults to none."); return "none";
  }

  private double rotation(JsonNode value, String path, int page, int element, Context context) {
    if (value.isMissingNode() || value.isNull()) return 0;
    if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
      context.normalize(path, page, element, "DEFAULT_ROTATION", "Invalid rotation uses zero degrees."); return 0;
    }
    double input = value.asDouble();
    double normalized = input % 360;
    if (normalized > 180) normalized -= 360;
    if (normalized < -180) normalized += 360;
    if (normalized != input) context.normalize(path, page, element, "ROTATION_BOUND", "Rotation normalized to equivalent angle in -180..180 degrees.");
    return normalized == 0 ? 0 : normalized;
  }

  private String optionalText(JsonNode value, int limit, String path, int page, int element, Context context) {
    if (!value.isTextual()) {
      context.normalize(path, page, element, "DEFAULT_TEXT", "Missing or non-text field uses empty plain text."); return "";
    }
    String text = value.asText();
    if (text.length() > limit) {
      context.normalize(path, page, element, "TEXT_LIMIT", "Plain text truncated to configured character limit=" + limit + ".");
      int end = limit;
      if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
      text = text.substring(0, end);
    }
    return text;
  }

  private List<String> modelWarnings(JsonNode warnings, Context context) {
    List<String> result = new ArrayList<>();
    if (!warnings.isArray()) {
      context.normalize("$.warnings", -1, -1, "DEFAULT_WARNINGS", "Missing warning array uses an empty warning list."); return result;
    }
    for (int i = 0; i < Math.min(MAX_WARNINGS, warnings.size()); i++) {
      String warning = optionalText(warnings.get(i), 300, "$.warnings[" + i + "]", -1, -1, context).strip();
      if (!warning.isEmpty() && !result.contains(warning)) result.add(warning);
    }
    if (warnings.size() > MAX_WARNINGS) context.normalize("$.warnings", -1, -1, "WARNING_LIMIT", "Model warnings truncated to 12 entries.");
    return result;
  }

  private List<String> warnings(Context context, List<String> modelWarnings) {
    Set<String> result = new LinkedHashSet<>(); result.add(REVIEW_WARNING);
    if (context.skippedShapes > 0 || context.outputPages < context.inputPages)
      result.add("AI 해석 중 사용할 수 없는 도형·초과 항목을 제외하고 " + context.renderedShapes + "개의 도형을 초안으로 만들었습니다. 제외된 부분은 직접 보완해 주세요.");
    if (context.normalizedFields > 0)
      result.add("일부 색상·스타일·캔버스 값을 안전한 기본값으로 보정했습니다. 보정된 부분을 확인해 주세요.");
    if (context.fittedPages > 0) result.add("캔버스를 벗어난 도형은 페이지 전체를 같은 비율로 줄이거나 이동했습니다. 실제 치수는 자동 검증되지 않습니다.");
    int details = 0;
    for (RenderIssue issue : context.issues) {
      if (!issue.action.equals("SKIP") || issue.elementIndex < 0 || details >= 3) continue;
      result.add("페이지 " + (issue.pageIndex + 1) + " 도형 " + (issue.elementIndex + 1) + " 제외: " + issue.path + " [" + issue.code + "]"); details++;
    }
    for (String warning : modelWarnings) { if (result.size() >= MAX_WARNINGS) break; result.add(warning); }
    return List.copyOf(result);
  }

  private void validateInputTree(JsonNode draft, Context context) {
    record Entry(JsonNode node, int depth) {}
    ArrayDeque<Entry> pending = new ArrayDeque<>(); pending.add(new Entry(draft, 0));
    int nodes = 0;
    while (!pending.isEmpty()) {
      Entry entry = pending.removeFirst();
      if (++nodes > MAX_INPUT_NODES || entry.depth > MAX_INPUT_DEPTH) {
        context.issue("$", -1, -1, "INPUT_STRUCTURE_LIMIT", "REJECT", "JSON input must contain <=20000 nodes and depth <=64."); throw failure(context);
      }
      if (entry.node.isContainerNode()) {
        for (JsonNode child : entry.node) {
          if (nodes + pending.size() >= MAX_INPUT_NODES) {
            context.issue("$", -1, -1, "INPUT_STRUCTURE_LIMIT", "REJECT", "JSON input must contain <=20000 nodes and depth <=64."); throw failure(context);
          }
          pending.addLast(new Entry(child, entry.depth + 1));
        }
      }
    }
  }

  private String renderXml(List<Page> pages) {
    StringBuilder xml = new StringBuilder("<mxfile host=\"app.diagrams.net\" agent=\"HiddenBATHAuto\" version=\"24.7.17\" compressed=\"false\">");
    for (Page page : pages) {
      int pageId = page.index + 1;
      xml.append("<diagram id=\"ai-page-").append(pageId).append("\" name=\"").append(escape(page.name)).append("\">")
          .append("<mxGraphModel dx=\"1000\" dy=\"700\" grid=\"1\" gridSize=\"10\" guides=\"1\" tooltips=\"1\" connect=\"1\" arrows=\"1\" fold=\"1\" page=\"1\" pageScale=\"1\" pageWidth=\"")
          .append(decimal(page.width)).append("\" pageHeight=\"").append(decimal(page.height))
          .append("\" math=\"0\" shadow=\"0\" background=\"#FFFFFF\"><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>");
      for (Shape shape : page.shapes) renderShape(xml, shape, "ai-" + pageId + "-" + (shape.index + 1));
      xml.append("</root></mxGraphModel></diagram>");
    }
    return xml.append("</mxfile>").toString();
  }

  private void renderShape(StringBuilder xml, Shape shape, String id) {
    StringBuilder style = new StringBuilder("html=0;whiteSpace=wrap;rounded=0;shadow=0;");
    style.append("strokeColor=").append(shape.stroke).append(";fillColor=").append(shape.fill)
        .append(";fontColor=").append(shape.font).append(";strokeWidth=").append(decimal(shape.strokeWidth))
        .append(";fontSize=").append(decimal(shape.fontSize)).append(";fontFamily=Arial;dashed=").append(shape.dashed ? "1" : "0")
        .append(";rotation=").append(decimal(shape.rotation)).append(';');
    if (shape.edge()) {
      style.append("edgeStyle=none;noEdgeStyle=1;curved=0;startArrow=").append(shape.startArrow)
          .append(";endArrow=").append(shape.endArrow).append(";startFill=0;endFill=0;labelBackgroundColor=#FFFFFF;");
      xml.append("<mxCell id=\"").append(id).append("\" value=\"").append(escape(shape.label))
          .append("\" style=\"").append(style).append("\" edge=\"1\" parent=\"1\"><mxGeometry relative=\"1\" as=\"geometry\">");
      point(xml, shape.points.get(0), "sourcePoint"); point(xml, shape.points.get(shape.points.size() - 1), "targetPoint");
      if (shape.points.size() > 2) { xml.append("<Array as=\"points\">"); for (int i = 1; i < shape.points.size() - 1; i++) point(xml, shape.points.get(i), null); xml.append("</Array>"); }
      xml.append("</mxGeometry></mxCell>");
    } else {
      if (shape.type.equals("ellipse")) style.append("shape=ellipse;");
      else if (shape.type.equals("text")) style.append("shape=text;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;spacing=2;");
      else style.append("shape=rectangle;");
      xml.append("<mxCell id=\"").append(id).append("\" value=\"").append(escape(shape.label))
          .append("\" style=\"").append(style).append("\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"")
          .append(decimal(shape.x)).append("\" y=\"").append(decimal(shape.y)).append("\" width=\"")
          .append(decimal(shape.width)).append("\" height=\"").append(decimal(shape.height)).append("\" as=\"geometry\"/></mxCell>");
    }
  }

  private static void point(StringBuilder xml, Point point, String name) {
    xml.append("<mxPoint x=\"").append(decimal(point.x)).append("\" y=\"").append(decimal(point.y)).append('"');
    if (name != null) xml.append(" as=\"").append(name).append('"'); xml.append("/>");
  }
  private static double quantize(double value) { return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue(); }
  private static String decimal(double value) { return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }

  /** Plain text only; model values cannot introduce HTML, styles, external URLs, or XML entities. */
  private static String escape(String value) {
    StringBuilder result = new StringBuilder();
    value.codePoints().forEach(cp -> {
      switch (cp) {
        case '&' -> result.append("&amp;"); case '<' -> result.append("&lt;"); case '>' -> result.append("&gt;");
        case '"' -> result.append("&quot;"); case '\'' -> result.append("&apos;");
        case '\n' -> result.append("&#xa;"); case '\r' -> result.append("&#xd;"); case '\t' -> result.append("&#x9;");
        default -> { if ((cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF)) result.appendCodePoint(cp); }
      }
    });
    return result.toString();
  }

  private static Rejected reject(String path, String code, String reason) { return new Rejected(path, code, reason); }
  private static ProductDrawingAiException failure(Context context) {
    RenderIssue first = context.issues.stream().filter(issue -> issue.action.equals("SKIP") || issue.action.equals("REJECT")).findFirst().orElse(null);
    log.warn("drawing_render_empty traceId={} inputPages={} inputShapes={} skippedShapes={} omittedIssues={} firstPath={} firstCode={}",
        context.traceId, context.inputPages, context.inputShapes, context.skippedShapes, context.omittedIssues,
        first == null ? "$" : first.path, first == null ? "NO_VALID_SHAPES" : first.code);
    String diagnostic = first == null ? "NO_VALID_SHAPES" : first.path + " [" + first.code + "]";
    return new ProductDrawingAiException(502, "AI 응답에서 편집 가능한 도형을 찾지 못했습니다. 해석된 도형 " + context.inputShapes
        + "개 중 " + context.skippedShapes + "개를 제외했습니다. 확인 위치: " + diagnostic + ". 이미지를 다시 확인하거나 직접 그려 주세요.");
  }

  private record Point(double x, double y) {}
  private record Page(int index, String name, double width, double height, List<Shape> shapes) {}
  private record Shape(int index, String type, String label, double x, double y, double width, double height,
      List<Point> points, String stroke, String fill, String font, double strokeWidth, double fontSize,
      boolean dashed, String startArrow, String endArrow, double rotation) {
    boolean edge() { return type.equals("line") || type.equals("polyline"); }
  }
  private static final class Rejected extends RuntimeException {
    final String path, code, reason;
    Rejected(String path, String code, String reason) { super(code); this.path = path; this.code = code; this.reason = reason; }
  }
  private static final class Context {
    final String traceId;
    final List<RenderIssue> issues = new ArrayList<>();
    int inputPages, outputPages, inputShapes, renderedShapes, skippedShapes, normalizedFields, fittedPages, clippedCoordinates, omittedIssues, storedNormalizations;
    Context(String traceId) {
      String safe = traceId == null ? "-" : traceId.replaceAll("[^A-Za-z0-9._-]", "_");
      this.traceId = safe.substring(0, Math.min(96, safe.length()));
    }
    void normalize(String path, int page, int element, String code, String reason) { normalizedFields++; issue(path, page, element, code, "NORMALIZE", reason); }
    void issue(String path, int page, int element, String code, String action, String reason) {
      // Reserve half the issue budget for rejected shapes, rather than letting default styles hide them.
      if (issues.size() >= MAX_ISSUES || (action.equals("NORMALIZE") && storedNormalizations >= MAX_ISSUES / 2)) { omittedIssues++; return; }
      RenderIssue issue = new RenderIssue(path, page, element, code, action, reason); issues.add(issue);
      if (action.equals("NORMALIZE")) storedNormalizations++;
      if (action.equals("SKIP") || action.equals("REJECT") || action.equals("TRUNCATE"))
        log.warn("drawing_render_issue traceId={} path={} pageIndex={} elementIndex={} code={} action={} reason={}", traceId, path, page, element, code, action, reason);
      else log.info("drawing_render_issue traceId={} path={} pageIndex={} elementIndex={} code={} action={} reason={}", traceId, path, page, element, code, action, reason);
    }
    RenderStats stats() { return new RenderStats(inputPages, outputPages, inputShapes, renderedShapes, skippedShapes, normalizedFields, fittedPages, clippedCoordinates, omittedIssues); }
  }
}
