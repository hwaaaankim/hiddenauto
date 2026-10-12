package com.dev.HiddenBATHAuto.service.productmaster;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Validates native editable drawio XML without external entity resolution or unbounded inflate. */
public final class ProductDrawingXmlValidator {
  public static final int MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;
  public static final int MAX_PAGES = 40;
  private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
  private static final int MAX_TOTAL_PAGE_BYTES = 8 * 1024 * 1024;
  private static final int MAX_TOTAL_CELLS = 20_000;
  private static final int MAX_TREE_NODES = 200_000;
  private static final int MAX_TREE_DEPTH = 60;

  private ProductDrawingXmlValidator() {}

  public static int validate(String xml) {
    if (xml == null || xml.isBlank())
      throw invalid("도면 내용이 없습니다. 도면을 확인한 뒤 등록해 주세요.");
    if (xml.length() > MAX_DOCUMENT_BYTES)
      throw tooLarge("도면 파일은 5 MB 이하로 등록해 주세요.");
    byte[] input = xml.getBytes(StandardCharsets.UTF_8);
    if (input.length > MAX_DOCUMENT_BYTES)
      throw tooLarge("도면 파일은 5 MB 이하로 등록해 주세요.");
    Document document = parse(input);
    Element file = document.getDocumentElement();
    if (!"mxfile".equals(file.getTagName()))
      throw invalid("편집 가능한 diagrams.net 도면 문서 형식(mxfile)이 필요합니다.");
    validateTree(file);
    int pageCount = 0;
    int totalDecodedBytes = 0;
    int totalCells = 0;
    Set<String> pageIds = new HashSet<>();
    for (Node child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!(child instanceof Element page)) {
        if (child.getNodeType() == Node.TEXT_NODE && !child.getTextContent().isBlank())
          throw invalid("도면 문서의 페이지 구성이 올바르지 않습니다.");
        continue;
      }
      if (!"diagram".equals(page.getTagName()))
        throw invalid("도면 문서에는 diagram 페이지만 포함할 수 있습니다.");
      if (++pageCount > MAX_PAGES)
        throw tooLarge("도면은 최대 40개 페이지까지 등록할 수 있습니다.");
      String pageId = page.getAttribute("id");
      if (!pageId.isBlank() && !pageIds.add(pageId))
        throw invalid("중복된 도면 페이지 ID가 있습니다.");
      Element model = null;
      for (Node content = page.getFirstChild(); content != null; content = content.getNextSibling()) {
        if (content instanceof Element element) {
          if (model != null || !"mxGraphModel".equals(element.getTagName()))
            throw invalid("각 도면 페이지에는 mxGraphModel 하나가 필요합니다.");
          model = element;
        }
      }
      if (model != null) {
        for (Node content = page.getFirstChild(); content != null; content = content.getNextSibling()) {
          if ((content.getNodeType() == Node.TEXT_NODE || content.getNodeType() == Node.CDATA_SECTION_NODE)
              && !content.getTextContent().isBlank())
            throw invalid("도면 페이지에는 도면 모델과 압축 내용을 함께 넣을 수 없습니다.");
        }
      }
      if (model == null) {
        byte[] expanded = decodeCompressedPage(page.getTextContent());
        totalDecodedBytes += expanded.length;
        if (totalDecodedBytes > MAX_TOTAL_PAGE_BYTES)
          throw tooLarge("압축 해제된 도면 내용이 허용 용량을 초과했습니다.");
        model = parse(expanded).getDocumentElement();
      }
      if (!"mxGraphModel".equals(model.getTagName()))
        throw invalid("도면 페이지 형식이 올바르지 않습니다.");
      totalCells += validateModel(model);
      if (totalCells > MAX_TOTAL_CELLS)
        throw tooLarge("도면의 도형 수가 허용 범위를 초과했습니다.");
    }
    if (pageCount == 0) throw invalid("도면에는 최소 한 개의 페이지가 필요합니다.");
    return pageCount;
  }

  private static int validateModel(Element model) {
    validateTree(model);
    Element root = null;
    for (Node child = model.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element) {
        if (root != null || !"root".equals(element.getTagName()))
          throw invalid("도면 모델의 root 구성이 올바르지 않습니다.");
        root = element;
      }
    }
    if (root == null) throw invalid("도면 모델의 root가 없습니다.");
    var cells = root.getElementsByTagName("mxCell");
    if (cells.getLength() < 2)
      throw invalid("도면의 기본 레이어 정보가 없습니다.");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < cells.getLength(); i++) {
      Element cell = (Element) cells.item(i);
      String id = cell.getAttribute("id");
      // Custom properties wrap mxCell in UserObject/object and place its ID on the wrapper.
      if (id.isBlank() && cell.getParentNode() instanceof Element owner)
        id = owner.getAttribute("id");
      if (id.isBlank() || !ids.add(id))
        throw invalid("도면 도형의 ID가 없거나 중복되었습니다.");
    }
    return cells.getLength();
  }

  private static void validateTree(Element root) {
    record Entry(Node node, int depth) {}
    ArrayDeque<Entry> pending = new ArrayDeque<>();
    pending.add(new Entry(root, 0));
    int count = 0;
    while (!pending.isEmpty()) {
      Entry current = pending.removeFirst();
      if (++count > MAX_TREE_NODES || current.depth() > MAX_TREE_DEPTH)
        throw tooLarge("도면의 중첩 구조가 허용 범위를 초과했습니다.");
      for (Node child = current.node().getFirstChild(); child != null; child = child.getNextSibling())
        pending.addLast(new Entry(child, current.depth() + 1));
    }
  }

  private static byte[] decodeCompressedPage(String content) {
    if (content == null || content.isBlank())
      throw invalid("도면 페이지의 내용이 없습니다.");
    StringBuilder base64 = new StringBuilder();
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (!Character.isWhitespace(c)) base64.append(c);
    }
    final byte[] compressed;
    try {
      compressed = Base64.getDecoder().decode(base64.toString());
    } catch (IllegalArgumentException exception) {
      throw invalid("압축 도면 페이지 형식이 올바르지 않습니다.");
    }
    Inflater inflater = new Inflater(true);
    try {
      inflater.setInput(compressed);
      ByteArrayOutputStream expanded = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      while (!inflater.finished()) {
        int count = inflater.inflate(buffer);
        if (count > 0) {
          if (expanded.size() + count > MAX_PAGE_BYTES * 3)
            throw tooLarge("압축 해제된 도면 페이지는 2 MB 이하로 등록해 주세요.");
          expanded.write(buffer, 0, count);
        } else if (inflater.finished()) {
          // A valid final DEFLATE marker may finish the stream without producing more XML bytes.
          break;
        } else if (inflater.needsInput() || inflater.needsDictionary()) {
          throw invalid("압축 도면 페이지를 읽을 수 없습니다.");
        } else {
          throw invalid("압축 도면 페이지의 구조가 올바르지 않습니다.");
        }
      }
      if (inflater.getRemaining() != 0)
        throw invalid("압축 도면 페이지에 불필요한 데이터가 포함되어 있습니다.");
      String decoded = URLDecoder.decode(expanded.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      byte[] result = decoded.getBytes(StandardCharsets.UTF_8);
      if (result.length > MAX_PAGE_BYTES)
        throw tooLarge("압축 해제된 도면 페이지는 2 MB 이하로 등록해 주세요.");
      return result;
    } catch (DataFormatException | IllegalArgumentException exception) {
      if (exception instanceof InvalidDrawingException drawingException) throw drawingException;
      throw invalid("압축 도면 페이지를 읽을 수 없습니다.");
    } finally {
      inflater.end();
    }
  }

  private static Document parse(byte[] xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var builder = factory.newDocumentBuilder();
      builder.setErrorHandler(new ErrorHandler() {
        public void warning(SAXParseException exception) throws SAXException { throw exception; }
        public void error(SAXParseException exception) throws SAXException { throw exception; }
        public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
      });
      return builder.parse(new ByteArrayInputStream(xml));
    } catch (Exception exception) {
      throw invalid("도면 XML을 읽을 수 없습니다. 외부 엔티티·DTD가 없는 정상 도면을 사용해 주세요.");
    }
  }

  private static InvalidDrawingException invalid(String message) {
    return new InvalidDrawingException(message, false);
  }

  private static InvalidDrawingException tooLarge(String message) {
    return new InvalidDrawingException(message, true);
  }

  public static final class InvalidDrawingException extends IllegalArgumentException {
    private final boolean tooLarge;

    InvalidDrawingException(String message, boolean tooLarge) {
      super(message);
      this.tooLarge = tooLarge;
    }

    public boolean isTooLarge() { return tooLarge; }
  }
}
