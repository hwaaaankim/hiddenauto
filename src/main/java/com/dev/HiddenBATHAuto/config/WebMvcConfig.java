package com.dev.HiddenBATHAuto.config;

import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${spring.upload.env}")
    private String env;

    @Value("${spring.upload.path}")
    private String uploadPath;

    private final Environment environment;

    public WebMvcConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String resourcePath = Paths.get(uploadPath).toUri().toString();

        System.out.println("[WebMvcConfig] Active profile: " + String.join(",", environment.getActiveProfiles()));
        System.out.println("[WebMvcConfig] Upload path for profile '" + env + "': " + resourcePath);

        registry.addResourceHandler("/upload/**")
                .addResourceLocations(resourcePath);
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