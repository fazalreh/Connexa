package com.connexa.api.api.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The text read from an attendee's QR code. */
public record CheckInRedeemRequest(@NotBlank @Size(max = 512) String pass) {
}
