package com.dev.HiddenBATHAuto.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiddenbath.rag.upload")
public class RagUploadProperties {

    /**
     * RAG 원본 파일 저장 루트입니다.
     * 운영 서버에서는 /home/ubuntu/hiddenbath-upload 같은 절대경로로 잡는 것을 권장합니다.
     */
    private String baseDir = System.getProperty("user.home") + "/hiddenbath-rag-upload";

    /**
     * WebMvcConfig에서 /administration/upload/** 같은 정적 매핑을 사용하는 경우 하위 폴더명과 맞춥니다.
     */
    private String assetSubDir = "rag-assets";

    /**
     * DB에 저장할 접근 URL prefix입니다. 실제 정적 리소스 매핑과 맞춰 주세요.
     */
    private String publicUrlPrefix = "/administration/upload/rag-assets";

    private long maxFileSizeBytes = 50L * 1024L * 1024L;

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getAssetSubDir() {
        return assetSubDir;
    }

    public void setAssetSubDir(String assetSubDir) {
        this.assetSubDir = assetSubDir;
    }

    public String getPublicUrlPrefix() {
        return publicUrlPrefix;
    }

    public void setPublicUrlPrefix(String publicUrlPrefix) {
        this.publicUrlPrefix = publicUrlPrefix;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
