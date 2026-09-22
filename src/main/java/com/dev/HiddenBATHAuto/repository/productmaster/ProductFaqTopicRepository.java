package com.dev.HiddenBATHAuto.repository.productmaster;

import com.dev.HiddenBATHAuto.model.productmaster.ProductFaqTopic;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ProductFaqTopicRepository extends JpaRepository<ProductFaqTopic, Long> {
  List<ProductFaqTopic> findAllByOrderByIdDesc();
}
