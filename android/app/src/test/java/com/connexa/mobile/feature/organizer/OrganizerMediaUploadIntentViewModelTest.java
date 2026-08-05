package com.connexa.mobile.feature.organizer;

import static org.junit.Assert.assertEquals;

import com.connexa.mobile.core.organizer.MediaUploadIntent;
import com.connexa.mobile.core.organizer.OrganizerMediaType;
import java.util.UUID;
import org.junit.Test;

public class OrganizerMediaUploadIntentViewModelTest {

    @Test
    public void exposesOnlyTheSafeMetadataNeededByTheEditor() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        MediaUploadIntent intent = new MediaUploadIntent(
                eventId,
                requestId,
                OrganizerMediaType.ATTACHMENT,
                "agenda.pdf",
                "application/pdf",
                4_096);

        OrganizerMediaUploadIntentViewModel viewModel = OrganizerMediaUploadIntentViewModel.from(intent);

        assertEquals(eventId, viewModel.getEventId());
        assertEquals(requestId, viewModel.getRequestId());
        assertEquals(OrganizerMediaType.ATTACHMENT, viewModel.getMediaType());
        assertEquals("agenda.pdf", viewModel.getFileName());
        assertEquals("application/pdf", viewModel.getMimeType());
        assertEquals(4_096, viewModel.getContentLengthBytes());
    }
}
