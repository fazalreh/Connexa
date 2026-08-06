package com.connexa.mobile.core.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AuthSessionFactoryTest {

    @Test
    public void providerNameIsUsedWhenPresent() {
        assertEquals("Fazal Rehman",
                AuthSessionFactory.displayNameFor("Fazal Rehman", "anything@example.com"));
    }

    @Test
    public void surroundingWhitespaceIsTrimmed() {
        assertEquals("Fazal Rehman",
                AuthSessionFactory.displayNameFor("  Fazal Rehman  ", "anything@example.com"));
    }

    @Test
    public void missingNameFallsBackToTheAddress() {
        // Console-created accounts have no profile name; sign-in must not fail over it.
        assertEquals("Organizer", AuthSessionFactory.displayNameFor(null, "organizer@connexa.test"));
    }

    @Test
    public void blankNameFallsBackToTheAddress() {
        assertEquals("Attendee", AuthSessionFactory.displayNameFor("   ", "attendee@connexa.test"));
    }

    @Test
    public void separatorsInTheLocalPartBecomeSpaces() {
        assertEquals("Fazal Rehman",
                AuthSessionFactory.displayNameFor(null, "fazal.rehman@example.com"));
        assertEquals("Fazal Rehman",
                AuthSessionFactory.displayNameFor(null, "fazal_rehman@example.com"));
        assertEquals("Fazal Rehman",
                AuthSessionFactory.displayNameFor(null, "fazal-rehman@example.com"));
    }

    @Test
    public void unusableAddressStillYieldsANonBlankName() {
        // AuthSession rejects a blank display name, so the fallback must always produce one.
        assertEquals("Connexa member", AuthSessionFactory.displayNameFor(null, "@example.com"));
        assertEquals("Connexa member", AuthSessionFactory.displayNameFor(null, null));
        assertEquals("Connexa member", AuthSessionFactory.displayNameFor("", ""));
    }

    @Test
    public void sessionIsBuiltWithTheDerivedName() {
        AuthSession session = AuthSessionFactory.create(
                "uid-1", "organizer@connexa.test", null, AccountRole.ORGANIZER);

        assertEquals("uid-1", session.getSubjectId());
        assertEquals("organizer@connexa.test", session.getEmail());
        assertEquals("Organizer", session.getDisplayName());
        assertEquals(AccountRole.ORGANIZER, session.getAccountRole());
    }
}
