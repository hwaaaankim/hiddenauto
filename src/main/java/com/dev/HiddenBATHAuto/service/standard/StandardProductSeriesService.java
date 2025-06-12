package com.dev.HiddenBATHAuto.service.standard;

import java.util.List;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.standard.StandardProductSeries;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductSeriesRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StandardProductSeriesService {

    private final StandardProductSeriesRepository standardProductSeriesRepository;

    public List<StandardProductSeries> findByCategoryId(Long categoryId) {
        return standardProductSeriesRepository.findByCategoryId(categoryId);
    }
}
