package com.connexa.api.api.v1;

import com.connexa.api.application.IngestionService;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates announcement intake before the request body is bound.
 *
 * <p>Checking the token inside the controller is too late: {@code @Valid} runs during
 * argument binding, so an unauthenticated caller would receive detailed validation
 * responses and could map the request shape without ever holding a credential. A filter
 * runs first, so an unauthorised request is refused before any of its content is examined.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public final class IngestionTokenFilter extends OncePerRequestFilter {

    static final String TOKEN_HEADER = "X-Connexa-Ingestion-Token";
    private static final String PATH_PREFIX = "/api/v1/ingestion";
    private static final String PROBLEM_TYPE = "https://api.connexa.local/problems/forbidden";

    private final IngestionService ingestionService;

    public IngestionTokenFilter(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathWithinApplication(request).startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            ingestionService.requireIngestionAuthority(request.getHeader(TOKEN_HEADER));
        } catch (ActorNotAuthorizedException refused) {
            writeForbidden(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty() || !requestUri.startsWith(contextPath)) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }

    private static void writeForbidden(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"" + PROBLEM_TYPE
                + "\",\"title\":\"Not authorized\",\"status\":403"
                + ",\"detail\":\"A valid ingestion token is required.\""
                + ",\"requestId\":\"" + RequestIdFilter.current(request) + "\"}");
    }
}
