package com.dev.HiddenBATHAuto.service.standard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.standard.StandardCategory;
import com.dev.HiddenBATHAuto.repository.standard.StandardCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StandardCategoryService {

    private final StandardCategoryRepository standardCategoryRepository;

    public List<StandardCategory> findAll() {
        return standardCategoryRepository.findAll();
    }
}