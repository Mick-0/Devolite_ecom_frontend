package com.verso.ai_client_form.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpsEnforcementFilter extends OncePerRequestFilter {

    @Value("${app.security.require-https:false}")
    private boolean requireHttps;

    @Value("${app.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Value("${app.security.redirect-host:}")
    private String redirectHost;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requireHttps) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean secure = request.isSecure();
        if (!secure && trustForwardedHeaders) {
            String forwardedProto = request.getHeader("X-Forwarded-Proto");
            if (forwardedProto != null) {
                String proto = forwardedProto.split(",")[0].trim();
                if ("https".equalsIgnoreCase(proto)) {
                    secure = true;
                }
            }
        }

        if (secure) {
            filterChain.doFilter(request, response);
            return;
        }

        String host = (redirectHost != null && !redirectHost.isBlank()) ? redirectHost.trim() : null;
        if (host == null || host.isBlank()) {
            if (trustForwardedHeaders) {
                String forwardedHost = request.getHeader("X-Forwarded-Host");
                if (forwardedHost != null && !forwardedHost.isBlank()) {
                    host = forwardedHost.split(",")[0].trim();
                }
            }
            if (host == null || host.isBlank()) {
                host = request.getServerName();
                int port = request.getServerPort();
                if (port > 0 && port != 80 && port != 443) {
                    host = host + ":" + port;
                }
            }
        }

        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String target = "https://" + host + uri + (query != null ? "?" + query : "");

        response.setStatus(HttpServletResponse.SC_PERMANENT_REDIRECT);
        response.setHeader("Location", target);
    }
}
