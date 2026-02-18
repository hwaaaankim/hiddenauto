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

    /** 애플리케이션 기동 시 업로드 폴더가 없으면 생성 */
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
        String location = "file:" + normalized; // file: 프리픽스

        System.out.println("[WebMvcConfig] Active profile: " + String.join(",", environment.getActiveProfiles()));
        System.out.println("[WebMvcConfig] Upload env: " + env);
        System.out.println("[WebMvcConfig] Upload path: " + normalized);
        System.out.println("[WebMvcConfig] Resource location: " + location);

        registry.addResourceHandler("/upload/**")
                .addResourceLocations(location);
    }

    /**
     * ✅ 디렉터리 경로를 OS별로 안전하게 정규화
     * - \ -> /
     * - ${user.home} 문자열이 그대로 들어온 경우 실제 홈으로 치환
     * - ~ 로 시작하면 홈으로 치환
     * - 마지막에 / 보장
     */
    private String normalizeDir(String dir) {
        if (!StringUtils.hasText(dir)) return dir;

        String d = dir.replace("\\", "/").trim();

        String userHome = System.getProperty("user.home");
        if (StringUtils.hasText(userHome)) {
            userHome = userHome.replace("\\", "/");
            // ${user.home} 문자열이 그대로 들어온 경우 치환
            d = d.replace("${user.home}", userHome);

            // ~ 처리
            if (d.equals("~")) {
                d = userHome;
            } else if (d.startsWith("~/")) {
                d = userHome + d.substring(1);
            }
        }

        if (!d.endsWith("/")) d = d + "/";
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
