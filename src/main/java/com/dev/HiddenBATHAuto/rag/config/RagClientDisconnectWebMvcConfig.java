package com.dev.HiddenBATHAuto.rag.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.DisconnectedClientHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 브라우저 새로고침, 페이지 이동, 탭 종료, 프록시/터널 종료 등으로
 * 클라이언트가 응답을 받기 전에 연결을 끊은 경우를 일반 API 오류보다 먼저 처리합니다.
 *
 * 연결이 이미 닫힌 상태에서는 JSON 오류 응답을 다시 작성하면 안 되므로
 * 빈 ModelAndView를 반환해 예외 처리를 종료합니다.
 */
@Configuration
public class RagClientDisconnectWebMvcConfig implements WebMvcConfigurer {

    private static final Logger log =
            LoggerFactory.getLogger(RagClientDisconnectWebMvcConfig.class);

    @Override
    public void extendHandlerExceptionResolvers(
            List<HandlerExceptionResolver> resolvers
    ) {
        resolvers.add(0, this::resolveClientDisconnect);
    }

    private ModelAndView resolveClientDisconnect(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception error
    ) {
        if (!DisconnectedClientHelper.isClientDisconnectedException(error)) {
            return null;
        }

        String queryString = request.getQueryString();
        String requestPath = request.getRequestURI()
                + (queryString == null || queryString.isBlank()
                        ? ""
                        : "?" + queryString);

        log.debug(
                "클라이언트 연결 종료: method={}, path={}, remote={}, userAgent={}",
                request.getMethod(),
                requestPath,
                request.getRemoteAddr(),
                safeHeader(request, "User-Agent")
        );

        // 응답 스트림이 이미 사용할 수 없는 상태이므로 아무 응답도 다시 쓰지 않습니다.
        return new ModelAndView();
    }

    private String safeHeader(
            HttpServletRequest request,
            String name
    ) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return "-";
        }

        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 300
                ? normalized
                : normalized.substring(0, 300);
    }
}
