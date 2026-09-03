package com.dev.HiddenBATHAuto.config.productmaster;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.product-master.image")
public class ProductMasterImageProperties {

    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int DEFAULT_MAX_FILES_PER_OWNER = 30;

    private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
    private int maxFilesPerOwner = DEFAULT_MAX_FILES_PER_OWNER;

    public long resolvedMaxFileSizeBytes() {
        return maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    public int resolvedMaxFilesPerOwner() {
        return maxFilesPerOwner > 0 ? maxFilesPerOwner : DEFAULT_MAX_FILES_PER_OWNER;
    }
}
