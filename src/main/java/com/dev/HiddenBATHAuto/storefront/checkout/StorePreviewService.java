package com.dev.HiddenBATHAuto.storefront.checkout;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Answer;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.ProductView;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStorefrontService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStudioService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingService;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.EvaluateRequest;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StorePreviewService {
    public record Selection(long productId, long seriesId, Map<Long, String> options, Map<String, Answer> answers) {}
    public record Item(Selection selection, String stamp, String name, String image, BigDecimal price,
                       BigDecimal productionDays, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate earliest, boolean custom, String series, List<String> summary, List<String> compactSummary, String category, BigDecimal width) {}
    public record Saved(long id, String kind, String note, Item item, String createdAt) {}
    public record Address(String zip, String province, String city, String district, String road, String jibun, String detail) {
        String display() {
            return java.util.stream.Stream.of(zip, road == null || road.isBlank() ? jibun : road, detail)
                .filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty()).collect(java.util.stream.Collectors.joining(" "));
        }
        boolean valid() {
            return zip != null && zip.matches("[0-9]{5}") && province != null && !province.isBlank()
                && ((road != null && !road.isBlank()) || (jibun != null && !jibun.isBlank())) && display().length() <= 500;
        }
    }
    public record Contacts(String orderer, String phone, String manager, String managerPhone) {}
    public record Line(String key, Item item, int quantity, Address address, Contacts contacts, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date, long methodId) {}
    public record Checkout(List<Line> lines, BigDecimal points) {}
    public record ResultLine(String key, Item item, int quantity, Address address, Contacts contacts, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date, String method,
                             Integer delivery, Integer packing, String explanation) {}
    public record Result(List<ResultLine> lines, BigDecimal products, BigDecimal delivery, BigDecimal packing,
                         BigDecimal points, BigDecimal payable, boolean pending) {}
    private final ProductStudioService studio;
    private final ProductStorefrontService storefront;
    private final ProductPricingService prices;
    private final CompanyRepository companies;
    private final ObjectMapper json;
    private final JdbcTemplate db;
    private final Set<LocalDate> holidays;

    public StorePreviewService(ProductStudioService studio, ProductStorefrontService storefront,
            ProductPricingService prices, CompanyRepository companies,
            ObjectMapper json, @Qualifier("dataSource") DataSource source,
            @Value("${hiddenbath.storefront.holidays:}") String holidays) {
        this.studio = studio;
        this.storefront = storefront;
        this.prices = prices;
        this.companies = companies;
        this.json = json.copy().registerModule(new JavaTimeModule()).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.db = new JdbcTemplate(source);
        this.holidays = new HashSet<>();
        // 한국 월력요항. 임시공휴일은 외부 설정값으로 추가합니다.
        String published = "2026-12-25 2027-01-01 2027-02-06 2027-02-07 2027-02-08 2027-02-09 2027-03-01 "
                + "2027-05-01 2027-05-03 2027-05-05 2027-05-13 2027-06-06 2027-07-17 2027-07-19 "
                + "2027-08-15 2027-08-16 2027-09-14 2027-09-15 2027-09-16 2027-10-03 2027-10-04 "
                + "2027-10-09 2027-10-11 2027-12-25 2027-12-27";
        Arrays.stream(published.split(" ")).map(LocalDate::parse).forEach(this.holidays::add);
        Arrays.stream(holidays.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(LocalDate::parse).forEach(this.holidays::add);
    }

    public String actor(long company) { return "SHOP_PREVIEW_COMPANY:" + company; }

    public ProductView orderable(long id) {
        storefront.requireCustomerProduct(id);
        var p = studio.detail(id);
        if (p.nonStandard() && (p.process() == null || p.process().questions() == null || p.process().questions().isEmpty()))
            throw new IllegalArgumentException("비규격 제품의 질문 구성이 등록되지 않았습니다.");
        return p;
    }

    public Item item(Selection selection, long company, boolean bookmark) {
        return item(selection, company, bookmark, false);
    }

    private Item item(Selection selection, long company, boolean bookmark, boolean legacyStamp) {
        var p = orderable(selection.productId());
        Map<Long, String> options = selection.options() == null ? Map.of() : selection.options();
        Map<String, Answer> answers = selection.answers() == null ? Map.of() : selection.answers();
        BigDecimal price = p.unitPrice();
        List<String> summary = new ArrayList<>();
        Map<Long,String> byGroup = new LinkedHashMap<>();
        Map<String, Answer> canonicalAnswers = new TreeMap<>();
        String series = p.groups().stream().filter(g -> "SERIES".equals(g.role())).findFirst()
                .flatMap(g -> p.variants().stream().filter(v -> g.id().equals(v.groupId())).findFirst()
                        .flatMap(v -> g.values().stream().filter(value -> v.valueIds().contains(value.id()))
                                .map(value -> value.labels().customer()).findFirst()))
                .orElse("");
        String category = "";
        Set<BigDecimal> widths = new HashSet<>();
        for (var group : p.groups()) for (var variant : p.variants()) {
            if (!group.id().equals(variant.groupId()) || variant.valueIds() == null) continue;
            for (var value : group.values()) {
                if (!variant.valueIds().contains(value.id())) continue;
                String label = value.labels().customer();
                if ("CATEGORY".equals(group.role())) category = label;
                if (!p.nonStandard()) collectWidth(widths, group.labels().customer(), label);
            }
        }
        if (p.nonStandard()) {
            var evaluation = studio.evaluate(p.id(), new com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.EvaluateRequest(null, answers), actor(company));
            evaluation.questions().stream().filter(q -> q.visible() && !q.question().fixed()
                    && q.answer() != null && q.errors().isEmpty() && answers.containsKey(q.question().key())).forEach(q -> {
                List<String> values = new ArrayList<>();
                List<String> choices = q.answer().choices() == null ? List.of() : q.answer().choices().stream().sorted().toList();
                Map<String, Object> fields = new TreeMap<>();
                if (q.answer().fields() != null) q.answer().fields().forEach((key, value) ->
                        fields.put(key, value instanceof BigDecimal number ? number.stripTrailingZeros() : value));
                canonicalAnswers.put(q.question().key(), new Answer(choices, fields));
                if (q.answer().choices() != null) q.question().choices().stream()
                        .filter(choice -> q.answer().choices().contains(choice.key()))
                        .map(choice -> choice.labels().customer()).forEach(values::add);
                if (q.answer().fields() != null) q.question().fields().forEach(field -> {
                    Object value = q.answer().fields().get(field.key());
                    if (value != null && (field.unit() == null || field.unit().isBlank() || "mm".equalsIgnoreCase(field.unit())))
                        collectWidth(widths, field.labels().customer(), value.toString());
                    if (value instanceof List<?> files) values.add(field.labels().customer() + " " + files.size() + "개 파일");
                    else if (value != null) values.add(field.labels().customer() + " " + value);
                });
                if (!values.isEmpty()) {
                    String line = q.question().labels().customer() + " · " + String.join(" / ", values);
                    summary.add(line);
                    byGroup.put(q.question().groupId(),line);
                }
            });
            if (bookmark) {
                if (answers.isEmpty() || !canonicalAnswers.keySet().containsAll(answers.keySet())) {
                    throw new IllegalArgumentException("완료한 질문의 답변을 확인해 주세요.");
                }
                price = null;
            } else {
                if (!evaluation.complete()) throw new IllegalArgumentException("제품 질문을 모두 완료해 주세요.");
                var quote = prices.evaluate(p.id(), new EvaluateRequest(null, evaluation.answers()));
                if (!quote.complete()) throw new IllegalArgumentException(quote.errors() == null || quote.errors().isEmpty()
                        ? "등록된 가격식을 확인해 주세요." : String.join(" · ", quote.errors()));
                price = quote.total();
            }
        } else {
            if (bookmark) throw new IllegalArgumentException("비규격 제품의 진행 상태만 저장할 수 있습니다.");
            var config = storefront.configure(selection.seriesId(), options, true);
            if (!config.complete() || config.products().stream().noneMatch(candidate -> candidate.id().equals(p.id()))) {
                throw new IllegalArgumentException("규격 옵션과 제품을 다시 확인해 주세요.");
            }
            config.display().stream().filter(row -> row.answer() != null)
                    .forEach(row -> {
                        String line = row.group().customer() + " · " + row.answer().customer();
                        summary.add(line);
                        byGroup.put(row.groupId(),line);
                    });
        }
        if (!bookmark && (price == null || price.signum() < 0)) {
            throw new IllegalArgumentException("발주 가능한 단가가 없습니다.");
        }
        var clean = new Selection(p.id(), p.id(),
                p.nonStandard() ? Map.of() : options, canonicalAnswers);
        String priceKey = price == null ? null : price.stripTrailingZeros().toPlainString();
        var stampData = legacyStamp
                ? Arrays.asList(clean, p.version(), p.process(), p.variants(), summary, priceKey)
                : Arrays.asList(clean, p.process(), p.variants(), summary, priceKey, p.productName(), p.productionHours());
        String stamp = (legacyStamp ? "" : "configuration:") + hash(encode(stampData));
        String image = "/administration/assets/images/customer-order-example/"
                + String.format(Locale.ROOT, "%02d.webp", (p.id() % 22) + 1);
        List<String> compact = new ArrayList<>();
        for (var setting : p.summaryOptions()) if ("customer".equals(setting.role())) {
            String line = byGroup.remove(setting.groupId());
            if (line != null && setting.visible()) compact.add(line);
        }
        compact.addAll(byGroup.values());
        return new Item(clean, stamp, p.productName(), image, price, p.productionHours(),
                StoreCheckoutPolicy.earliestDate(today(), p.productionHours(), holidays::contains),
                p.nonStandard(), series, summary, compact, Objects.toString(category, ""), widths.size() == 1 ? widths.iterator().next() : null);
    }

    private static void collectWidth(Set<BigDecimal> widths, String label, String value) {
        if (label == null || value == null) return;
        String number = value.strip();
        if (Set.of("사이즈", "규격", "크기").contains(label.strip())) {
            String[] parts = number.split("[*×xX]");
            if (parts.length < 2) return;
            number = parts[0].strip();
        } else if (!Set.of("가로", "가로 사이즈", "가로사이즈", "폭", "너비", "W").contains(label.strip())) return;
        if (number.matches("[0-9]+(?:[.][0-9]+)?(?: *mm)?"))
            widths.add(new BigDecimal(number.replace("mm", "").strip()).stripTrailingZeros());
    }

    public Address companyAddress(long id) {
        var c = companies.findById(id).orElseThrow();
        return new Address(c.getZipCode(), c.getDoName(), c.getSiName(), c.getGuName(), c.getRoadAddress(), c.getJibunAddress(), c.getDetailAddress());
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }
    public Set<LocalDate> holidays() { return Set.copyOf(holidays); }
    public int balance(long company) { return companies.findById(company).orElseThrow().getPoint(); }

    public Item check(Item previous, long company, boolean bookmark) {
        Item current;
        try {
            current = item(previous.selection(), company, bookmark);
            if (previous.stamp() != null && previous.stamp().matches("[a-f0-9]{64}")
                    && item(previous.selection(), company, bookmark, true).stamp().equals(previous.stamp())) {
                return current;
            }
        } catch (IllegalArgumentException | NoSuchElementException e) {
            throw new Changed(e.getMessage());
        }
        if (!current.stamp().equals(previous.stamp())) throw new Changed();
        return current;
    }

    public static class Changed extends RuntimeException {
        public Changed() { this(null); }
        public Changed(String reason) {
            super(reason == null || reason.isBlank() ? "제품 정보 또는 가격이 변경되었습니다. 다시 선택해 주세요."
                    : "저장한 제품을 다시 확인해 주세요. " + reason);
        }
    }

    public Map<String, Long> savedCounts(long company) {
        Map<String, Long> counts = new HashMap<>(Map.of("interest", 0L, "bookmark", 0L));
        db.query("SELECT kind, COUNT(*) FROM tb_store_preview_saved WHERE company_id=? GROUP BY kind",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, company);
        return counts;
    }

    public List<Saved> saved(long company) {
        return db.query("SELECT id, kind, note, payload, created_at FROM tb_store_preview_saved WHERE company_id=? ORDER BY id DESC",
                (rs, i) -> {
                    Item savedItem;
                    try {
                        savedItem = decode(rs.getString(4));
                    } catch (IllegalStateException error) {
                        savedItem = null;
                    }
                    return new Saved(rs.getLong(1), rs.getString(2), rs.getString(3), savedItem, rs.getTimestamp(5).toInstant().toString());
                }, company);
    }

    public void save(long company, String kind, String note, Item previous) {
        if (!Set.of("interest", "bookmark").contains(kind) || note == null || note.length() > 300) {
            throw new IllegalArgumentException("저장 유형과 남김말을 확인해 주세요.");
        }
        Item current = check(previous, company, "bookmark".equals(kind));
        String identity = hash(encode(current.selection()));
        try {
            db.update("INSERT INTO tb_store_preview_saved(company_id,kind,identity_key,note,payload) VALUES (?,?,?,?,?)",
                    company, kind, identity, note.strip(), encode(current));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new IllegalArgumentException("동일한 구성이 이미 저장되어 있습니다.");
        }
    }

    public void remove(long company, long id) {
        db.update("DELETE FROM tb_store_preview_saved WHERE company_id=? AND id=?", company, id);
    }

    public Result checkout(long company, Checkout request) {
        if (request.lines() == null || request.lines().isEmpty() || request.lines().size() > 100) {
            throw new IllegalArgumentException("발주할 품목을 1~100개 선택해 주세요.");
        }
        List<ResultLine> details = new ArrayList<>();
        List<StoreCheckoutPolicy.Line> order = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<StoreCheckoutPolicy.DeliveryGroup> seen = new HashSet<>();
        var items = new LinkedHashMap<String, Item>();
        var groupByLine = new HashMap<String, StoreCheckoutPolicy.DeliveryGroup>();
        var groupedProducts = new LinkedHashMap<StoreCheckoutPolicy.DeliveryGroup, List<StoreShipping.Product>>();
        var addresses = new HashMap<String, Address>();
        var direct = companyAddress(company);
        for (Line line : request.lines()) {
            Item p = check(line.item(), company, false);
            Address address = line.methodId() == 1 ? direct : line.address();
            if (line.quantity() < 1 || line.quantity() > 99999 || line.key() == null || !keys.add(line.key())
                    || address == null || !address.valid())
                throw new IllegalArgumentException(p.name() + "의 수량과 배송 주소를 확인해 주세요.");
            if (line.date() == null || line.date().isBefore(p.earliest()) || !StoreCheckoutPolicy.isBusinessDay(line.date(), holidays::contains))
                throw new IllegalArgumentException(p.name() + "의 희망 출고일을 확인해 주세요.");
            var group = new StoreCheckoutPolicy.DeliveryGroup(line.date(), line.methodId(), address.display().replaceAll("\\s+", " "));
            items.put(line.key(), p);
            addresses.put(line.key(), address);
            groupByLine.put(line.key(), group);
            groupedProducts.computeIfAbsent(group, ignored -> new ArrayList<>()).add(new StoreShipping.Product(p.category(), p.width(), line.quantity()));
        }
        var quotes = new HashMap<StoreCheckoutPolicy.DeliveryGroup, StoreShipping.Quote>();
        boolean pending = false;
        for (Line line : request.lines()) {
            var group = groupByLine.get(line.key());
            var address = addresses.get(line.key());
            var quote = quotes.computeIfAbsent(group, g -> StoreShipping.quote(g.methodId(), address.province().startsWith("제주"), groupedProducts.get(g)));
            var method = StoreShipping.METHODS.stream().filter(m -> m.id() == line.methodId()).findFirst().orElseThrow();
            var p = items.get(line.key());
            boolean first = seen.add(group);
            pending |= quote.delivery() == null;
            var fees = new StoreCheckoutPolicy.Fees(BigDecimal.valueOf(Objects.requireNonNullElse(quote.delivery(), 0)),
                    BigDecimal.valueOf(Objects.requireNonNullElse(quote.packing(), 0)));
            order.add(new StoreCheckoutPolicy.Line(p.price(), line.quantity(), group, fees));
            details.add(new ResultLine(line.key(), p, line.quantity(), address, line.contacts(), line.date(), method.name(),
                    first || quote.delivery() == null ? quote.delivery() : Integer.valueOf(0),
                    first || quote.packing() == null ? quote.packing() : Integer.valueOf(0),
                    first ? quote.reason() : "같은 묶음의 첫 품목에 합산 · " + quote.reason()));
        }
        var total = StoreCheckoutPolicy.calculate(order, request.points(), BigDecimal.valueOf(balance(company)));
        return new Result(details, total.products(), total.delivery(), total.packing(), total.points(), total.payable(), pending);
    }

    private Item decode(String value) {
        try { return json.readValue(value, Item.class); }
        catch (java.io.IOException e) { throw new IllegalStateException("저장된 구성 데이터를 읽을 수 없습니다.", e); }
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("구성 데이터를 해석할 수 없습니다.", e); }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
