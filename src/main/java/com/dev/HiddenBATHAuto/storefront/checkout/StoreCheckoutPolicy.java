package com.dev.HiddenBATHAuto.storefront.checkout;

import com.dev.HiddenBATHAuto.utils.KoreanAdministrativeRegionNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class StoreCheckoutPolicy {
    private StoreCheckoutPolicy() {}

    public static LocalDate earliestDate(LocalDate today, BigDecimal productionDays,
            Predicate<LocalDate> isHoliday) {
        return earliestDate(today, productionDays, isHoliday, LocalDate.MAX);
    }

    static BigDecimal validProductionDays(BigDecimal days) {
        return days == null || days.signum() < 0 || days.compareTo(BigDecimal.valueOf(1_000_000)) > 0
                || days.stripTrailingZeros().scale() > 3 ? null : days;
    }

    static LocalDate earliestDate(LocalDate today, BigDecimal productionDays, Predicate<LocalDate> isHoliday,
            LocalDate calendarThrough) {
        BigDecimal days = validProductionDays(productionDays);
        int remaining = days == null ? 2 : days.setScale(0, RoundingMode.CEILING).intValueExact();
        LocalDate date = today;
        while (!date.isAfter(calendarThrough)) {
            if (isBusinessDay(date, isHoliday)) {
                if (remaining > 0 && !date.equals(today)) remaining--;
                if (remaining == 0) return date;
            }
            if (date.equals(calendarThrough)) break;
            date = date.plusDays(1);
        }
        return null;
    }

    public static boolean isBusinessDay(LocalDate date, Predicate<LocalDate> isHoliday) {
        return date.getDayOfWeek().getValue() < 6 && !isHoliday.test(date);
    }

    static String addressKey(String zip, String baseAddress, String detail) {
        // Daum and the address book may use different official abbreviations for the same province.
        String[] parts = baseAddress.strip().replaceAll("(?U)\\s+", " ").split(" ", 2);
        String base = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(parts[0])
                + (parts.length == 2 ? " " + parts[1] : "");
        return zip + "|" + base.length() + "|" + base + "|" + detail;
    }

    public record DeliveryGroup(LocalDate date, long methodId, String address) {}

    public record Fees(BigDecimal delivery, BigDecimal packing) {
        public Fees {
            delivery = nonnegative(delivery);
            packing = nonnegative(packing);
        }
    }

    public record Line(BigDecimal unitPrice, int quantity, DeliveryGroup group, Fees fees) {
        public Line {
            unitPrice = nonnegative(unitPrice);
            if (quantity <= 0) {
                throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
            }
            Objects.requireNonNull(group);
            Objects.requireNonNull(group.date());
            Objects.requireNonNull(fees);
        }
    }

    public record Totals(BigDecimal products, BigDecimal delivery, BigDecimal packing,
            BigDecimal points, BigDecimal payable, Map<DeliveryGroup, Fees> deliveryGroups) {}

    public static Totals calculate(List<Line> lines, BigDecimal requestedPoints, BigDecimal companyBalance) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("발주할 품목을 선택해 주세요.");
        }

        BigDecimal products = BigDecimal.ZERO;
        Map<DeliveryGroup, Fees> groups = new LinkedHashMap<>();
        for (Line line : lines) {
            products = products.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            Fees previous = groups.putIfAbsent(line.group(), line.fees());
            if (previous != null && (previous.delivery().compareTo(line.fees().delivery()) != 0
                    || previous.packing().compareTo(line.fees().packing()) != 0)) {
                throw new IllegalArgumentException("같은 배송수단의 비용이 변경되었습니다. 새로 확인해 주세요.");
            }
        }

        BigDecimal points = nonnegative(requestedPoints);
        if (points.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("포인트는 1포인트 단위로 입력해 주세요.");
        if (points.compareTo(companyBalance.max(BigDecimal.ZERO)) > 0 || points.compareTo(products) > 0) {
            throw new IllegalArgumentException("포인트는 보유 잔액과 상품금액 이내에서 사용해 주세요.");
        }

        BigDecimal delivery = BigDecimal.ZERO;
        BigDecimal packing = BigDecimal.ZERO;
        for (Fees fees : groups.values()) {
            delivery = delivery.add(fees.delivery());
            packing = packing.add(fees.packing());
        }
        return new Totals(products, delivery, packing, points,
                products.subtract(points).add(delivery).add(packing),
                java.util.Collections.unmodifiableMap(groups));
    }

    private static BigDecimal nonnegative(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("금액을 확인해 주세요.");
        }
        return amount;
    }
}
