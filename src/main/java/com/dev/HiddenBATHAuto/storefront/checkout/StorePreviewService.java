package com.dev.HiddenBATHAuto.storefront.checkout;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Answer;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.ProductView;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Variant;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStorefrontService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStorefrontService.UnavailableConfiguration;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStudioService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingService;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.EvaluateRequest;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.dev.HiddenBATHAuto.utils.KoreanAdministrativeRegionNormalizer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StorePreviewService {
    public record Selection(long productId, long seriesId, Map<Long, String> options, Map<String, Answer> answers) {}
    public record Item(Selection selection, String stamp, String name, String image, BigDecimal price,
                       BigDecimal productionDays, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate earliest, boolean custom, String series, List<String> summary, List<String> compactSummary, String category, BigDecimal width) {}
    public record CardMetadata(String category, String series, BigDecimal productionDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate earliest, String image) {}
    public record Saved(long id, String kind, String note, Item item, String createdAt) {}
    public record Address(String zip, String province, String city, String district, String road, String jibun, String detail) {
        public Address {
            zip = meaningful(zip);
            province = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(meaningful(province));
            city = meaningful(city);
            district = meaningful(district);
            road = meaningful(road);
            jibun = meaningful(jibun);
            detail = meaningful(detail);
            // Historic address-book screens used the selected parcel as the base address if no road existed.
            if (!jibun.isEmpty() && jibun.equals(road)) road = "";
        }
        private static String meaningful(String value) {
            String clean = value == null ? "" : value.strip().replaceAll("(?U)\\s+", " ");
            return Set.of("-", "–", "—").contains(clean) ? "" : clean;
        }
        String baseAddress() { return road.isEmpty() ? jibun : road; }
        boolean valid() {
            return zip.matches("[0-9]{5}") && !province.isEmpty() && !baseAddress().isEmpty()
                && road.length() <= 255 && jibun.length() <= 255 && detail.length() <= 255;
        }
    }
    public record Contacts(String orderer, String phone, String manager, String managerPhone) {}
    public record Line(String key, Item item, int quantity, Address address, Contacts contacts, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date, long methodId) {}
    public record Checkout(List<Line> lines, BigDecimal points) {}
    public record ResultLine(String key, Item item, int quantity, Address address, Contacts contacts, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date, String method,
                             Integer delivery, Integer packing, String explanation, String shippingGroup) {}
    public record Result(List<ResultLine> lines, BigDecimal products, BigDecimal delivery, BigDecimal packing,
                         BigDecimal points, BigDecimal payable, boolean pending) {}
    private final ProductStudioService studio;
    private final ProductStorefrontService storefront;
    private final ProductPricingService prices;
    private final CompanyRepository companies;
    private final ObjectMapper json;
    private final JdbcTemplate db;
    private final Set<LocalDate> holidays;
    // Extend this boundary together with hiddenbath.storefront.holidays when the next official calendar is published.
    private LocalDate calendarThrough = LocalDate.of(2027, 12, 31);

    @Value("${hiddenbath.storefront.calendar-through:2027-12-31}")
    public void setCalendarThrough(String date) { this.calendarThrough = LocalDate.parse(date.strip()); }
    public LocalDate calendarThrough() { return calendarThrough; }

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
            throw new UnavailableConfiguration("비규격 제품의 질문 구성이 등록되지 않았습니다.");
        return p;
    }

    public Item item(Selection selection, long company, boolean bookmark) {
        if (selection == null || selection.productId() <= 0)
            throw new IllegalArgumentException("제품의 저장된 선택 정보를 확인해 주세요.");
        var p = orderable(selection.productId());
        Map<Long, String> options = selection.options() == null ? Map.of() : selection.options();
        Map<String, Answer> answers = selection.answers() == null ? Map.of() : selection.answers();
        BigDecimal price = p.unitPrice();
        List<String> summary = new ArrayList<>();
        Map<Long,List<String>> byGroup = new LinkedHashMap<>();
        Map<String, Answer> canonicalAnswers = new TreeMap<>();
        List<Object> path = new ArrayList<>();
        Map<Long, List<String>> optionLabels = new TreeMap<>();
        String series = p.groups().stream().filter(g -> "SERIES".equals(g.role())).findFirst()
                .flatMap(g -> p.variants().stream().filter(v -> g.id().equals(v.groupId())).findFirst()
                        .flatMap(v -> g.values().stream().filter(value -> v.valueIds().contains(value.id()))
                                .map(value -> value.labels().customer()).findFirst()))
                .orElse("");
        String category = "";
        Set<BigDecimal> widths = new HashSet<>();
        for (var group : p.groups()) for (var variant : p.variants()) {
            if (!group.id().equals(variant.groupId())) continue;
            if (!p.nonStandard() && variant.inputs() != null) {
                for (var field : group.fields()) {
                    Object raw = variant.inputs().get(field.key());
                    if (raw != null && millimetres(field.unit())) collectWidth(widths, field.labels().customer(), raw.toString());
                }
            }
            if (variant.valueIds() == null) continue;
            for (var value : group.values()) {
                if (!variant.valueIds().contains(value.id())) continue;
                String label = value.labels().customer();
                if ("CATEGORY".equals(group.role())) category = label;
                if (!p.nonStandard()) {
                    collectWidth(widths, group.labels().customer(), label);
                    optionLabels.computeIfAbsent(group.id(), ignored -> new ArrayList<>()).add(label);
                }
            }
        }
        if (p.nonStandard()) {
            var evaluation = studio.evaluate(p.id(), new com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.EvaluateRequest(null, answers), actor(company));
            evaluation.questions().stream().filter(q -> q.answer() != null && q.errors().isEmpty()
                    && (q.question().fixed() || (q.visible() && answers.containsKey(q.question().key())))).forEach(q -> {
                boolean userAnswer = !q.question().fixed();
                List<String> values = new ArrayList<>();
                List<String> choices = q.answer().choices() == null ? List.of() : q.answer().choices().stream().sorted().toList();
                Map<String, Object> fields = new TreeMap<>();
                if (q.answer().fields() != null) q.answer().fields().forEach((key, value) ->
                        fields.put(key, value instanceof BigDecimal number ? number.stripTrailingZeros() : value));
                if (userAnswer) canonicalAnswers.put(q.question().key(), new Answer(choices, fields));
                if (q.answer().choices() != null) q.question().choices().stream()
                        .filter(choice -> q.answer().choices().contains(choice.key()))
                        .map(choice -> choice.labels().customer()).forEach(label -> {
                            values.add(label);
                            if (userAnswer) {
                                optionLabels.computeIfAbsent(q.question().groupId(), ignored -> new ArrayList<>()).add(label);
                                collectWidth(widths, q.question().labels().customer(), label);
                            }
                        });
                if (q.answer().fields() != null) q.question().fields().forEach(field -> {
                    Object value = q.answer().fields().get(field.key());
                    if (userAnswer && value != null && millimetres(field.unit()))
                        collectWidth(widths, field.labels().customer(), value.toString());
                    if (value instanceof List<?> files) values.add(field.labels().customer() + " " + files.size() + "개 파일");
                    else if (value != null) values.add(field.labels().customer() + " " + comparableValue(value));
                });
                if (!values.isEmpty()) {
                    String line = q.question().labels().customer() + " · " + String.join(" / ", values);
                    summary.add(line);
                    byGroup.computeIfAbsent(q.question().groupId(), ignored -> new ArrayList<>()).add(line);
                }
            });
            // Only the traversed prefix belongs to a partial bookmark. Unanswered future groups and their help text do not.
            int through = -1;
            for (int i = 0; i < evaluation.questions().size(); i++) {
                var state = evaluation.questions().get(i);
                if (canonicalAnswers.containsKey(state.question().key())) through = i;
            }
            for (int i = 0; i < evaluation.questions().size() && (!bookmark || i <= through); i++) {
                var state = evaluation.questions().get(i);
                if (state.visible() && !state.question().fixed())
                    path.add(List.of(state.question().key(), state.question().control().name()));
            }
            if (!canonicalAnswers.keySet().containsAll(answers.keySet()))
                throw new UnavailableConfiguration("저장한 질문 또는 선택값을 지금 구성에서 사용할 수 없습니다.");
            if (bookmark) {
                if (answers.isEmpty()) {
                    throw new UnavailableConfiguration("완료한 질문의 답변을 확인해 주세요.");
                }
                price = null;
            } else {
                if (!evaluation.complete()) throw new UnavailableConfiguration("제품 질문을 모두 완료해 주세요.");
                var quote = prices.evaluate(p.id(), new EvaluateRequest(null, evaluation.answers()));
                if (!quote.complete()) throw new IllegalArgumentException(quote.errors() == null || quote.errors().isEmpty()
                        ? "등록된 가격식을 확인해 주세요." : String.join(" · ", quote.errors()));
                price = quote.total();
            }
        } else {
            if (bookmark) throw new IllegalArgumentException("비규격 제품의 진행 상태만 저장할 수 있습니다.");
            var config = storefront.configure(selection.seriesId(), options, true);
            if (!config.complete() || config.products().stream().noneMatch(candidate -> candidate.id().equals(p.id()))) {
                throw new UnavailableConfiguration("규격 옵션과 제품을 다시 확인해 주세요.");
            }
            config.display().stream().filter(row -> row.answer() != null)
                    .forEach(row -> {
                        String line = row.group().customer() + " · " + row.answer().customer();
                        summary.add(line);
                        byGroup.computeIfAbsent(row.groupId(), ignored -> new ArrayList<>()).add(line);
                    });
        }
        if (!bookmark && (price == null || price.signum() < 0)) {
            throw new UnavailableConfiguration("발주 가능한 단가가 없습니다.");
        }
        var clean = new Selection(p.id(), p.id(),
                p.nonStandard() ? Map.of() : options, canonicalAnswers);
        String priceKey = price == null ? null : price.stripTrailingZeros().toPlainString();
        optionLabels.values().forEach(labels -> labels.sort(String::compareTo));
        var stampData = Arrays.asList(comparableSelection(clean), path, p.nonStandard(), optionLabels, priceKey, category, series,
                widths.stream().sorted().map(BigDecimal::toPlainString).toList());
        String stamp = "configuration:v2:" + hash(encode(stampData));
        String image = customerImage(p.id(), p.assets().stream().filter(asset -> asset.image())
                .map(asset -> asset.id()).findFirst().orElse(null));
        List<String> compact = new ArrayList<>();
        for (var setting : p.summaryOptions()) if ("customer".equals(setting.role())) {
            List<String> lines = byGroup.remove(setting.groupId());
            if (lines != null && setting.visible()) compact.addAll(lines);
        }
        if (p.summaryOptions().stream().noneMatch(rule -> "customer".equals(rule.role()))) compact.addAll(summary);
        else byGroup.values().forEach(compact::addAll);
        return new Item(clean, stamp, p.productName(), image, price, StoreCheckoutPolicy.validProductionDays(p.productionHours()),
                StoreCheckoutPolicy.earliestDate(today(), p.productionHours(), holidays::contains, calendarThrough),
                p.nonStandard(), series, summary, compact, Objects.toString(category, ""), widths.size() == 1 ? widths.iterator().next() : null);
    }

    private static String customerImage(long id, String assetId) {
        return assetId == null ? String.format(Locale.ROOT, "/administration/assets/images/customer-order-example/%02d.webp", id % 22 + 1)
                : "/customer/store-preview/api/assets/" + assetId;
    }

    private static boolean millimetres(String unit) {
        return unit == null || unit.isBlank() || "mm".equalsIgnoreCase(unit.strip()) || "㎜".equals(unit.strip());
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

    public LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }
    public Set<LocalDate> holidays() { return Set.copyOf(holidays); }
    public int balance(long company) { return companies.findById(company).orElseThrow().getPoint(); }

    public Item check(Item previous, long company, boolean bookmark) {
        if (previous == null || previous.selection() == null || previous.stamp() == null ||
                !previous.stamp().matches("(?:configuration:(?:v2:)?)?[a-f0-9]{64}"))
            throw new Changed(false, "저장된 구성을 확인할 수 없습니다. 다시 시도해 주세요.");
        Item current;
        try {
            current = item(previous.selection(), company, bookmark);
        } catch (UnavailableConfiguration invalid) {
            throw new Changed(true, invalid.getMessage());
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException uncertain) {
            // Includes temporary file lookup failures, unreadable settings and unsuccessful price diagnostics.
            throw new Changed(false, Objects.toString(uncertain.getMessage(), "구성을 다시 확인해 주세요."));
        }
        if (current.stamp().equals(previous.stamp())) return current;
        if (!previous.stamp().startsWith("configuration:v2:") && sameLegacyConfiguration(previous, current)) return current;
        throw new Changed(true, changeReason(previous, current));
    }

    private String changeReason(Item previous, Item current) {
        if (previous.price() != null && current.price() != null && previous.price().compareTo(current.price()) != 0) {
            NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);
            return "상품 단가가 " + won.format(previous.price()) + "원에서 " + won.format(current.price()) + "원으로 변경되었습니다.";
        }
        if (previous.category() != null && !previous.category().isBlank() && !previous.category().equals(current.category()))
            return "제품 대분류가 변경되었습니다. 현재 대분류는 " + current.category() + "입니다.";
        if (previous.width() != null && current.width() != null && previous.width().compareTo(current.width()) != 0)
            return "제품 가로 규격이 " + previous.width().stripTrailingZeros().toPlainString() + "mm에서 "
                    + current.width().stripTrailingZeros().toPlainString() + "mm로 변경되었습니다.";
        return "저장한 질문 순서 또는 선택 옵션이 현재 제품 구성과 달라졌습니다.";
    }

    private static Object comparableValue(Object value) {
        return value instanceof Number number ? new BigDecimal(number.toString()).stripTrailingZeros().toPlainString() : value;
    }

    private static Selection comparableSelection(Selection selection) {
        Map<String, Answer> answers = new TreeMap<>();
        if (selection.answers() != null) selection.answers().forEach((key, answer) -> {
            if (answer == null) return;
            Map<String, Object> fields = new TreeMap<>();
            if (answer.fields() != null) answer.fields().forEach((field, value) -> fields.put(field, comparableValue(value)));
            var choices = answer.choices() == null ? List.<String>of() : answer.choices().stream().sorted().toList();
            answers.put(key, new Answer(choices, fields));
        });
        return new Selection(selection.productId(), selection.seriesId(), selection.options() == null ? Map.of() : selection.options(), answers);
    }

    private static final java.util.regex.Pattern NUMERIC_TOKEN = java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}])[-+]?[0-9]+(?:[.][0-9]+)?(?:[eE][-+]?[0-9]+)?(?![\\p{L}\\p{N}])");

    private static List<String> legacyAnswerLabels(List<String> summary) {
        if (summary == null) return List.of();
        return summary.stream().filter(Objects::nonNull).map(line -> {
            int split = line.indexOf(" · ");
            // Old payloads contain the role's display labels, not question IDs. A label-only edit isn't evidence
            // that the answer is invalid. Compare the option contents and normalize numeric display formatting.
            String value = split < 0 ? line : line.substring(split + 3);
            return NUMERIC_TOKEN.matcher(value).replaceAll(match -> {
                String token = match.group();
                if (token.length() > 40) return token;
                try {
                    BigDecimal number = new BigDecimal(token);
                    return Math.abs((long)number.scale()) < 64 ? number.stripTrailingZeros().toPlainString() : token;
                } catch (NumberFormatException invalid) { return token; }
            });
        }).sorted().toList();
    }

    private boolean sameLegacyConfiguration(Item saved, Item current) {
        // The earlier hashes also changed after a help/stock/lead-time edit. Compare saved purchase data instead.
        return saved.custom() == current.custom()
                && Objects.equals(comparableSelection(saved.selection()), comparableSelection(current.selection()))
                && (saved.price() == null ? current.price() == null : current.price() != null && saved.price().compareTo(current.price()) == 0)
                && (saved.summary() == null || legacyAnswerLabels(saved.summary()).equals(legacyAnswerLabels(current.summary())))
                && (saved.category() == null || saved.category().isBlank() || saved.category().equals(current.category()))
                && (saved.series() == null || saved.series().isBlank() || saved.series().equals(current.series()))
                && (saved.width() == null || current.width() != null && saved.width().compareTo(current.width()) == 0);
    }

    public static class Changed extends RuntimeException {
        private final String lineKey;
        private final boolean invalid;
        public Changed(boolean invalid, String message) { this(null, invalid, message); }
        private Changed(String lineKey, boolean invalid, String message) {
            super(message);
            this.lineKey = lineKey;
            this.invalid = invalid;
        }
        public String lineKey() { return lineKey; }
        public boolean invalid() { return invalid; }
    }

    /** A list page can refresh dated metadata without re-evaluating every saved configuration. */
    public Map<Long, CardMetadata> cardMetadata(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 10 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("표시할 제품을 1~10개 지정해 주세요.");
        // Use the same per-product variant IDs as ProductStudioService.detail and the catalog.
        // Legacy rows can have matching definitions even when a mirrored component row is missing.
        record Label(long groupId, String role, String text) {}
        Map<Long, Label> labels = new HashMap<>();
        String labelSql = """
            SELECT v.id, g.id, g.system_role, v.customer_label
              FROM tb_pm_attribute_value v JOIN tb_pm_attribute_group g ON g.id=v.group_id
             WHERE g.system_role IN ('CATEGORY','SERIES')
            """;
        db.query(labelSql, rs -> {
            labels.put(rs.getLong(1), new Label(rs.getLong(2), rs.getString(3), rs.getString(4)));
        });
        String sql = """
            SELECT p.id, p.production_hours, p.non_standard, p.studio_definition_json,
                   (SELECT a.id FROM tb_pm_studio_asset a WHERE a.owner_type='PRODUCT' AND a.owner_id=p.id
                     AND a.content_type LIKE 'image/%' ORDER BY a.created_at, a.id LIMIT 1) AS image_id
              FROM tb_pm_product p
             WHERE p.id IN (:ids) AND p.status='ACTIVE' AND p.studio_definition_json <> ''
            """;
        var byId = new LinkedHashMap<Long, CardMetadata>();
        List<Long> customIds = new ArrayList<>();
        LocalDate base = today();
        new NamedParameterJdbcTemplate(db).query(sql, Map.of("ids", ids), rs -> {
            Variant[] variants;
            try {
                variants = json.readValue(rs.getString("studio_definition_json"), Variant[].class);
            } catch (java.io.IOException invalidDefinition) {
                return;
            }
            if (variants == null) return;
            Map<String, String> names = new HashMap<>();
            for (Variant variant : variants) {
                if (variant == null || variant.groupId() == null || variant.valueIds() == null || variant.valueIds().isEmpty()) continue;
                Label label = labels.get(variant.valueIds().get(0));
                if (label != null && label.groupId() == variant.groupId()) names.put(label.role(), label.text());
            }
            long id = rs.getLong("id");
            BigDecimal days = StoreCheckoutPolicy.validProductionDays(rs.getBigDecimal("production_hours"));
            byId.put(id, new CardMetadata(names.get("CATEGORY"), names.get("SERIES"), days,
                    StoreCheckoutPolicy.earliestDate(base, days, holidays::contains, calendarThrough), customerImage(id, rs.getString("image_id"))));
            if (rs.getBoolean("non_standard")) customIds.add(id);
        });
        if (!customIds.isEmpty()) {
            for (var status : prices.verifiedStatus(customIds)) {
                if (!status.registered() || !status.ready() || status.stale()) byId.remove(status.productId());
            }
        }
        return byId;
    }

    public Map<String, Long> savedCounts(long company) {
        Map<String, Long> counts = new HashMap<>(Map.of("interest", 0L, "bookmark", 0L));
        db.query("SELECT kind, COUNT(*) FROM tb_store_preview_saved WHERE company_id=? GROUP BY kind",
                rs -> { counts.put(rs.getString(1), rs.getLong(2)); }, company);
        return counts;
    }

    private static final String SAVED_SELECT =
            "SELECT id, kind, note, payload, created_at FROM tb_store_preview_saved WHERE company_id=?";

    private Saved readSaved(ResultSet rs, int index) throws SQLException {
        Item item;
        try {
            item = decode(rs.getString("payload"));
        } catch (IllegalStateException error) {
            item = null;
        }
        return new Saved(rs.getLong("id"), rs.getString("kind"), rs.getString("note"), item,
                rs.getTimestamp("created_at").toInstant().toString());
    }

    public List<Saved> saved(long company, String kind) {
        if (kind == null) return db.query(SAVED_SELECT + " ORDER BY id DESC", this::readSaved, company);
        if (!Set.of("interest", "bookmark").contains(kind))
            throw new IllegalArgumentException("저장한 목록 종류를 확인해 주세요.");
        return db.query(SAVED_SELECT + " AND kind=? ORDER BY id DESC", this::readSaved, company, kind);
    }

    public Saved saved(long company, long id) {
        return db.query(SAVED_SELECT + " AND id=?", this::readSaved, company, id).stream()
                .findFirst().orElseThrow(() -> new NoSuchElementException("저장한 항목을 찾을 수 없습니다."));
    }

    public void save(long company, String kind, String note, Item previous) {
        if (kind == null || !Set.of("interest", "bookmark").contains(kind) || note == null || note.length() > 300) {
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

    public record Cleanup(boolean removed, String status, String kind) {}

    public Cleanup removeInvalid(long company, long id, String expectedStamp, String expectedKind) {
        if (expectedStamp == null || expectedStamp.isBlank()) return new Cleanup(false, "unverified", "");
        record Stored(String kind, String payload) {}
        var rows = db.query("SELECT kind,payload FROM tb_store_preview_saved WHERE company_id=? AND id=?",
                (rs, i) -> new Stored(rs.getString(1), rs.getString(2)), company, id);
        if (rows.isEmpty()) return new Cleanup(false, "missing", "");
        var row = rows.get(0);
        if (!row.kind().equals(expectedKind)) return new Cleanup(false, "replaced", row.kind());
        Item item;
        try {
            item = decode(row.payload());
        } catch (IllegalStateException unreadable) {
            return new Cleanup(false, "unverified", row.kind());
        }
        if (item == null || item.stamp() == null) return new Cleanup(false, "unverified", row.kind());
        if (!expectedStamp.equals(item.stamp())) return new Cleanup(false, "replaced", row.kind());
        try {
            check(item, company, "bookmark".equals(row.kind()));
            return new Cleanup(false, "valid", row.kind());
        } catch (Changed changed) {
            if (!changed.invalid()) return new Cleanup(false, "unverified", row.kind());
        }
        // Compare the stored bytes as well as the company and record ID. A concurrent replacement stays intact.
        int count = db.update("DELETE FROM tb_store_preview_saved WHERE company_id=? AND id=? AND BINARY payload=BINARY ?",
                company, id, row.payload());
        return count == 1 ? new Cleanup(true, "removed", row.kind()) : new Cleanup(false, "replaced", row.kind());
    }

    public Result checkout(long company, Checkout request) {
        if (request == null || request.lines() == null || request.lines().isEmpty() || request.lines().size() > 100) {
            throw new IllegalArgumentException("발주할 품목을 1~100개 선택해 주세요.");
        }
        List<ResultLine> details = new ArrayList<>();
        List<StoreCheckoutPolicy.Line> order = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Map<StoreCheckoutPolicy.DeliveryGroup, String> firstKeys = new HashMap<>();
        var items = new LinkedHashMap<String, Item>();
        var groupByLine = new HashMap<String, StoreCheckoutPolicy.DeliveryGroup>();
        var groupedProducts = new LinkedHashMap<StoreCheckoutPolicy.DeliveryGroup, List<StoreShipping.Product>>();
        var addresses = new HashMap<String, Address>();
        var direct = companyAddress(company);
        for (Line line : request.lines()) {
            if (line == null || line.key() == null || line.key().isBlank() || !keys.add(line.key()))
                throw new IllegalArgumentException("발주할 품목의 식별자가 누락되거나 중복되었습니다. 품목을 다시 선택해 주세요.");
            Item p;
            try {
                p = check(line.item(), company, false);
            } catch (Changed changed) {
                String name = line.item() == null ? "저장된 품목" : Objects.toString(line.item().name(), "저장된 품목");
                throw new Changed(line.key(), changed.invalid(), name + " · " + changed.getMessage());
            }
            Address address = line.methodId() == 1 ? direct : line.address();
            if (line.quantity() < 1 || line.quantity() > 99999
                    || address == null || !address.valid())
                throw new IllegalArgumentException(p.name() + "의 수량과 배송 주소를 확인해 주세요.");
            if (p.earliest() == null || (line.date() != null && line.date().isAfter(calendarThrough)))
                throw new IllegalArgumentException(p.name() + "의 희망일을 확인할 수 없습니다. 공휴일 일정은 " + calendarThrough + "까지 등록되어 있습니다.");
            if (line.date() == null || line.date().isBefore(p.earliest()) || !StoreCheckoutPolicy.isBusinessDay(line.date(), holidays::contains))
                throw new IllegalArgumentException(p.name() + "의 배송 희망일을 확인해 주세요.");
            var group = new StoreCheckoutPolicy.DeliveryGroup(line.date(), line.methodId(), StoreCheckoutPolicy.addressKey(address.zip(), address.baseAddress(), address.detail()));
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
            String firstKey = firstKeys.computeIfAbsent(group, ignored -> line.key());
            boolean first = line.key().equals(firstKey);
            pending |= quote.delivery() == null || quote.packing() == null;
            var fees = new StoreCheckoutPolicy.Fees(BigDecimal.valueOf(Objects.requireNonNullElse(quote.delivery(), 0)),
                    BigDecimal.valueOf(Objects.requireNonNullElse(quote.packing(), 0)));
            order.add(new StoreCheckoutPolicy.Line(p.price(), line.quantity(), group, fees));
            details.add(new ResultLine(line.key(), p, line.quantity(), address, line.contacts(), line.date(), method.name(),
                    first || quote.delivery() == null ? quote.delivery() : Integer.valueOf(0),
                    first || quote.packing() == null ? quote.packing() : Integer.valueOf(0),
                    first ? quote.reason() : "같은 묶음의 첫 품목에 합산 · " + quote.reason(), firstKey));
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
