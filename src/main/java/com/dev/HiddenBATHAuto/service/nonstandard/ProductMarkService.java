package com.dev.HiddenBATHAuto.service.nonstandard;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.ProductMark;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductMarkRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductMarkService {

    private final ProductMarkRepository productMarkRepository;

    public void saveProductMark(Member member, String optionJson, String localizedOptionJson) {
        ProductMark mark = new ProductMark();
        mark.setMember(member);
        mark.setOptionJson(optionJson);
        mark.setLocalizedOptionJson(localizedOptionJson);

        productMarkRepository.save(mark);
    }
}

