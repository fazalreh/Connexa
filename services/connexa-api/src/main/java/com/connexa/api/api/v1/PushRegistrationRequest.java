package com.connexa.api.api.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param token the device's messaging token
 * @param platform which client produced it, for diagnostics only
 */
public record PushRegistrationRequest(
        @NotBlank @Size(max = 512) String token,
        @Size(max = 32) String platform) {
}
