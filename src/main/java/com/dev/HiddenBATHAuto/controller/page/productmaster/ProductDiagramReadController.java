package com.dev.HiddenBATHAuto.controller.page.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Answer;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductDiagramDtos.Layout;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductActualRepository;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStudioJson;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Staff view uses the immutable actual-specification snapshot, never current editable definitions. */
@Controller
@RequiredArgsConstructor
@RequestMapping("/team/product-diagram")
@PreAuthorize("hasAnyRole('ADMIN','MANAGEMENT','INTERNAL_EMPLOYEE')")
public class ProductDiagramReadController {
  private final ProductActualRepository actuals;
  private final ProductStudioJson json;
  @GetMapping("/actuals/{id}")
  public String page(@PathVariable Long id, Model model) {
    model.addAttribute("diagramActualId",id);
    return "administration/productmaster/diagramRead";
  }
  @GetMapping("/actuals/{id}/data")
  @ResponseBody
  @Transactional(readOnly = true)
  public Map<String,Object> data(@PathVariable Long id) {
    var actual=actuals.findById(id).orElseThrow(() -> new NoSuchElementException("실제품을 찾을 수 없습니다."));
    Map<String,Answer> answers=json.read(actual.getAnswersJson(),new TypeReference<Map<String,Answer>>(){});
    Map<String,Layout> diagrams=new LinkedHashMap<>();
    answers.forEach((key,answer) -> {if(answer!=null && answer.diagram()!=null)diagrams.put(key,answer.diagram());});
    return Map.of("code",actual.getCode(),"diagrams",diagrams);
  }
}
