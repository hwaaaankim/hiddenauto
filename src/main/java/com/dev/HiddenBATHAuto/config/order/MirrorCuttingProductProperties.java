package com.dev.HiddenBATHAuto.config.order;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hiddenbath.order.mirror-cutting")
public class MirrorCuttingProductProperties {

    /**
     * 거울재단용 자동 판정 사용 여부입니다.
     * false여도 관리자가 스위치를 직접 ON 한 주문은 true로 저장됩니다.
     */
    private boolean enabled = true;

    /** 제품명/시리즈/카테고리/옵션 전체 텍스트에서 포함 여부를 검사할 키워드입니다. */
    private List<String> anyTextKeywords = new ArrayList<>();

    /** 제품명에서만 포함 여부를 검사할 키워드입니다. */
    private List<String> productNameKeywords = new ArrayList<>();

    /** 규격 카테고리, 제품시리즈, 생산팀 분류명에서 포함 여부를 검사할 키워드입니다. */
    private List<String> seriesKeywords = new ArrayList<>();

    /** 옵션명/옵션값에서 포함 여부를 검사할 키워드입니다. */
    private List<String> optionKeywords = new ArrayList<>();

    /**
     * 실제 같은 제품으로 볼 대표 제품명 목록입니다.
     * 예: '모던 거울장'을 등록하면 '모던 거울장 1도어 HG'도 ignoreTokens 제거 후 같은 제품으로 판정됩니다.
     */
    private List<String> canonicalProductNames = new ArrayList<>();

    /**
     * 같은 제품 판정 시 제거할 변형 토큰입니다.
     * 예: 1도어, 2도어, HG, HB, 색상명 등.
     */
    private List<String> ignoreTokens = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAnyTextKeywords() {
        return anyTextKeywords;
    }

    public void setAnyTextKeywords(List<String> anyTextKeywords) {
        this.anyTextKeywords = anyTextKeywords == null ? new ArrayList<>() : anyTextKeywords;
    }

    public List<String> getProductNameKeywords() {
        return productNameKeywords;
    }

    public void setProductNameKeywords(List<String> productNameKeywords) {
        this.productNameKeywords = productNameKeywords == null ? new ArrayList<>() : productNameKeywords;
    }

    public List<String> getSeriesKeywords() {
        return seriesKeywords;
    }

    public void setSeriesKeywords(List<String> seriesKeywords) {
        this.seriesKeywords = seriesKeywords == null ? new ArrayList<>() : seriesKeywords;
    }

    public List<String> getOptionKeywords() {
        return optionKeywords;
    }

    public void setOptionKeywords(List<String> optionKeywords) {
        this.optionKeywords = optionKeywords == null ? new ArrayList<>() : optionKeywords;
    }

    public List<String> getCanonicalProductNames() {
        return canonicalProductNames;
    }

    public void setCanonicalProductNames(List<String> canonicalProductNames) {
        this.canonicalProductNames = canonicalProductNames == null ? new ArrayList<>() : canonicalProductNames;
    }

    public List<String> getIgnoreTokens() {
        return ignoreTokens;
    }

    public void setIgnoreTokens(List<String> ignoreTokens) {
        this.ignoreTokens = ignoreTokens == null ? new ArrayList<>() : ignoreTokens;
    }
}
