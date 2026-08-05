package com.connexa.mobile.core.organizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import org.junit.Test;

public class OrganizerEventDraftValidatorTest {

    @Test
    public void acceptsACompleteWellOrderedDraft() {
        OrganizerEventDraftValidationResult result = OrganizerEventDraftValidator.validate(validDraft());

        assertTrue(result.isValid());
        assertNull(result.firstInvalidField());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void returnsFieldSpecificFeedbackForIncompleteAndInvalidValues() {
        Instant startsAt = Instant.parse("2026-09-10T10:00:00Z");
        OrganizerEventDraft draft = new OrganizerEventDraft(
                " ",
                "Short",
                "X",
                startsAt,
                startsAt,
                " ",
                0);

        OrganizerEventDraftValidationResult result = OrganizerEventDraftValidator.validate(draft);

        assertFalse(result.isValid());
        assertEquals(OrganizerDraftField.TITLE, result.firstInvalidField());
        assertEquals("Title is required.", result.errorFor(OrganizerDraftField.TITLE));
        assertEquals(
                "Description must be at least 20 characters.",
                result.errorFor(OrganizerDraftField.DESCRIPTION));
        assertEquals(
                "Location must be at least 2 characters.",
                result.errorFor(OrganizerDraftField.LOCATION));
        assertEquals(
                "End time must be after the start time.",
                result.errorFor(OrganizerDraftField.ENDS_AT));
        assertEquals("Category is required.", result.errorFor(OrganizerDraftField.CATEGORY));
        assertEquals(
                "Capacity must be between 1 and 100000.",
                result.errorFor(OrganizerDraftField.CAPACITY));
    }

    @Test
    public void reportsEachMissingTimeSeparately() {
        OrganizerEventDraft draft = new OrganizerEventDraft(
                "Community workshop",
                "A practical session that gives people time to build useful skills.",
                "Innovation Hub",
                null,
                null,
                "Workshop",
                40);

        OrganizerEventDraftValidationResult result = OrganizerEventDraftValidator.validate(draft);

        assertEquals("Start time is required.", result.errorFor(OrganizerDraftField.STARTS_AT));
        assertEquals("End time is required.", result.errorFor(OrganizerDraftField.ENDS_AT));
    }

    private static OrganizerEventDraft validDraft() {
        return new OrganizerEventDraft(
                "Community design workshop",
                "A practical session for people who want to improve community services together.",
                "Innovation Hub",
                Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-10T12:00:00Z"),
                "Workshop",
                40);
    }
}
