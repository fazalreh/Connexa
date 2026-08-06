package com.connexa.api.api.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The address storage returned, together with the name this service signed for. */
public record MediaConfirmRequest(
        @NotBlank @Size(max = 512) String url,
        @NotBlank @Size(max = 256) String publicId) {
}
