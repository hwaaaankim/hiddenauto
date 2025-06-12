package com.dev.HiddenBATHAuto.service.standard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.standard.StandardProduct;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StandardProductService {

    private final StandardProductRepository standardProductRepository;

    public List<StandardProduct> findAll() {
        return standardProductRepository.findAll();
    }
}
