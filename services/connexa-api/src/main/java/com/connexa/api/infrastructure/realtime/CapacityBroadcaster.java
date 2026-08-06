package com.connexa.api.infrastructure.realtime;

import com.connexa.api.domain.event.EventCapacity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes seat-count changes to everyone currently watching an event.
 *
 * <p>Subscribers are held per event rather than in one list, so publishing touches only the
 * people watching that event instead of walking every open connection.
 *
 * <p>A dead connection is not detectable until something is written to it, so send failures
 * are the removal signal rather than an error: a client that closed its tab, lost signal, or
 * went through a proxy timeout all look the same from here.
 */
@Component
public class CapacityBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(CapacityBroadcaster.class);

    private final Map<UUID, List<SseEmitter>> watchersByEvent = new ConcurrentHashMap<>();

    /**
     * Registers a watcher and wires its own cleanup.
     *
     * <p>Every terminal callback removes the emitter. Without that the map grows for the
     * lifetime of the process, because an emitter that has completed is never reused but
     * would still be held.
     */
    public SseEmitter subscribe(UUID eventId, SseEmitter emitter) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(emitter, "emitter is required");

        watchersByEvent.computeIfAbsent(eventId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(eventId, emitter));
        emitter.onTimeout(() -> remove(eventId, emitter));
        emitter.onError(throwable -> remove(eventId, emitter));
        return emitter;
    }

    /** Sends the current count to every watcher of this event. */
    public void publish(EventCapacity capacity) {
        Objects.requireNonNull(capacity, "capacity is required");
        List<SseEmitter> watchers = watchersByEvent.get(capacity.eventId());
        if (watchers == null || watchers.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : watchers) {
            send(capacity.eventId(), emitter, "capacity", payload(capacity));
        }
    }

    /**
     * Sends a comment to every open connection.
     *
     * <p>Intermediaries close a stream that has been quiet, so an event nobody is booking
     * would drop its watchers within minutes without this.
     */
    public void heartbeat() {
        watchersByEvent.forEach((eventId, watchers) -> {
            for (SseEmitter emitter : watchers) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException disconnected) {
                    remove(eventId, emitter);
                }
            }
        });
    }

    public int watcherCount(UUID eventId) {
        List<SseEmitter> watchers = watchersByEvent.get(eventId);
        return watchers == null ? 0 : watchers.size();
    }

    private void send(UUID eventId, SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException disconnected) {
            // Expected whenever a client goes away; not worth an error-level log.
            log.debug("Dropping a closed capacity watcher for event {}", eventId);
            remove(eventId, emitter);
        }
    }

    private void remove(UUID eventId, SseEmitter emitter) {
        List<SseEmitter> watchers = watchersByEvent.get(eventId);
        if (watchers == null) {
            return;
        }
        watchers.remove(emitter);
        // Drop the empty list too, or the map retains one entry per event ever watched.
        watchersByEvent.remove(eventId, List.of());
        if (watchers.isEmpty()) {
            watchersByEvent.remove(eventId, watchers);
        }
    }

    /** Hand-built rather than serialised: the shape is fixed and tiny. */
    private static String payload(EventCapacity capacity) {
        Integer remaining = capacity.spotsRemaining();
        return "{\"eventId\":\"" + capacity.eventId()
                + "\",\"totalCapacity\":" + (capacity.totalCapacity() == null ? "null" : capacity.totalCapacity())
                + ",\"spotsRemaining\":" + (remaining == null ? "null" : remaining)
                + ",\"reserved\":" + capacity.reserved()
                + ",\"full\":" + capacity.full() + "}";
    }
}
