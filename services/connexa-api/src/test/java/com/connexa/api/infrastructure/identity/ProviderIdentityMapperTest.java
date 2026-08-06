package com.connexa.api.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderIdentityMapperTest {

    private static final String ISSUER = "https://securetoken.google.com/connexa-3dc6b";

    @Test
    @DisplayName("a verified token always yields at least the attendee role")
    void grantsAttendeeByDefault() {
        VerifiedIdentity identity = mapper().toVerifiedIdentity(
                claims("alice@example.com", true, Set.of()));

        assertThat(identity.roles()).containsExactly(IdentityRole.ATTENDEE);
        assertThat(identity.key().issuer()).isEqualTo(ISSUER);
        assertThat(identity.key().subject()).isEqualTo("uid-1");
    }

    @Test
    @DisplayName("an allowlisted verified email is granted the organizer role")
    void allowlistGrantsOrganizer() {
        VerifiedIdentity identity = mapper("organizer@connexa.test")
                .toVerifiedIdentity(claims("organizer@connexa.test", true, Set.of()));

        assertThat(identity.roles())
                .containsExactlyInAnyOrder(IdentityRole.ATTENDEE, IdentityRole.ORGANIZER);
    }

    @Test
    @DisplayName("an unverified email never satisfies the allowlist")
    void unverifiedEmailIsRefusedOrganizer() {
        // Anyone can register any address with email/password sign-up, so without this the
        // allowlist would be trivially claimable by an attacker.
        VerifiedIdentity identity = mapper("organizer@connexa.test")
                .toVerifiedIdentity(claims("organizer@connexa.test", false, Set.of()));

        assertThat(identity.roles()).containsExactly(IdentityRole.ATTENDEE);
    }

    @Test
    @DisplayName("the verification requirement can be relaxed for local use")
    void unverifiedEmailAllowedWhenRequirementRelaxed() {
        ProviderIdentityMapper relaxed =
                new ProviderIdentityMapper(List.of("organizer@connexa.test"), false);

        VerifiedIdentity identity =
                relaxed.toVerifiedIdentity(claims("organizer@connexa.test", false, Set.of()));

        assertThat(identity.roles()).contains(IdentityRole.ORGANIZER);
    }

    @Test
    @DisplayName("allowlist matching ignores case and surrounding whitespace")
    void allowlistMatchingIsCaseAndWhitespaceInsensitive() {
        ProviderIdentityMapper mapper =
                new ProviderIdentityMapper(List.of("  Organizer@Connexa.Test  "), true);

        VerifiedIdentity identity =
                mapper.toVerifiedIdentity(claims("ORGANIZER@connexa.test", true, Set.of()));

        assertThat(identity.roles()).contains(IdentityRole.ORGANIZER);
    }

    @Test
    @DisplayName("an email outside the allowlist is not an organizer")
    void nonAllowlistedEmailIsNotOrganizer() {
        VerifiedIdentity identity = mapper("organizer@connexa.test")
                .toVerifiedIdentity(claims("someone@example.com", true, Set.of()));

        assertThat(identity.roles()).containsExactly(IdentityRole.ATTENDEE);
    }

    @Test
    @DisplayName("a provider-asserted role grants organizer without any allowlist")
    void providerClaimGrantsOrganizer() {
        VerifiedIdentity identity = mapper()
                .toVerifiedIdentity(claims("alice@example.com", true, Set.of("organizer")));

        assertThat(identity.roles()).contains(IdentityRole.ORGANIZER);
    }

    @Test
    @DisplayName("a provider-asserted role does not require a verified email")
    void providerClaimIgnoresEmailVerification() {
        // Only an administrator can set a provider role, so it carries no impersonation risk.
        VerifiedIdentity identity = mapper()
                .toVerifiedIdentity(claims("alice@example.com", false, Set.of("organizer")));

        assertThat(identity.roles()).contains(IdentityRole.ORGANIZER);
    }

    @Test
    @DisplayName("a token with no email at all is still a valid attendee")
    void missingEmailStillYieldsAttendee() {
        VerifiedIdentity identity = mapper("organizer@connexa.test")
                .toVerifiedIdentity(claims(null, false, Set.of()));

        assertThat(identity.roles()).containsExactly(IdentityRole.ATTENDEE);
        assertThat(identity.email()).isNull();
    }

    @Test
    @DisplayName("unrecognised provider roles are ignored rather than rejected")
    void unknownRolesAreIgnored() {
        VerifiedIdentity identity = mapper()
                .toVerifiedIdentity(claims("alice@example.com", true, Set.of("wizard", "moderator")));

        assertThat(identity.roles())
                .containsExactlyInAnyOrder(IdentityRole.ATTENDEE, IdentityRole.MODERATOR);
    }

    @Test
    @DisplayName("an empty allowlist grants nobody the organizer role")
    void emptyAllowlistGrantsNothing() {
        VerifiedIdentity identity = mapper()
                .toVerifiedIdentity(claims("anyone@example.com", true, Set.of()));

        assertThat(identity.roles()).containsExactly(IdentityRole.ATTENDEE);
    }

    private static ProviderIdentityMapper mapper(String... organizerEmails) {
        return new ProviderIdentityMapper(List.of(organizerEmails), true);
    }

    private static ProviderTokenClaims claims(String email, boolean verified, Set<String> roles) {
        return new ProviderTokenClaims(ISSUER, "uid-1", email, verified, "Test User", roles);
    }
}
