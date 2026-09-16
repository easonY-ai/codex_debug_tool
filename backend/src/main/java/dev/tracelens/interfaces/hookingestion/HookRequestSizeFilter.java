package dev.tracelens.interfaces.hookingestion;

import dev.tracelens.application.hookingestion.AcceptHookEventUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP transport guard for the raw-evidence Hook endpoint.
 *
 * <p>The controller intentionally accepts a typed envelope rather than a byte array. Checking the
 * declared request size before Jackson parses it preserves the 413 contract even when an oversized
 * body is not valid JSON. The controller repeats the check on the decoded raw event for requests
 * without a usable Content-Length header.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HookRequestSizeFilter extends OncePerRequestFilter {
    private final AcceptHookEventUseCase useCase;

    public HookRequestSizeFilter(AcceptHookEventUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !"/api/ingestion/hooks".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > HookIngestionController.MAX_HOOK_REQUEST_BYTES) {
            useCase.recordClientError();
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"HOOK_REQUEST_TOO_LARGE\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
