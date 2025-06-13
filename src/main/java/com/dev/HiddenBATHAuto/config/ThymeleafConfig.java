package com.dev.HiddenBATHAuto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class ThymeleafConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
