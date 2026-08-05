package com.connexa.api.domain.event;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventNotFoundException(UUID eventId) {
        super("No event exists for id " + eventId);
    }
}
