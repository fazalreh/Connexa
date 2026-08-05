package com.connexa.api.api.v1;

import com.connexa.api.domain.attendance.RsvpStatus;
import jakarta.validation.constraints.NotNull;

public record RsvpUpdateRequest(@NotNull RsvpStatus status) {
}
