package com.dev.HiddenBATHAuto.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

import com.dev.HiddenBATHAuto.handler.CustomAccessDeniedHandler;
import com.dev.HiddenBATHAuto.handler.CustomAuthenticationSuccessHandler;
import com.dev.HiddenBATHAuto.service.auth.PrincipalDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class WebSecurityConfig {

	
	private final PrincipalDetailsService principalDetailsService;
	
	
	private final String[] visitorsCommonUrls = {
			"/signUp",
			"/api/v1/**",
			"/api/v2/**",
			"/signUpProcess",
			"/findPasswordProcess",
			"/findPassword",
			"/findUsernameProcess",
			"/findUsername",
			"/api/excel/**",
			"/api/standard/**"
	};
	
	private final String[] adminCommonUrls = {
			"/common/main"
	};
	
	private final String[] adminsUrls = {
			"/admin/**"
	};
	
	private final String[] managementUrls = {
			"/management/**" 
	};
	
	private final String[] teamUrls = {
			"/team/**"
	};
	
	private final String[] customersUrls = {
		"/", 
		"/index",
	    "/customer/**",
	    "/orderConfirm",
	    "/api/v2/cartDelete",
	    "/api/v2/insertCart",
	    "/api/v2/cartSelect",
	    "/api/v2/cartDeleteAll",
	    "/api/v2/insertMark",
	    "/api/v1/translate",
	    "/api/v1/calendar/events",
	    "/api/v1/calendar/tasks",
	    "/api/order/submit",
	    "/modeling",
	    "/blueprint",
	    "/calculate",
	    "/standardOrderProduct",
	    "/nonStandardOrderProduct",
	    "/cart"
	};

	
	@Bean
	HttpSessionEventPublisher httpSessionEventPublisher() {
	    return new HttpSessionEventPublisher();
	}
	
	@Bean
	SpringSecurityDialect springSecurityDialect() {
		return new SpringSecurityDialect();
	}

	@Bean
	WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.ignoring().requestMatchers(PathRequest.toStaticResources().atCommonLocations());
	}
	
	@Bean
	PasswordEncoder passwordEncoder() {
	    return new BCryptPasswordEncoder();
	}
	
	@Bean
	CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler() {
	    return new CustomAuthenticationSuccessHandler();
	}
	
	@Bean
	CustomAccessDeniedHandler customAccessDeniedHandler() {
		return new CustomAccessDeniedHandler();
	}
	
	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		
		http.csrf((csrfConfig) ->  
				csrfConfig.disable())
			.headers((headerConfig) -> 
				headerConfig
					.frameOptions(frameOptionsConfig -> frameOptionsConfig.disable()))
			.authorizeHttpRequests((authorizeRequests) -> 
				authorizeRequests
					.requestMatchers(visitorsCommonUrls).permitAll()
					.requestMatchers("/administration/**", "/front/**", "/favicon.ico").permitAll()
					.requestMatchers(adminCommonUrls).hasAnyAuthority(
						    "ROLE_ADMIN",
						    "ROLE_MANAGEMENT",
						    "ROLE_INTERNAL_EMPLOYEE",
						    "ROLE_CUSTOMER_REPRESENTATIVE",
						    "ROLE_CUSTOMER_EMPLOYEE"
						)
					.requestMatchers(adminsUrls).hasAuthority("ROLE_ADMIN")
					.requestMatchers(managementUrls).hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGEMENT")
					.requestMatchers(teamUrls).hasAuthority("ROLE_INTERNAL_EMPLOYEE")
					.requestMatchers(customersUrls).hasAnyAuthority("ROLE_CUSTOMER_REPRESENTATIVE", "ROLE_CUSTOMER_EMPLOYEE")
					.anyRequest().authenticated())
			.exceptionHandling((ex -> ex.accessDeniedHandler(customAccessDeniedHandler())))
			.formLogin((formLogin) -> 
				formLogin
					.loginPage("/loginForm")
					.usernameParameter("username")
					.passwordParameter("password")
					.loginProcessingUrl("/signinProcess")
					.successHandler(customAuthenticationSuccessHandler())
					.permitAll())
			.rememberMe((remember) -> 
				remember
					.rememberMeParameter("remember")
					.userDetailsService(principalDetailsService)
					.tokenValiditySeconds(60*60*24*30)
					.alwaysRemember(false))
			.logout((logoutConfig) -> 
				logoutConfig
					.logoutUrl("/logout")
					.deleteCookies("JSESSIONID")
					.invalidateHttpSession(true)
					.logoutSuccessUrl("/index"));
			return http.build();
	}
}

