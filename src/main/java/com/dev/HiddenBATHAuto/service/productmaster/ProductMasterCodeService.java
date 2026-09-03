package com.dev.HiddenBATHAuto.service.productmaster;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductDimensionType;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeInputType;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeGroupRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeValueRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductConfigurationRuleRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductDynamicPriceRuleRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductPriceMatrixRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductMasterCodeService {

    private static final char[] BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int MAX_GENERATION_ATTEMPTS = 100;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ProductAttributeGroupRepository groupRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductMasterRepository productRepository;
    private final ProductConfigurationRuleRepository configurationRuleRepository;
    private final ProductPriceMatrixRepository priceMatrixRepository;
    private final ProductDynamicPriceRuleRepository dynamicPriceRuleRepository;

    public String newGroupCode() {
        return uniqueRandomCode("G", 5, groupRepository::existsByGroupCode);
    }

    public String newValueCode() {
        return uniqueRandomCode("V", 7, valueRepository::existsByValueCode);
    }

    public String newConfigurationRuleCode() {
        return uniqueRandomCode("R", 7, configurationRuleRepository::existsByRuleCode);
    }

    public String newPriceMatrixCode() {
        return uniqueRandomCode("M", 7, priceMatrixRepository::existsByMatrixCode);
    }

    public String newDynamicPriceRuleCode() {
        return uniqueRandomCode("P", 7, dynamicPriceRuleRepository::existsByPriceRuleCode);
    }

    public String newCatalogCode(String productCode) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String body = randomBase36(4);
            String checksum = sha256Hex(productCode + ":" + body)
                    .substring(0, 2)
                    .toUpperCase(Locale.ROOT);
            String code = "HB-" + body + "-" + checksum;
            if (!productRepository.existsByCatalogCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("카탈로그 코드를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    public String buildProductCode(List<IdentityPart> parts) {
        String body = parts.stream()
                .sorted(Comparator
                        .comparing(IdentityPart::groupCode)
                        .thenComparing(part -> nullSafeText(part.valueCode()))
                        .thenComparing(part -> nullSafe(part.widthMm()))
                        .thenComparing(part -> nullSafe(part.depthMm()))
                        .thenComparing(part -> nullSafe(part.heightMm())))
                .map(this::encodePart)
                .collect(Collectors.joining("|"));

        String code = "PM1|" + body;
        if (code.length() > 700) {
            throw new IllegalArgumentException("핵심 구성요소가 너무 많아 제품 코드를 생성할 수 없습니다. 핵심 구성을 정리해 주세요.");
        }
        return code;
    }

    public String configurationHash(String productCode) {
        return sha256Hex(productCode);
    }

    private String encodePart(IdentityPart part) {
        StringBuilder encoded = new StringBuilder()
                .append(part.groupCode())
                .append('=');

        if (part.inputType() == ProductAttributeInputType.NUMBER) {
            encoded.append("N:").append(part.numericValue().stripTrailingZeros().toPlainString());
            return encoded.toString();
        }
        if (part.inputType() == ProductAttributeInputType.TEXT) {
            encoded.append("T:").append(sha256Hex(nullSafeText(part.textValue())).substring(0, 16));
            return encoded.toString();
        }
        encoded.append(part.valueCode());

        if (part.dimensionType() == ProductDimensionType.WIDTH_HEIGHT) {
            encoded.append('@')
                    .append(part.widthMm())
                    .append('X')
                    .append(part.heightMm());
        } else if (part.dimensionType() == ProductDimensionType.WIDTH_DEPTH_HEIGHT) {
            encoded.append('@')
                    .append(part.widthMm())
                    .append('X')
                    .append(part.depthMm())
                    .append('X')
                    .append(part.heightMm());
        } else if (part.dimensionType() == ProductDimensionType.CUSTOM) {
            encoded.append("@CUSTOM");
        }
        return encoded.toString();
    }

    private String uniqueRandomCode(String prefix, int randomLength, Predicate<String> exists) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = prefix + randomBase36(randomLength);
            if (!exists.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("고유 코드를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private String randomBase36(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(BASE36[secureRandom.nextInt(BASE36.length)]);
        }
        return result.toString();
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte element : digest) {
                hex.append(String.format("%02x", element & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private static int nullSafe(Integer value) {
        return value == null ? -1 : value;
    }

    private static String nullSafeText(String value) {
        return value == null ? "" : value;
    }

    public record IdentityPart(
            String groupCode,
            String valueCode,
            ProductAttributeInputType inputType,
            ProductDimensionType dimensionType,
            Integer widthMm,
            Integer depthMm,
            Integer heightMm,
            java.math.BigDecimal numericValue,
            String textValue
    ) {
    }
}
