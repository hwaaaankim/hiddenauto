package com.dev.HiddenBATHAuto.service.analytics;

import java.io.IOException;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dev.HiddenBATHAuto.dto.analytics.*;
import com.dev.HiddenBATHAuto.repository.analytics.AsAnalyticsRepository;

@Service @Transactional(readOnly=true)
public class AsAnalyticsService {
 private final AsAnalyticsRepository repository;
 private final Map<String,List<String>> catalog;
 private static final List<String> DIMENSIONS=List.of("COMPANY","CATEGORY","REASON","STATUS","CHARGE","PAYMENT");
 private static final String BASIS="신청일 기준 · AS 신청 1개 = 1건 · 완료·비용·수납은 현재 저장 상태 · 0원은 무상·미정 · 입금일·분할수납액 구분 불가 · 선택 사유 기준이며 자유입력 상세 설명을 자동 추정하지 않음";
 public AsAnalyticsService(AsAnalyticsRepository repository,ObjectMapper mapper) throws IOException {
  this.repository=repository;
  try(var stream=new ClassPathResource("static/front/as-reason-catalog.js").getInputStream()){
   String source=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   catalog=mapper.readValue(source.substring(source.indexOf('{'),source.lastIndexOf('}')+1),new TypeReference<LinkedHashMap<String,List<String>>>(){});
  }
 }
 public Map<String,List<String>> catalog(){return catalog;}
 private String text(String s){return s==null?"":s.trim();}
 private ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
 private String canonical(String s){return text(s).replace("스크레치","스크래치").replaceAll("\\s+", " ").replace("개폐불량","개폐 불량").replace("댐퍼불량","댐퍼 불량");}
 private record Atom(AsAnalyticsRepository.Entry entry,String category,String reason,boolean classified) {}
 private Atom atom(AsAnalyticsRepository.Entry entry){
  String subject=text(entry.getSubject());int split=subject.indexOf(" - ");
  if(split>0){String category=subject.substring(0,split).trim(),reason=subject.substring(split+3).trim();
   if(catalog.containsKey(category)&&!reason.isEmpty())return new Atom(entry,category,canonical(reason),catalog.get(category).stream().map(this::canonical).anyMatch(canonical(reason)::equals));
  }
  return new Atom(entry,"미분류",subject.isEmpty()?"사유 미등록":"기존 사유: "+subject,false);
 }
 private String status(Atom a){return a.entry.getStatus()==null?"UNKNOWN":a.entry.getStatus().name();}
 private String charge(Atom a){Integer p=a.entry.getPrice();return p==null||p<0?"UNKNOWN":p==0?"ZERO":"PAID";}
 private String payment(Atom a){return !charge(a).equals("PAID")?"NOT_APPLICABLE":Boolean.TRUE.equals(a.entry.getPaymentCollected())?"COLLECTED":"UNCOLLECTED";}
 private String dimension(String key,Atom a){return switch(key){
  case "COMPANY"->a.entry.getCompanyId()==null?"업체 미지정":(text(a.entry.getCompanyName()).isEmpty()?"업체명 미등록":text(a.entry.getCompanyName()))+" (#"+a.entry.getCompanyId()+")";
  case "CATEGORY"->a.category;case "REASON"->a.reason;case "STATUS"->statusLabel(status(a));case "CHARGE"->chargeLabel(charge(a));case "PAYMENT"->paymentLabel(payment(a));default->throw bad("지원하지 않는 행 기준입니다.");};}
 private String statusLabel(String v){return switch(v){case "REQUESTED"->"요청됨";case "IN_PROGRESS"->"진행중";case "COMPLETED"->"완료";case "CANCELED"->"취소";default->"상태 미등록";};}
 private String chargeLabel(String v){return switch(v){case "PAID"->"유상";case "ZERO"->"무상·미정 (0원)";default->"비용 확인필요";};}
 private String paymentLabel(String v){return switch(v){case "COLLECTED"->"유상 수납완료";case "UNCOLLECTED"->"유상 미수납";default->"수납 대상 아님·미정";};}
 private String dimensionLabel(String v){return switch(v){case "COMPANY"->"업체";case "CATEGORY"->"대분류";case "REASON"->"선택 사유";case "STATUS"->"처리 상태";case "CHARGE"->"비용 구분";case "PAYMENT"->"수납 상태";default->v;};}
 private void option(String value,List<String> allowed,String label){if(value==null||!allowed.contains(value))throw bad(label+"을 확인해 주세요.");}
 private List<String> dimensions(AsAnalyticsRequest r){var result=new ArrayList<String>();result.add(r.getPrimaryY());result.addAll(r.getDimensions());return result;}
 private void validate(AsAnalyticsRequest r,boolean export){
  if(r==null)throw bad("AS 조회 조건이 필요합니다.");
  if(r.getFromDate()!=null&&r.getToDate()!=null&&r.getFromDate().isAfter(r.getToDate()))throw bad("신청 시작일은 종료일보다 늦을 수 없습니다.");
  for(var d:new LocalDate[]{r.getFromDate(),r.getToDate()})if(d!=null&&(d.getYear()<1000||d.getYear()>9998))throw bad("유효한 신청일을 입력해 주세요.");
  option(r.getPrimaryY(),DIMENSIONS,"행 기준");
  if(r.getDimensions()==null||r.getDimensions().size()>2||r.getDimensions().stream().anyMatch(Objects::isNull)||new HashSet<>(dimensions(r)).size()!=dimensions(r).size()||!DIMENSIONS.containsAll(r.getDimensions()))throw bad("보조 항목은 행 기준과 중복 없이 최대 2개입니다.");
  option(r.getTimeGrouping(),List.of("NONE","DAY","WEEK","MONTH"),"기간 묶기");
  if(!r.getTimeGrouping().equals("NONE")&&!r.getDimensions().isEmpty())throw bad("기간 추이와 보조 행 항목은 함께 사용할 수 없습니다.");
  option(r.getCharge(),List.of("ALL","PAID","ZERO","UNKNOWN"),"비용 구분");option(r.getPayment(),List.of("ALL","COLLECTED","UNCOLLECTED","NOT_APPLICABLE"),"수납 상태");
  if(r.getStatuses()==null||r.getStatuses().isEmpty()||r.getStatuses().stream().anyMatch(Objects::isNull)||!List.of("REQUESTED","IN_PROGRESS","COMPLETED","CANCELED","UNKNOWN").containsAll(r.getStatuses()))throw bad("처리 상태를 하나 이상 선택해 주세요.");
  if(text(r.getCompanyKeyword()).length()>100||r.getCategory()==null||r.getReason()==null||r.getCategory().length()>255||r.getReason().length()>300)throw bad("검색어와 필터를 확인해 주세요.");
  if(r.getMetrics()==null||r.getMetrics().isEmpty()||new HashSet<>(r.getMetrics()).size()!=r.getMetrics().size())throw bad("표 지표를 중복 없이 하나 이상 선택해 주세요.");
  try{for(String m:r.getMetrics())AsAnalyticsMetric.valueOf(m);if(r.getChartMetrics()==null||r.getChartMetrics().isEmpty()||r.getChartMetrics().size()>4||new HashSet<>(r.getChartMetrics()).size()!=r.getChartMetrics().size()||!r.getMetrics().containsAll(r.getChartMetrics()))throw new IllegalArgumentException();}catch(Exception e){throw bad("그래프는 표에 추가한 지표 중 최대 4개를 선택해 주세요.");}
  option(r.getChartType(),List.of("BAR","HORIZONTAL","LINE"),"그래프 형태");option(r.getSortDir(),List.of("asc","desc"),"정렬 방향");
  if(r.getChartLimit()<0||r.getChartLimit()>100||r.getChartLimit()==0&&r.getTimeGrouping().equals("NONE"))throw bad("비교 그래프는 1~100개, 기간 추이는 0(전체)~100개입니다.");
  if(r.getPage()<0||!export&&(r.getSize()<1||r.getSize()>500))throw bad("페이지와 조회 크기를 확인해 주세요.");
  var keys=columns(r).stream().map(AnalyticsQueryResponse.ColumnMeta::getKey).toList();if("rank".equals(r.getSortKey())||!keys.contains(r.getSortKey()))throw bad("현재 표에 있는 항목으로 정렬해 주세요.");
 }
 private List<Atom> load(AsAnalyticsRequest r){
  List<Atom> result=new ArrayList<>();Set<Long> ids=new HashSet<>();
  for(var e:repository.findForAnalytics(r.getFromDate()==null?null:r.getFromDate().atStartOfDay(),r.getToDate()==null?null:r.getToDate().plusDays(1).atStartOfDay())){
   if(e.getId()==null||e.getRequestedAt()==null||!ids.add(e.getId()))continue;
   var a=atom(e);if(r.getFromDate()!=null&&e.getRequestedAt().toLocalDate().isBefore(r.getFromDate())||r.getToDate()!=null&&e.getRequestedAt().toLocalDate().isAfter(r.getToDate()))continue;
   if(!r.getStatuses().contains(status(a))||!r.getCategory().equals("ALL")&&!r.getCategory().equals(a.category)||!r.getReason().equals("ALL")&&!canonical(r.getReason()).equals(a.reason)||!r.getCharge().equals("ALL")&&!r.getCharge().equals(charge(a))||!r.getPayment().equals("ALL")&&!r.getPayment().equals(payment(a)))continue;
   String company=dimension("COMPANY",a).toLowerCase(Locale.ROOT);if(!company.contains(text(r.getCompanyKeyword()).toLowerCase(Locale.ROOT)))continue;result.add(a);
  }return result;
 }
 private Map<String,Long> totals(List<Atom> atoms){Map<String,Long> m=new LinkedHashMap<>();for(var metric:AsAnalyticsMetric.values())m.put(metric.key(),0L);
  for(var a:atoms){inc(m,"asCount",1);String s=status(a),c=charge(a),p=payment(a);if(s.equals("COMPLETED"))inc(m,"completedCount",1);else if(s.equals("CANCELED"))inc(m,"canceledCount",1);else inc(m,"pendingCount",1);
   if(a.entry.getPrice()!=null)inc(m,"cost",a.entry.getPrice());
   if(c.equals("PAID")){inc(m,"paidCount",1);boolean paid=p.equals("COLLECTED");inc(m,paid?"collectedCount":"uncollectedCount",1);inc(m,paid?"collectedCost":"uncollectedCost",a.entry.getPrice());}
   else inc(m,c.equals("ZERO")?"zeroCount":"unknownCount",1);
  }return m;}
 private void inc(Map<String,Long> m,String k,long v){m.put(k,Math.addExact(m.get(k),v));}
 private LocalDate period(LocalDate date,String grouping){return switch(grouping){case "WEEK"->date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));case "MONTH"->date.withDayOfMonth(1);default->date;};}
 private LocalDate next(LocalDate date,String grouping){return switch(grouping){case "WEEK"->date.plusWeeks(1);case "MONTH"->date.plusMonths(1);default->date.plusDays(1);};}
 private String periodLabel(LocalDate date,String grouping){var p=period(date,grouping);return grouping.equals("MONTH")?p.toString().substring(0,7):p.toString();}
 private List<Map<String,Object>> rows(List<Atom> atoms,AsAnalyticsRequest r){
  Map<List<String>,List<Atom>> groups=new LinkedHashMap<>();boolean trend=!r.getTimeGrouping().equals("NONE");
  for(var a:atoms){var key=trend?List.of(periodLabel(a.entry.getRequestedAt().toLocalDate(),r.getTimeGrouping())):dimensions(r).stream().map(d->dimension(d,a)).toList();groups.computeIfAbsent(key,k->new ArrayList<>()).add(a);}
  if(trend){LocalDate min=r.getFromDate()==null?atoms.stream().map(a->a.entry.getRequestedAt().toLocalDate()).min(Comparator.naturalOrder()).orElse(null):r.getFromDate(),max=r.getToDate()==null?atoms.stream().map(a->a.entry.getRequestedAt().toLocalDate()).max(Comparator.naturalOrder()).orElse(null):r.getToDate();
   if(min!=null&&max!=null){int count=0;for(var d=period(min,r.getTimeGrouping());!d.isAfter(max);d=next(d,r.getTimeGrouping())){if(++count>5000)throw bad("기간이 길어 5,000개 구간을 넘었습니다. 월별로 묶거나 기간을 줄여 주세요.");groups.putIfAbsent(List.of(periodLabel(d,r.getTimeGrouping())),List.of());}}
  }
  List<Map<String,Object>> rows=new ArrayList<>();for(var e:groups.entrySet()){
   Map<String,Object> row=new LinkedHashMap<>(totals(e.getValue()));row.put("groupValues",trend?List.of():e.getKey());
   if(trend)row.put("period",e.getKey().get(0));else for(int i=0;i<dimensions(r).size();i++)row.put("dim"+i,e.getKey().get(i));row.put("label",String.join(" / ",e.getKey()));rows.add(row);
  }
  Comparator<Map<String,Object>> cmp=AsAnalyticsMetric.forKey(r.getSortKey())!=null?Comparator.comparingLong(a->((Number)a.get(r.getSortKey())).longValue()):Comparator.comparing(a->String.valueOf(a.get(r.getSortKey())));
  if(r.getSortDir().equals("desc"))cmp=cmp.reversed();rows.sort(cmp.thenComparing(a->String.valueOf(a.get("label"))));for(int i=0;i<rows.size();i++)rows.get(i).put("rank",i+1);return rows;
 }
 private List<AnalyticsQueryResponse.ColumnMeta> columns(AsAnalyticsRequest r){var c=new ArrayList<AnalyticsQueryResponse.ColumnMeta>();
  c.add(AnalyticsQueryResponse.ColumnMeta.builder().key("rank").label("순위").build());
  if(!r.getTimeGrouping().equals("NONE"))c.add(AnalyticsQueryResponse.ColumnMeta.builder().key("period").label(r.getTimeGrouping().equals("WEEK")?"주 시작일":"신청 기간").help("신청일 기준이며 주별은 월요일 시작입니다.").build());
  else for(int i=0;i<dimensions(r).size();i++)c.add(AnalyticsQueryResponse.ColumnMeta.builder().key("dim"+i).label(dimensionLabel(dimensions(r).get(i))).build());
  for(var m:r.getMetrics())c.add(AsAnalyticsMetric.valueOf(m).column());return c;
 }
 public AsAnalyticsResponse query(AsAnalyticsRequest r,boolean export){
  validate(r,export);var atoms=load(r);var all=rows(atoms,r);var t=totals(atoms);List<String> warnings=new ArrayList<>();
  long missing=atoms.stream().filter(a->!a.classified).count();if(missing>0)warnings.add(String.format(Locale.US,"선택형 사유와 정확히 연결되지 않는 %,d건이 있습니다. 미분류 또는 저장된 기존 사유를 그대로 포함했습니다.",missing));
  if(t.get("unknownCount")>0)warnings.add("비용이 없거나 음수인 AS가 있습니다. 비용 확인필요 건수로 구분하며 음수 금액은 저장 부호를 유지합니다.");
  if(t.get("zeroCount")>0)warnings.add("0원은 무상과 금액 미정을 구분할 수 없어 ‘무상·미정’으로 합산했습니다.");
  long invalidFlags=atoms.stream().filter(a->!charge(a).equals("PAID")&&Boolean.TRUE.equals(a.entry.getPaymentCollected())).count();if(invalidFlags>0)warnings.add(String.format(Locale.US,"유상 금액 없이 수납완료로 표시된 %,d건은 유상 수납완료 비용에 포함하지 않았습니다.",invalidFlags));
  int start=export?0:(int)Math.min((long)r.getPage()*r.getSize(),all.size()),end=export?all.size():Math.min(start+r.getSize(),all.size());
  var meta=AnalyticsQueryResponse.Meta.builder().periodText("신청일: "+(r.getFromDate()==null?"과거 전체":r.getFromDate())+" ~ "+(r.getToDate()==null?"미래 전체":r.getToDate()))
   .basisText(BASIS).yAxisText("행 기준: "+(!r.getTimeGrouping().equals("NONE")?"신청 기간":dimensions(r).stream().map(this::dimensionLabel).collect(Collectors.joining(" / ")))).xAxisText("지표: "+r.getMetrics().stream().map(m->AsAnalyticsMetric.valueOf(m).label()).collect(Collectors.joining(", ")))
   .viewText("보기: "+switch(r.getTimeGrouping()){case "DAY"->"일별 추이";case "WEEK"->"주별 추이";case "MONTH"->"월별 추이";default->"항목별 비교";})
   .filterText("업체 검색: "+(text(r.getCompanyKeyword()).isEmpty()?"전체":text(r.getCompanyKeyword()))+" / 대분류: "+(r.getCategory().equals("ALL")?"전체":r.getCategory())+" / 사유: "+(r.getReason().equals("ALL")?"전체":r.getReason())+" / 비용: "+(r.getCharge().equals("ALL")?"전체":chargeLabel(r.getCharge()))+" / 수납: "+(r.getPayment().equals("ALL")?"전체":paymentLabel(r.getPayment()))+" / 처리: "+r.getStatuses().stream().map(this::statusLabel).collect(Collectors.joining(", ")))
   .sortText("정렬: "+columns(r).stream().filter(c->c.getKey().equals(r.getSortKey())).map(AnalyticsQueryResponse.ColumnMeta::getLabel).findFirst().orElse("")+(r.getSortDir().equals("asc")?" 오름차순":" 내림차순")).generatedAtText(ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))+" KST")
   .chartBasisText("그래프는 첫 선택 지표 기준 상위 항목 또는 기간순 · 단위별 독립 축 · 표 합계는 표시 제한과 무관한 전체 범위").build();
  var bundle=AnalyticsQueryResponse.builder().columns(columns(r)).rows(new ArrayList<>(all.subList(start,end))).totalRows(all.size()).meta(meta).warnings(warnings)
   .totals(AnalyticsQueryResponse.Totals.builder().metrics(t).taskCount(t.get("asCount")).sales(t.get("cost")).build()).chart(chart(all,r)).build();
  return AsAnalyticsResponse.builder().bundle(bundle).insights(insights(atoms,t,r)).build();
 }
 private AnalyticsQueryResponse.ChartData chart(List<Map<String,Object>> rows,AsAnalyticsRequest r){if(rows.isEmpty())return null;
  boolean trend=!r.getTimeGrouping().equals("NONE");String key=AsAnalyticsMetric.valueOf(r.getChartMetrics().get(0)).key();
  var sorted=rows.stream().sorted(trend?Comparator.comparing(a->String.valueOf(a.get("period"))):Comparator.<Map<String,Object>>comparingLong(a->((Number)a.get(key)).longValue()).reversed().thenComparing(a->String.valueOf(a.get("label")))).toList();
  int n=r.getChartLimit()==0?sorted.size():Math.min(r.getChartLimit(),sorted.size());var selected=trend?sorted.subList(sorted.size()-n,sorted.size()):sorted.subList(0,n);
  List<AnalyticsQueryResponse.ChartSeries> series=new ArrayList<>();String[] colors={"#2563eb","#0d9488","#d97706","#9333ea"};for(int i=0;i<r.getChartMetrics().size();i++){var m=AsAnalyticsMetric.valueOf(r.getChartMetrics().get(i));series.add(AnalyticsQueryResponse.ChartSeries.builder().key(m.key()).label(m.label()).unit(m.unit()).color(colors[i]).values(selected.stream().map(a->(Number)a.get(m.key())).toList()).build());}
  var chart=AnalyticsQueryResponse.ChartData.builder().title("AS · "+series.stream().map(AnalyticsQueryResponse.ChartSeries::getLabel).collect(Collectors.joining(" / "))).type(r.getChartType().toLowerCase(Locale.ROOT)).labels(selected.stream().map(a->String.valueOf(a.get("label"))).toList()).series(series).values(series.get(0).getValues()).unit(series.stream().map(AnalyticsQueryResponse.ChartSeries::getUnit).distinct().collect(Collectors.joining(" / "))).valueKey(key).totalPoints(rows.size()).note(String.format(Locale.US,"전체 %,d행 중 %,d개 표시 · %s",rows.size(),selected.size(),trend?"기간순·최근 구간":"첫 그래프 지표 상위 항목")).build();
  chart.setSvg(AnalyticsChartRenderer.svg(chart));chart.setSvgPages(AnalyticsChartRenderer.pages(chart));return chart;
 }
 private List<String> insights(List<Atom> atoms,Map<String,Long> totals,AsAnalyticsRequest r){List<String> list=new ArrayList<>();long count=totals.get("asCount");
  if(count==0){
   if((r.getCharge().equals("ZERO")||r.getCharge().equals("UNKNOWN"))&&(r.getPayment().equals("COLLECTED")||r.getPayment().equals("UNCOLLECTED")) || r.getCharge().equals("PAID")&&r.getPayment().equals("NOT_APPLICABLE"))
    list.add("선택한 비용 구분과 수납 상태가 일치하지 않습니다. 유상 수납완료·미수납은 비용이 0원 초과인 AS에만 적용됩니다. 수납 상태를 ‘전체’로 바꾸어 확인해 주세요.");
   else list.add("현재 조건에 해당하는 AS 신청이 없습니다. 기간·업체명·필터를 확인해 주세요.");return list;
  }
  for(String dim:List.of("CATEGORY","REASON")){var groups=atoms.stream().collect(Collectors.groupingBy(a->dimension(dim,a),TreeMap::new,Collectors.counting()));var top=groups.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).findFirst().orElseThrow();list.add(String.format(Locale.US,"최다 %s: %s · %,d건 (조회 AS의 %.1f%%).",dimensionLabel(dim),top.getKey(),top.getValue(),top.getValue()*100.0/count));}
  list.add(String.format(Locale.US,"미완료 %,d건 · 완료율 %.1f%%. 수납 대기 비용 %,d원 (유상 %,d건).",totals.get("pendingCount"),totals.get("completedCount")*100.0/count,totals.get("uncollectedCost"),totals.get("uncollectedCount")));
  if(r.getFromDate()!=null&&r.getToDate()!=null){long days=java.time.temporal.ChronoUnit.DAYS.between(r.getFromDate(),r.getToDate())+1;if(days<=3660&&r.getFromDate().minusDays(days).getYear()>=1000){var previous=new AsAnalyticsRequest();org.springframework.beans.BeanUtils.copyProperties(r,previous);previous.setFromDate(r.getFromDate().minusDays(days));previous.setToDate(r.getFromDate().minusDays(1));long old=load(previous).size();list.add(String.format(Locale.US,"직전 같은 길이 기간 (%s ~ %s): %,d건 → 현재 %,d건 · %s. 두 기간 모두 현재 상태 필터를 적용합니다.",previous.getFromDate(),previous.getToDate(),old,count,old==0?"이전 0건으로 증감률 산출 불가":String.format(Locale.US,"%+.1f%%",(count-old)*100.0/old)));}}
  list.add("사유·대분류 비중은 접수 집중도입니다. 제품 판매량과 연결된 고장률이 아니므로 판매량 차이를 함께 고려해 주세요.");return list;
 }
 public AsAnalyticsResponse.Details details(AsAnalyticsResponse.DetailsRequest d){
  if(d==null||d.getQuery()==null||d.getPage()<0||d.getSize()<1||d.getSize()>200)throw bad("상세 조회 조건을 확인해 주세요.");var r=d.getQuery();validate(r,false);
  if(d.getGroupValues()==null||!d.getGroupValues().isEmpty()&&d.getGroupValues().size()!=dimensions(r).size())throw bad("상세 행 범위를 확인해 주세요.");
  if(d.getPeriod()!=null&&(r.getTimeGrouping().equals("NONE")||!d.getPeriod().matches("[0-9]{4}-[0-9]{2}(-[0-9]{2})?")))throw bad("상세 기간을 확인해 주세요.");
  var matching=load(r).stream().filter(a->d.getGroupValues().isEmpty()||dimensions(r).stream().map(k->dimension(k,a)).toList().equals(d.getGroupValues())).filter(a->d.getPeriod()==null||periodLabel(a.entry.getRequestedAt().toLocalDate(),r.getTimeGrouping()).equals(d.getPeriod())).sorted(Comparator.<Atom,java.time.LocalDateTime>comparing(a->a.entry.getRequestedAt()).reversed().thenComparing(a->a.entry.getId())).toList();
  int start=(int)Math.min((long)d.getPage()*d.getSize(),matching.size()),end=Math.min(start+d.getSize(),matching.size());
  var rows=matching.subList(start,end).stream().map(a->AsAnalyticsResponse.DetailRow.builder().id(a.entry.getId()).date(a.entry.getRequestedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).company(dimension("COMPANY",a)).category(a.category).reason(a.reason).subject(text(a.entry.getSubject())).product(text(a.entry.getProductName())).status(statusLabel(status(a))).price(a.entry.getPrice()).charge(chargeLabel(charge(a))).payment(paymentLabel(payment(a))).build()).toList();
  return AsAnalyticsResponse.Details.builder().rows(rows).totalRows(matching.size()).page(d.getPage()).size(d.getSize()).totals(totals(matching)).build();
 }
}
