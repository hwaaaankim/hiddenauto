package com.dev.HiddenBATHAuto.config;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
import com.dev.HiddenBATHAuto.handler.CustomAuthenticationFailureHandler;
import com.dev.HiddenBATHAuto.handler.CustomAuthenticationSuccessHandler;
import com.dev.HiddenBATHAuto.service.auth.PrincipalDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class WebSecurityConfig {

    private final PrincipalDetailsService principalDetailsService;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    private final String[] visitorsCommonUrls = {
            "/signUp",
            "/api/v1/**",
            "/api/v2/**",
            "/api/public/solapi/webhook/**",
            "/signUpProcess",
            "/findPasswordProcess",
            "/findPassword",
            "/findUsernameProcess",
            "/findUsername",
            "/api/excel/**",
            "/api/standard/**",
            "/excelConvert",
            "/api/admin/member-excel/upload",
            "/productImageUpload/**",
            "/loginForm",
            "/signinProcess"
    };

    private final String[] adminCommonUrls = {
            "/common/main"
    };

    /**
     * ADMIN과 MANAGEMENT가 함께 사용할 수 있는 /admin 하위 기능입니다.
     * /admin/** 전체를 MANAGEMENT에 열면 위험하므로,
     * 실제 메뉴에서 MANAGEMENT도 사용해야 하는 기능만 먼저 허용합니다.
     */
    private final String[] adminManagementUrls = {
            "/admin/process/**",
            "/admin/notification/**"
    };

    /**
     * ADMIN 전용 기능입니다.
     * 매출관리/매출분석(/analytics)은 MANAGEMENT에 열지 않습니다.
     */
    private final String[] adminsUrls = {
            "/admin/**",
            "/analytics"
    };

    private final String[] managementUrls = {
            "/management/**"
    };

    private final String[] teamUrls = {
            "/team/**"
    };

    /**
     * 관리자/관리팀/내부직원이 함께 사용하는 공통 내부 API입니다.
     * /team/** 또는 /management/** 아래에 두면 반대 권한 화면에서 호출할 수 없으므로 별도 경로로 분리합니다.
     */
    private final String[] internalApiUrls = {
            "/api/internal/**"
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
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(principalDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        // ✅ 계정없음 / 비밀번호틀림 구분하려면 반드시 필요
        provider.setHideUserNotFoundExceptions(false);

        return provider;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.authenticationProvider(authenticationProvider());

        http.csrf(csrfConfig -> csrfConfig.disable())
            .headers(headerConfig ->
                headerConfig.frameOptions(frameOptionsConfig -> frameOptionsConfig.disable()))
            .authorizeHttpRequests(authorizeRequests ->
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
                    .requestMatchers(internalApiUrls).hasAnyAuthority(
                            "ROLE_ADMIN",
                            "ROLE_MANAGEMENT",
                            "ROLE_INTERNAL_EMPLOYEE"
                    )

                    // ✅ MANAGEMENT도 접근 가능한 /admin 하위 기능은 /admin/**보다 먼저 선언해야 합니다.
                    .requestMatchers(adminManagementUrls).hasAnyAuthority(
                            "ROLE_ADMIN",
                            "ROLE_MANAGEMENT"
                    )

                    // ✅ /admin/** 나머지와 /analytics는 ADMIN 전용입니다.
                    .requestMatchers(adminsUrls).hasAuthority("ROLE_ADMIN")

                    // ✅ 발주등록, 발주관리, AS관리, 배송관리, 생산관리 등 /management/**는 ADMIN/MANAGEMENT 공통입니다.
                    .requestMatchers(managementUrls).hasAnyAuthority(
                            "ROLE_ADMIN",
                            "ROLE_MANAGEMENT"
                    )
                    .requestMatchers(teamUrls).hasAuthority("ROLE_INTERNAL_EMPLOYEE")
                    .requestMatchers(customersUrls).hasAnyAuthority(
                            "ROLE_CUSTOMER_REPRESENTATIVE",
                            "ROLE_CUSTOMER_EMPLOYEE"
                    )
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.accessDeniedHandler(customAccessDeniedHandler))
            .formLogin(formLogin ->
                formLogin
                    .loginPage("/loginForm")
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .loginProcessingUrl("/signinProcess")
                    .successHandler(customAuthenticationSuccessHandler())
                    .failureHandler(customAuthenticationFailureHandler)
                    .permitAll()
            )
            .rememberMe(remember ->
                remember
                    .rememberMeParameter("remember")
                    .userDetailsService(principalDetailsService)
                    .tokenValiditySeconds(60 * 60 * 24 * 30)
                    .alwaysRemember(false)
            )
            .logout(logoutConfig ->
                logoutConfig
                    .logoutUrl("/logout")
                    .deleteCookies("JSESSIONID")
                    .invalidateHttpSession(true)
                    .logoutSuccessUrl("/index")
            );

        return http.build();
    }
}