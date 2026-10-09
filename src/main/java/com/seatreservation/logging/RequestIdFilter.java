package com.seatreservation.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "request_id";
    public static final String MDC_USER_ID = "user_id";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String incoming = request.getHeader(HEADER);
        String requestId = (incoming != null && SAFE_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        long startNanos = System.nanoTime();

        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startNanos) / 1_000_000;
            String path = request.getRequestURI();
            if (path.startsWith("/actuator")) {
                log.debug("HTTP {} {} -> {} in {} ms", request.getMethod(), path, response.getStatus(), millis);
            } else {
                log.info("HTTP {} {} -> {} in {} ms", request.getMethod(), path, response.getStatus(), millis);
            }
            MDC.clear();
        }
    }
}