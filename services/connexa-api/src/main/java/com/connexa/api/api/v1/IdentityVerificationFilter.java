package com.connexa.api.api.v1;

import com.connexa.api.domain.identity.AuthenticationRequiredException;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.identity.IdentityVerifier;
import com.connexa.api.infrastructure.identity.VerifiedIdentityRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifies protected requests before MVC binds a request body or calls a controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class IdentityVerificationFilter extends OncePerRequestFilter {

    private static final String AUTHENTICATION_PROBLEM_TYPE =
            "https://api.connexa.local/problems/authentication-required";

    private final IdentityVerifier identityVerifier;

    public IdentityVerificationFilter(IdentityVerifier identityVerifier) {
        this.identityVerifier = identityVerifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !requiresIdentity(pathWithinApplication(request), request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            VerifiedIdentityRequest.attach(request, identityVerifier.verify(request));
            filterChain.doFilter(request, response);
        } catch (AuthenticationRequiredException exception) {
            writeAuthenticationProblem(request, response);
        }
    }

    private static boolean requiresIdentity(String path, String method) {
        if (path.equals("/api/v1/me") || path.startsWith("/api/v1/me/")) {
            return true;
        }
        if (path.startsWith("/api/v1/assistant/")
                || path.startsWith("/api/v1/organizer/")
                || path.startsWith("/api/v1/notifications")
                // Index rebuilding spends provider quota, so it is never anonymous.
                // Listed explicitly rather than relying on the controller to reject a
                // request with no identity attached.
                || path.startsWith("/api/v1/search/")) {
            return true;
        }
        if (!path.startsWith("/api/v1/events")) {
            return false;
        }
        return !("GET".equalsIgnoreCase(method) && isPublicEventRead(path));
    }

    /**
     * The catalogue: the listing, one event, and how many seats it has left.
     *
     * <p>Seat availability is public for the same reason the event itself is — it is the
     * number a poster carries, and hiding it left a browsing visitor unable to tell a full
     * event from an open one, which is what decides whether they sign up at all.
     *
     * <p>What stays closed is anything naming a person: an individual's RSVP, their place in
     * a queue, their saved events. The count is about the event; those are about someone.
     * The live stream is closed too, being a connection an anonymous caller could hold open
     * without limit.
     */
    private static boolean isPublicEventRead(String path) {
        if (path.equals("/api/v1/events")) {
            return true;
        }
        String prefix = "/api/v1/events/";
        if (!path.startsWith(prefix)) {
            return false;
        }
        String afterEventId = path.substring(prefix.length());
        int separator = afterEventId.indexOf('/');
        if (separator < 0) {
            return true;
        }
        // Exactly one segment, matched whole: "capacity" is public, "capacity/stream" is not.
        return afterEventId.substring(separator + 1).equals("capacity");
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty() || !requestUri.startsWith(contextPath)) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }

    private static void writeAuthenticationProblem(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestId = RequestIdFilter.current(request);
        response.getWriter().write("{\"type\":\"" + AUTHENTICATION_PROBLEM_TYPE
                + "\",\"title\":\"Authentication required\",\"status\":401"
                + ",\"detail\":\"A verified identity is required for this operation.\""
                + ",\"requestId\":\"" + requestId + "\"}");
    }
}
