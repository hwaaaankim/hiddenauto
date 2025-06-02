package com.dev.HiddenBATHAuto.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.WebApplicationInitializer;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

@Configuration
public class MainWebAppInitializer implements WebApplicationInitializer {
   
	@Override
    public void onStartup(@NonNull ServletContext sc) throws ServletException {
        sc.getSessionCookieConfig().setHttpOnly(true);        
        sc.getSessionCookieConfig().setSecure(false);        
    }
}
