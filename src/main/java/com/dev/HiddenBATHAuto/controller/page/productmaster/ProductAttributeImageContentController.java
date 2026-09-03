package com.dev.HiddenBATHAuto.controller.page.productmaster;

import java.time.Duration;
import java.util.NoSuchElementException;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.dev.HiddenBATHAuto.service.productmaster.ProductAttributeImageService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductAttributeImageService.StoredImageResource;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ProductAttributeImageContentController {

    private final ProductAttributeImageService imageService;

    @GetMapping("/product-spec/images/{token}")
    public ResponseEntity<Resource> image(@PathVariable String token) {
        try {
            StoredImageResource stored = imageService.loadPublicImage(token);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(stored.contentType()))
                    .contentLength(stored.fileSize())
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(stored.resource());
        } catch (NoSuchElementException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
