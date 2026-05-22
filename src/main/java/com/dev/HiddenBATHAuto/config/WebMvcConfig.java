package com.dev.HiddenBATHAuto.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.annotation.PostConstruct;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final Environment environment;

    public WebMvcConfig(Environment environment) {
        this.environment = environment;
    }

    @Value("${spring.upload.path}")
    private String uploadPath;

    @Value("${spring.upload.env}")
    private String env;

    @PostConstruct
    public void ensureUploadDir() {
        try {
            String normalized = normalizeDir(uploadPath);
            Path p = Paths.get(normalized);

            if (Files.notExists(p)) {
                Files.createDirectories(p);
            }

            System.out.println("[WebMvcConfig] Ensured upload dir: " + p.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("[WebMvcConfig] Failed to create upload dir: " + e.getMessage());
        }
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String normalized = normalizeDir(uploadPath);
        String location = "file:" + normalized;

        System.out.println("[WebMvcConfig] Active profile: " + String.join(",", environment.getActiveProfiles()));
        System.out.println("[WebMvcConfig] Upload env: " + env);
        System.out.println("[WebMvcConfig] Upload path: " + normalized);
        System.out.println("[WebMvcConfig] Resource location: " + location);

        /*
         * 사용자/공통 접근용
         * 예: /upload/process-info/1/2026-05-22/xxx.jpg
         */
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(location);

        /*
         * 관리자 화면 접근용
         * ProcessMakerService에서 현재 fileUrl을
         * /administration/upload/process-info/... 형태로 만들고 있으므로 반드시 필요합니다.
         */
        registry.addResourceHandler("/administration/upload/**")
                .addResourceLocations(location);
    }

    private String normalizeDir(String dir) {
        if (!StringUtils.hasText(dir)) {
            return dir;
        }

        String d = dir.replace("\\", "/").trim();

        String userHome = System.getProperty("user.home");
        if (StringUtils.hasText(userHome)) {
            userHome = userHome.replace("\\", "/");

            d = d.replace("${user.home}", userHome);

            if (d.equals("~")) {
                d = userHome;
            } else if (d.startsWith("~/")) {
                d = userHome + d.substring(1);
            }
        }

        if (!d.endsWith("/")) {
            d = d + "/";
        }

        return d;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ObjectMapper objectMapper = ((MappingJackson2HttpMessageConverter) converter).getObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
            }
        }
    }
}