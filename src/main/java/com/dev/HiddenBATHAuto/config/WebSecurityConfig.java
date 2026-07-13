package com.dev.HiddenBATHAuto.config;

import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 로그인 화면의 체크박스 name과 반드시 동일해야 합니다.
     */
    private static final String REMEMBER_ME_PARAMETER = "remember";

    /**
     * 브라우저에 저장되는 자동 로그인 쿠키 이름입니다.
     */
    private static final String REMEMBER_ME_COOKIE_NAME =
            "HIDDENBATH_REMEMBER_ME";

    /**
     * 자동 로그인 유지 기간: 30일
     */
    private static final int REMEMBER_ME_VALIDITY_SECONDS =
            60 * 60 * 24 * 30;

    private final PrincipalDetailsService principalDetailsService;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    /**
     * Remember-Me 토큰 서명 키입니다.
     *
     * 서버가 재시작되어도 동일한 키를 사용해야
     * 기존 자동 로그인 쿠키가 계속 유효합니다.
     *
     * application.yml:
     *
     * security:
     *   remember-me:
     *     key: ${HIDDENBATH_REMEMBER_ME_KEY}
     */
    @Value("${security.remember-me.key}")
    private String rememberMeKey;

    /**
     * true이면 HTTPS 연결에서만 Remember-Me 쿠키를 전송합니다.
     *
     * 로컬 HTTP 개발환경에서는 false,
     * 실제 HTTPS 운영환경에서는 true를 사용합니다.
     */
    @Value("${security.remember-me.secure-cookie:false}")
    private boolean rememberMeSecureCookie;

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
     *
     * /admin/** 전체를 MANAGEMENT에 열면 위험하므로,
     * 실제 메뉴에서 MANAGEMENT도 사용해야 하는 기능만 먼저 허용합니다.
     */
    private final String[] adminManagementUrls = {
            "/admin/process/**",
            "/admin/notification/**"
    };

    /**
     * ADMIN 전용 기능입니다.
     *
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
     *
     * /team/** 또는 /management/** 아래에 두면
     * 반대 권한 화면에서 호출할 수 없으므로 별도 경로로 분리합니다.
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
        return web -> web
                .ignoring()
                .requestMatchers(
                        PathRequest.toStaticResources().atCommonLocations()
                );
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
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        provider.setUserDetailsService(principalDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        /**
         * 계정 없음과 비밀번호 불일치를
         * CustomAuthenticationFailureHandler에서 구분하기 위해 필요합니다.
         */
        provider.setHideUserNotFoundExceptions(false);

        return provider;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.authenticationProvider(authenticationProvider());

        http
            .csrf(csrfConfig ->
                    csrfConfig.disable()
            )
            .headers(headerConfig ->
                    headerConfig.frameOptions(
                            frameOptionsConfig ->
                                    frameOptionsConfig.disable()
                    )
            )
            .authorizeHttpRequests(authorizeRequests ->
                    authorizeRequests

                        /*
                         * 비로그인 사용자 접근 가능 URL
                         */
                        .requestMatchers(visitorsCommonUrls)
                        .permitAll()

                        .requestMatchers(
                                "/administration/**",
                                "/front/**",
                                "/favicon.ico"
                        )
                        .permitAll()

                        /*
                         * 로그인한 모든 프로젝트 사용자 공통 페이지
                         */
                        .requestMatchers(adminCommonUrls)
                        .hasAnyAuthority(
                                "ROLE_ADMIN",
                                "ROLE_MANAGEMENT",
                                "ROLE_INTERNAL_EMPLOYEE",
                                "ROLE_CUSTOMER_REPRESENTATIVE",
                                "ROLE_CUSTOMER_EMPLOYEE"
                        )

                        /*
                         * 관리자/관리팀/내부직원 공통 내부 API
                         */
                        .requestMatchers(internalApiUrls)
                        .hasAnyAuthority(
                                "ROLE_ADMIN",
                                "ROLE_MANAGEMENT",
                                "ROLE_INTERNAL_EMPLOYEE"
                        )

                        /*
                         * MANAGEMENT도 접근 가능한 /admin 하위 기능은
                         * /admin/**보다 먼저 선언해야 합니다.
                         */
                        .requestMatchers(adminManagementUrls)
                        .hasAnyAuthority(
                                "ROLE_ADMIN",
                                "ROLE_MANAGEMENT"
                        )

                        /*
                         * /admin/** 나머지와 /analytics는 ADMIN 전용
                         */
                        .requestMatchers(adminsUrls)
                        .hasAuthority(
                                "ROLE_ADMIN"
                        )

                        /*
                         * 발주등록, 발주관리, AS관리, 배송관리,
                         * 생산관리 등 /management/** 기능
                         */
                        .requestMatchers(managementUrls)
                        .hasAnyAuthority(
                                "ROLE_ADMIN",
                                "ROLE_MANAGEMENT"
                        )

                        /*
                         * 생산팀/배송팀/AS팀/출고팀 내부직원 페이지
                         */
                        .requestMatchers(teamUrls)
                        .hasAuthority(
                                "ROLE_INTERNAL_EMPLOYEE"
                        )

                        /*
                         * 고객사 대표/직원 페이지
                         */
                        .requestMatchers(customersUrls)
                        .hasAnyAuthority(
                                "ROLE_CUSTOMER_REPRESENTATIVE",
                                "ROLE_CUSTOMER_EMPLOYEE"
                        )

                        /*
                         * 위에서 별도로 허용하지 않은 요청은
                         * 로그인한 사용자만 접근 가능합니다.
                         */
                        .anyRequest()
                        .authenticated()
            )
            .exceptionHandling(exceptionHandling ->
                    exceptionHandling
                        .accessDeniedHandler(
                                customAccessDeniedHandler
                        )
            )
            .formLogin(formLogin ->
                    formLogin
                        .loginPage("/loginForm")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .loginProcessingUrl("/signinProcess")
                        .successHandler(
                                customAuthenticationSuccessHandler()
                        )
                        .failureHandler(
                                customAuthenticationFailureHandler
                        )
                        .permitAll()
            )
            .rememberMe(rememberMe ->
                    rememberMe
                        /*
                         * 토큰 위변조 검증용 고정 서명 키
                         */
                        .key(rememberMeKey)

                        /*
                         * 로그인 폼 체크박스 name
                         */
                        .rememberMeParameter(
                                REMEMBER_ME_PARAMETER
                        )

                        /*
                         * 브라우저 쿠키 이름
                         */
                        .rememberMeCookieName(
                                REMEMBER_ME_COOKIE_NAME
                        )

                        /*
                         * Remember-Me 사용자 조회 서비스
                         */
                        .userDetailsService(
                                principalDetailsService
                        )

                        /*
                         * 쿠키 및 토큰 유지 기간: 30일
                         */
                        .tokenValiditySeconds(
                                REMEMBER_ME_VALIDITY_SECONDS
                        )

                        /*
                         * 운영 HTTPS에서는 true 권장
                         */
                        .useSecureCookie(
                                rememberMeSecureCookie
                        )

                        /*
                         * 체크박스를 선택한 경우에만 자동 로그인 쿠키 발급
                         */
                        .alwaysRemember(false)
            )
            .logout(logoutConfig ->
                    logoutConfig
                        .logoutUrl("/logout")

                        /*
                         * 로그아웃 시 세션 쿠키와
                         * Remember-Me 쿠키를 모두 삭제합니다.
                         */
                        .deleteCookies(
                                "JSESSIONID",
                                REMEMBER_ME_COOKIE_NAME
                        )

                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessUrl("/index")
            );

        return http.build();
    }
}