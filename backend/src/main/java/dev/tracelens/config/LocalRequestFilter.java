package dev.tracelens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalRequestFilter extends OncePerRequestFilter {
    private static final Set<String> HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        String host = request.getHeader("Host");
        String origin = request.getHeader("Origin");
        boolean allowed = false;
        if (host != null) {
            try {
                URI local = URI.create(request.getScheme() + "://" + host);
                boolean localOrigin = origin == null || isLoopbackOrigin(URI.create(origin));
                allowed = HOSTS.contains(local.getHost()) && local.getRawUserInfo() == null
                        && local.getRawPath().isEmpty() && local.getRawQuery() == null && local.getRawFragment() == null
                        && localOrigin
                        && !"cross-site".equals(request.getHeader("Sec-Fetch-Site"));
            } catch (IllegalArgumentException ignored) { /* Reject malformed authority/origin. */ }
        }
        if (!allowed) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"LOCAL_REQUEST_REQUIRED\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isLoopbackOrigin(URI value) {
        return "http".equals(value.getScheme()) && HOSTS.contains(value.getHost()) && value.getRawUserInfo() == null
                && (value.getRawPath().isEmpty() || "/".equals(value.getRawPath()))
                && value.getRawQuery() == null && value.getRawFragment() == null;
    }
}
