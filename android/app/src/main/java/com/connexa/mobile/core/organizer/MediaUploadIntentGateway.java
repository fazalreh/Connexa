package com.connexa.mobile.core.organizer;

import java.io.IOException;

/**
 * Boundary through which organizer features ask the Connexa service to accept a media intent.
 *
 * <p>A transport implementation belongs outside feature code. It may later coordinate content
 * transfer, but this interface keeps UI state independent of any storage provider.</p>
 */
public interface MediaUploadIntentGateway {

    MediaUploadTicket requestUpload(MediaUploadIntent intent) throws IOException;
}
