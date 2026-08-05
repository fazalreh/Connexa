package com.connexa.api.domain.event;

import java.util.UUID;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(UUID eventId) {
        super("No event exists for id " + eventId);
    }
}
