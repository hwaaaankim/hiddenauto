package com.dev.HiddenBATHAuto.dto.analytics;
public enum AsAnalyticsMetric {
 AS_COUNT("asCount","AS 신청건수","건","AS 신청 1개를 1건으로 집계합니다. 수리 부품 수량이 아닙니다."),
 COMPLETED_COUNT("completedCount","완료건수","건","조회 기간에 신청된 AS 중 현재 완료 상태입니다. 완료일 기준 실적이 아닙니다."),
 PENDING_COUNT("pendingCount","미완료건수","건","현재 요청됨·진행중·상태 미등록인 AS입니다. 취소는 제외합니다."),
 CANCELED_COUNT("canceledCount","취소건수","건","취소 상태를 필터에 포함한 경우에만 집계합니다."),
 PAID_COUNT("paidCount","유상건수","건","저장 비용이 0원 초과인 신청입니다."),
 ZERO_COUNT("zeroCount","무상·미정 건수","건","저장 비용 0원. 무상 확정 여부는 별도 저장되지 않아 구분할 수 없습니다."),
 UNKNOWN_COUNT("unknownCount","비용 확인필요 건수","건","저장 비용이 없거나 음수인 신청입니다. 유상·무상으로 추정하지 않습니다."),
 COLLECTED_COUNT("collectedCount","유상 수납완료 건수","건","유상 중 현재 수납완료로 표시된 건수입니다."),
 UNCOLLECTED_COUNT("uncollectedCount","유상 미수납 건수","건","유상 중 현재 수납완료 표시가 없는 건수입니다."),
 COST("cost","AS 비용 합계","원","저장된 AS 비용의 합계입니다. 조회 기간에 신청된 AS의 현재 비용이며 매출액·실제 입금액이 아닙니다."),
 COLLECTED_COST("collectedCost","유상 수납완료 비용","원","유상·수납완료로 표시된 신청의 저장 비용 합계입니다. 실제 입금일·분할수납액은 저장되지 않습니다."),
 UNCOLLECTED_COST("uncollectedCost","유상 미수납 비용","원","유상·미수납으로 표시된 신청의 저장 비용 합계입니다.");
 private final String key,label,unit,help;
 AsAnalyticsMetric(String key,String label,String unit,String help){this.key=key;this.label=label;this.unit=unit;this.help=help;}
 public String key(){return key;}public String label(){return label;}public String unit(){return unit;}public String help(){return help;}
 public AnalyticsQueryResponse.ColumnMeta column(){return AnalyticsQueryResponse.ColumnMeta.builder().key(key).label(label).unit(unit).help(help).build();}
 public static AsAnalyticsMetric forKey(String key){for(var m:values())if(m.key.equals(key))return m;return null;}
}
