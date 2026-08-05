package com.connexa.mobile.feature.organizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.organizer.OrganizerDraftField;
import com.connexa.mobile.core.organizer.OrganizerEventDraft;
import java.time.Instant;
import org.junit.Test;

public class OrganizerEventDraftFormViewModelTest {

    @Test
    public void exposesWhetherTheEditorCanSubmitTheCurrentDraft() {
        OrganizerEventDraftFormViewModel valid = OrganizerEventDraftFormViewModel.from(validDraft());
        OrganizerEventDraftFormViewModel invalid = OrganizerEventDraftFormViewModel.from(new OrganizerEventDraft(
                "",
                "",
                "",
                null,
                null,
                "",
                null));

        assertTrue(valid.canSubmit());
        assertNull(valid.firstInvalidField());
        assertFalse(invalid.canSubmit());
        assertEquals(OrganizerDraftField.TITLE, invalid.firstInvalidField());
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
