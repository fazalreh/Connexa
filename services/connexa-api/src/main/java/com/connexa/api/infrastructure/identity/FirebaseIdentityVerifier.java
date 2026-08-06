package com.connexa.api.infrastructure.identity;

import com.connexa.api.domain.identity.AuthenticationRequiredException;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Verifies a bearer token with the identity provider and maps it to an application identity.
 *
 * <p>Every claim originates from the provider's signature check. Nothing is read from
 * caller-supplied headers other than the token itself, so a client cannot assert who it is
 * or what it may do.
 */
public final class FirebaseIdentityVerifier implements IdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebaseIdentityVerifier.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseAuth firebaseAuth;
    private final ProviderIdentityMapper mapper;
    private final String fallbackIssuer;
    private final boolean checkRevoked;

    public FirebaseIdentityVerifier(
            FirebaseAuth firebaseAuth,
            ProviderIdentityMapper mapper,
            String projectId,
            boolean checkRevoked) {
        this.firebaseAuth = Objects.requireNonNull(firebaseAuth, "firebaseAuth is required");
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
        this.fallbackIssuer = "https://securetoken.google.com/"
                + Objects.requireNonNull(projectId, "projectId is required");
        this.checkRevoked = checkRevoked;
    }

    @Override
    public VerifiedIdentity verify(HttpServletRequest request) {
        String token = bearerToken(request);
        try {
            return mapper.toVerifiedIdentity(claimsOf(firebaseAuth.verifyIdToken(token, checkRevoked)));
        } catch (FirebaseAuthException exception) {
            // The reason a token failed is not the caller's business: distinguishing expired
            // from revoked from forged would help someone probing the boundary.
            log.debug("Rejected bearer token", exception);
            throw new AuthenticationRequiredException();
        } catch (IllegalArgumentException exception) {
            log.debug("Rejected malformed token claims", exception);
            throw new AuthenticationRequiredException();
        }
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = Objects.requireNonNull(request, "request is required")
                .getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new AuthenticationRequiredException();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new AuthenticationRequiredException();
        }
        return token;
    }

    private ProviderTokenClaims claimsOf(FirebaseToken token) {
        Map<String, Object> claims = token.getClaims();
        String issuer = stringClaim(claims, "iss");
        return new ProviderTokenClaims(
                issuer == null || issuer.isBlank() ? fallbackIssuer : issuer,
                token.getUid(),
                token.getEmail(),
                token.isEmailVerified(),
                token.getName(),
                roleClaims(claims));
    }

    /**
     * Reads role names from either a single {@code role} claim or a {@code roles} list, so the
     * deployment is not tied to one custom-claim shape.
     */
    private static Set<String> roleClaims(Map<String, Object> claims) {
        Set<String> roles = new LinkedHashSet<>();
        addRoleValue(roles, claims.get("role"));
        addRoleValue(roles, claims.get("roles"));
        return roles;
    }

    private static void addRoleValue(Set<String> roles, Object value) {
        if (value instanceof String text) {
            for (String part : text.split(",")) {
                String trimmed = part.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    roles.add(trimmed);
                }
            }
        } else if (value instanceof Collection<?> values) {
            for (Object element : values) {
                addRoleValue(roles, element);
            }
        }
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text ? text : null;
    }
}
