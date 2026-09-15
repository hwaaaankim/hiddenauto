package com.dev.HiddenBATHAuto.service.productmaster;

import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeGroupRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeValueRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductMasterCodeService {

  private static final char[] BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
  private static final int MAX_GENERATION_ATTEMPTS = 100;

  private final SecureRandom secureRandom = new SecureRandom();
  private final ProductAttributeGroupRepository groupRepository;
  private final ProductAttributeValueRepository valueRepository;
  private final ProductMasterRepository productRepository;

  public String newGroupCode() {
    return uniqueRandomCode("G", 5, groupRepository::existsByGroupCode);
  }

  public String newValueCode() {
    return uniqueRandomCode("V", 7, valueRepository::existsByValueCode);
  }

  public String newCatalogCode(String productCode) {
    for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
      String body = randomBase36(4);
      String checksum =
          sha256Hex(productCode + ":" + body).substring(0, 2).toUpperCase(Locale.ROOT);
      String code = "HB-" + body + "-" + checksum;
      if (!productRepository.existsByCatalogCode(code)) {
        return code;
      }
    }
    throw new IllegalStateException("카탈로그 코드를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  }

  public String configurationHash(String productCode) {
    return sha256Hex(productCode);
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
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte element : digest) {
        hex.append(String.format("%02x", element & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
    }
  }
}
