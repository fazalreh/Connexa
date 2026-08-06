package com.connexa.api.api.v1;

import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.application.MediaUploadService;
import com.connexa.api.domain.media.MediaUpload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizer image uploads.
 *
 * <p>Two steps, because the image itself does not pass through this service: the first
 * authorises an upload and signs it, the second confirms that what storage returned is
 * really the upload that was authorised.
 */
@Validated
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaUploadService mediaUploadService;
    private final IdentityAccessService identityAccessService;

    public MediaController(
            MediaUploadService mediaUploadService, IdentityAccessService identityAccessService) {
        this.mediaUploadService = mediaUploadService;
        this.identityAccessService = identityAccessService;
    }

    @PostMapping("/uploads")
    public MediaUploadResponse authorize(
            HttpServletRequest request, @Valid @RequestBody MediaUploadRequest uploadRequest) {
        return MediaUploadResponse.from(mediaUploadService.authorizeUpload(
                identityAccessService.requireVerifiedIdentity(request),
                new MediaUpload(uploadRequest.mimeType(), uploadRequest.contentLengthBytes())));
    }

    @PostMapping("/uploads/confirmations")
    public MediaConfirmResponse confirm(
            HttpServletRequest request, @Valid @RequestBody MediaConfirmRequest confirmRequest) {
        // Verifying the identity again keeps the endpoint closed to anonymous callers even
        // though the address itself is what is being checked.
        identityAccessService.requireVerifiedIdentity(request);
        return new MediaConfirmResponse(
                mediaUploadService.confirmUpload(confirmRequest.url(), confirmRequest.publicId()));
    }
}
