package com.dev.HiddenBATHAuto.storefront.checkout;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Answer;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.ProductView;
import com.dev.HiddenBATHAuto.repository.storefront.StoreDeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStorefrontService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStudioService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingService;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.EvaluateRequest;
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
                       BigDecimal productionDays, LocalDate earliest, boolean custom, String series, List<String> summary) {}
    public record Saved(long id, String kind, String note, Item item, String createdAt) {}
    public record Line(String key, Item item, int quantity, String address, LocalDate date, long methodId) {}
    public record Checkout(List<Line> lines, BigDecimal points) {}
    public record ResultLine(String key, Item item, int quantity, String address, LocalDate date, String method,
                             BigDecimal delivery, BigDecimal packing) {}
    public record Result(List<ResultLine> lines, BigDecimal products, BigDecimal delivery, BigDecimal packing,
                         BigDecimal points, BigDecimal payable) {}
    private final ProductStudioService studio;
    private final ProductStorefrontService storefront;
    private final ProductPricingService prices;
    private final StoreDeliveryMethodRepository methods;
    private final CompanyRepository companies;
    private final ObjectMapper json;
    private final JdbcTemplate db;
    private final Set<LocalDate> holidays;

    public StorePreviewService(ProductStudioService studio, ProductStorefrontService storefront,
            ProductPricingService prices, StoreDeliveryMethodRepository methods, CompanyRepository companies,
            ObjectMapper json, @Qualifier("dataSource") DataSource source,
            @Value("${hiddenbath.storefront.holidays:}") String holidays) {
        this.studio = studio;
        this.storefront = storefront;
        this.prices = prices;
        this.methods = methods;
        this.companies = companies;
        this.json = json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.db = new JdbcTemplate(source);
        this.holidays = new HashSet<>();
        Arrays.stream(holidays.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(LocalDate::parse).forEach(this.holidays::add);
    }

    public String actor(long company) { return "SHOP_PREVIEW_COMPANY:" + company; }

    public ProductView active(long id) {
        var p = studio.detail(id);
        if (!"ACTIVE".equals(p.status())) throw new NoSuchElementException("현재 발주할 수 없는 제품입니다.");
        return p;
    }

    public Item item(Selection selection, long company, boolean bookmark) {
        var p = active(selection.productId());
        Map<Long, String> options = selection.options() == null ? Map.of() : selection.options();
        Map<String, Answer> answers = selection.answers() == null ? Map.of() : selection.answers();
        BigDecimal price = p.unitPrice();
        List<String> summary = new ArrayList<>();
        Map<String, Answer> canonicalAnswers = new TreeMap<>();
        String series = p.groups().stream().filter(g -> "SERIES".equals(g.role())).findFirst()
                .flatMap(g -> p.variants().stream().filter(v -> g.id().equals(v.groupId())).findFirst()
                        .flatMap(v -> g.values().stream().filter(value -> v.valueIds().contains(value.id()))
                                .map(value -> value.labels().customer()).findFirst()))
                .orElse("");
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
                    if (value instanceof List<?> files) values.add(field.labels().customer() + " " + files.size() + "개 파일");
                    else if (value != null) values.add(field.labels().customer() + " " + value);
                });
                if (!values.isEmpty()) summary.add(q.question().labels().customer() + " · " + String.join(" / ", values));
            });
            if (bookmark) {
                if (answers.isEmpty() || !canonicalAnswers.keySet().containsAll(answers.keySet())) {
                    throw new IllegalArgumentException("완료한 질문의 답변을 확인해 주세요.");
                }
                price = null;
            } else {
                if (!evaluation.complete()) throw new IllegalArgumentException("제품 질문을 모두 완료해 주세요.");
                var quote = prices.evaluate(p.id(), new EvaluateRequest(null, evaluation.answers()));
                if (!quote.complete()) throw new IllegalArgumentException("등록된 가격식을 확인해 주세요.");
                price = quote.total();
            }
        } else {
            if (bookmark) throw new IllegalArgumentException("비규격 제품의 진행 상태만 저장할 수 있습니다.");
            var config = storefront.configure(selection.seriesId(), options, true);
            if (!config.complete() || config.products().stream().noneMatch(candidate -> candidate.id().equals(p.id()))) {
                throw new IllegalArgumentException("규격 옵션과 제품을 다시 확인해 주세요.");
            }
            config.display().stream().filter(row -> row.answer() != null)
                    .map(row -> row.group().customer() + " · " + row.answer().customer()).forEach(summary::add);
        }
        if (!bookmark && (price == null || price.signum() < 0)) {
            throw new IllegalArgumentException("발주 가능한 단가가 없습니다.");
        }
        var clean = new Selection(p.id(), p.id(),
                p.nonStandard() ? Map.of() : options, canonicalAnswers);
        var stampData = Arrays.asList(clean, p.version(), p.process(), p.variants(), summary,
                price == null ? null : price.stripTrailingZeros().toPlainString());
        String image = "/administration/assets/images/customer-order-example/"
                + String.format(Locale.ROOT, "%02d.webp", (p.id() % 22) + 1);
        return new Item(clean, hash(encode(stampData)), p.productName(), image, price, p.productionHours(),
                p.productionHours() == null ? null : StoreCheckoutPolicy.earliestDate(today(), p.productionHours(), holidays::contains),
                p.nonStandard(), series, summary);
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }
    public Set<LocalDate> holidays() { return Set.copyOf(holidays); }
    public int balance(long company) { return companies.findById(company).orElseThrow().getPoint(); }

    public Item check(Item previous, long company, boolean bookmark) {
        Item current;
        try {
            current = item(previous.selection(), company, bookmark);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            throw new Changed();
        }
        if (!current.stamp().equals(previous.stamp())) throw new Changed();
        return current;
    }

    public static class Changed extends RuntimeException {
        public Changed() { super("해당제품의 정보가 변경되었습니다. 새로 선택해 주세요."); }
    }

    public List<Saved> saved(long company) {
        return db.query("SELECT id, kind, note, payload, created_at FROM tb_store_preview_saved WHERE company_id=? ORDER BY id DESC",
                (rs, i) -> new Saved(rs.getLong(1), rs.getString(2), rs.getString(3), decode(rs.getString(4)), rs.getTimestamp(5).toInstant().toString()), company);
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
        var shipping = new HashMap<Long, com.dev.HiddenBATHAuto.model.storefront.StoreDeliveryMethod>();
        methods.findAllByOrderByIdAsc().forEach(m -> shipping.put(m.getId(), m));
        for (Line line : request.lines()) {
            Item p = check(line.item(), company, false);
            if (line.quantity() > 99999 || line.key() == null || !keys.add(line.key()) || line.address() == null
                    || line.address().isBlank() || line.address().length() > 400) {
                throw new IllegalArgumentException("품목과 배송 주소를 확인해 주세요.");
            }
            if (p.earliest() == null || line.date() == null || line.date().isBefore(p.earliest())
                    || !StoreCheckoutPolicy.isBusinessDay(line.date(), holidays::contains)) {
                throw new IllegalArgumentException(p.name() + "의 희망 출고일을 확인해 주세요.");
            }
            var method = shipping.get(line.methodId());
            if (method == null) throw new IllegalArgumentException("배송수단을 다시 선택해 주세요.");
            var group = new StoreCheckoutPolicy.DeliveryGroup(line.date(), method.getId());
            var fees = new StoreCheckoutPolicy.Fees(BigDecimal.valueOf(method.getDeliveryFee()), BigDecimal.valueOf(method.getPackingFee()));
            order.add(new StoreCheckoutPolicy.Line(p.price(), line.quantity(), group, fees));
            boolean first = seen.add(group);
            details.add(new ResultLine(line.key(), p, line.quantity(), line.address().strip(), line.date(), method.getName(),
                    first ? fees.delivery() : BigDecimal.ZERO, first ? fees.packing() : BigDecimal.ZERO));
        }
        var total = StoreCheckoutPolicy.calculate(order, request.points(), BigDecimal.valueOf(balance(company)));
        return new Result(details, total.products(), total.delivery(), total.packing(), total.points(), total.payable());
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
