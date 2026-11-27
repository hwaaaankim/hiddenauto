package com.dev.HiddenBATHAuto.controller.page;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.dev.HiddenBATHAuto.dto.analytics.*;
import com.dev.HiddenBATHAuto.service.analytics.*;
import lombok.RequiredArgsConstructor;
@Controller @RequiredArgsConstructor @RequestMapping("/as-analytics")
public class AsAnalyticsController {
 private final AsAnalyticsService service;
 private final AsAnalyticsExcelExporter excel;
 @GetMapping public String page(){return "administration/analytics/asAnalytics";}
 @GetMapping("/api/options") @ResponseBody public Map<String,Object> options(){return Map.of("catalog",service.catalog(),"metrics",java.util.Arrays.stream(AsAnalyticsMetric.values()).map(m->Map.of("code",m.name(),"key",m.key(),"label",m.label(),"unit",m.unit(),"help",m.help())).toList());}
 @PostMapping("/api/query") @ResponseBody public AsAnalyticsResponse query(@RequestBody AsAnalyticsRequest request){return service.query(request,false);}
 @PostMapping("/api/details") @ResponseBody public AsAnalyticsResponse.Details details(@RequestBody AsAnalyticsResponse.DetailsRequest request){return service.details(request);}
 @PostMapping("/api/export/excel") public ResponseEntity<byte[]> excel(@RequestBody AsAnalyticsRequest request){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"as-analytics.xlsx\"").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(excel.export(service.query(request,true)));}
 @PostMapping("/api/export/csv") public ResponseEntity<byte[]> csv(@RequestBody AsAnalyticsRequest request){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"as-analytics.csv\"").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(AnalyticsCsvExporter.export(service.query(request,true).getBundle()));}
 @PostMapping("/api/export/report") public String report(@RequestBody AsAnalyticsRequest request,Model model){var response=service.query(request,true);model.addAttribute("analysis",response);model.addAttribute("bundle",response.getBundle());return "administration/analytics/asAnalyticsReport";}
 @ExceptionHandler(ResponseStatusException.class) @ResponseBody public ResponseEntity<Map<String,String>> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"조회 조건을 확인해 주세요.":e.getReason()));}
}
