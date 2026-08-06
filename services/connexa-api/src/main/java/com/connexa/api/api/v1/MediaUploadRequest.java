package com.connexa.api.api.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * What the organizer intends to upload.
 *
 * <p>The size is stated up front so an image that is too large is refused before the
 * transfer starts rather than after it finishes.
 */
public record MediaUploadRequest(
        @NotBlank @Size(max = 128) String mimeType,
        @Positive long contentLengthBytes) {
}
