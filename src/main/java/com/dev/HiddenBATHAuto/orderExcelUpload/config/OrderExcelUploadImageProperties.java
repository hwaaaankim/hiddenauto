package com.dev.HiddenBATHAuto.orderExcelUpload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 엑셀 발주 화면에서 사용하는 이미지 선택/저장 제한입니다.
 *
 * spring.servlet.multipart는 HTTP multipart 자체의 상한이고,
 * 이 설정은 실제 엑셀 발주 이미지에 적용하는 업무 상한입니다.
 * 프론트의 현재용량/최대용량 표시와 서버 검증이 같은 값을 사용합니다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.order-excel-upload.image")
public class OrderExcelUploadImageProperties {

    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 500L * 1024L * 1024L;
    private static final long DEFAULT_MAX_TOTAL_SIZE_BYTES = 1024L * 1024L * 1024L;

    private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
    private long maxTotalSizeBytes = DEFAULT_MAX_TOTAL_SIZE_BYTES;

    public long resolvedMaxFileSizeBytes() {
        return maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    public long resolvedMaxTotalSizeBytes() {
        return maxTotalSizeBytes > 0 ? maxTotalSizeBytes : DEFAULT_MAX_TOTAL_SIZE_BYTES;
    }
}
