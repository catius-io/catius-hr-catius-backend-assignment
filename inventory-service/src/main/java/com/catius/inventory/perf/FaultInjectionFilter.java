package com.catius.inventory.perf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Perf 시나리오 전용 fault injection — k6에서 inventory의 의도적 지연/오류 거동을 관찰하기 위한 도구.
 *
 * <p>{@code @Profile("fault-injection")}로 완전 격리되어 기본 실행·테스트·운영 경로에는 영향이 없다.
 * 활성 시 {@code /api/v1/inventory/reserve} POST 요청에만 delay 또는 5xx 오류를 주입한다.
 * inventory의 다른 엔드포인트(GET, /release)는 unaffected — Saga 측에서 보상 흐름을 관찰하려면
 * release는 정상 동작해야 함.
 *
 * <p>설정 (모두 기본값 0):
 * <ul>
 *   <li>{@code fault.delay-ms}: 매 요청마다 추가하는 고정 지연 (ms)</li>
 *   <li>{@code fault.error-rate}: 0.0~1.0 — 응답을 5xx로 강제할 확률</li>
 * </ul>
 */
@Component
@Profile("fault-injection")
@Slf4j
public class FaultInjectionFilter extends OncePerRequestFilter {

    @Value("${fault.delay-ms:0}")
    private long delayMs;

    @Value("${fault.error-rate:0.0}")
    private double errorRate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/inventory/reserve".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (errorRate > 0.0 && ThreadLocalRandom.current().nextDouble() < errorRate) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"FAULT_INJECTED\",\"message\":\"perf fault injection\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
