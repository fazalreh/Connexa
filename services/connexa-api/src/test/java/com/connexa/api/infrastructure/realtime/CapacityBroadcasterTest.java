package com.connexa.api.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.event.EventCapacity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class CapacityBroadcasterTest {

    /** Captures what was written and can be made to fail like a closed connection. */
    private static final class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean disconnected;

        @Override
        public void send(SseEmitter.SseEventBuilder builder) throws IOException {
            if (disconnected) {
                throw new IOException("connection closed");
            }
            // build() yields the wire fragments; only the String parts carry the payload,
            // the rest are field names and separators.
            StringBuilder payload = new StringBuilder();
            for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
                if (part.getData() instanceof String text) {
                    payload.append(text);
                }
            }
            sent.add(payload.toString());
        }
    }

    private static EventCapacity capacity(UUID eventId, int reserved) {
        return new EventCapacity(eventId, 10, reserved);
    }

    @Test
    @DisplayName("a watcher receives updates for its event")
    void deliversToWatchers() {
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();
        UUID eventId = UUID.randomUUID();
        RecordingEmitter watcher = new RecordingEmitter();
        broadcaster.subscribe(eventId, watcher);

        broadcaster.publish(capacity(eventId, 3));

        assertThat(watcher.sent).hasSize(1);
        assertThat(watcher.sent.get(0)).contains("\"reserved\":3").contains("\"spotsRemaining\":7");
    }

    @Test
    @DisplayName("every watcher of an event is updated")
    void deliversToAllWatchersOfAnEvent() {
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();
        UUID eventId = UUID.randomUUID();
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        broadcaster.subscribe(eventId, first);
        broadcaster.subscribe(eventId, second);

        broadcaster.publish(capacity(eventId, 1));

        assertThat(first.sent).hasSize(1);
        assertThat(second.sent).hasSize(1);
    }

    @Test
    @DisplayName("watchers of other events are not disturbed")
    void doesNotCrossEvents() {
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();
        UUID watched = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        RecordingEmitter watcher = new RecordingEmitter();
        broadcaster.subscribe(watched, watcher);

        broadcaster.publish(capacity(other, 5));

        assertThat(watcher.sent).isEmpty();
    }

    @Test
    @DisplayName("publishing with no watchers is harmless")
    void publishingToNobodyIsSafe() {
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();

        broadcaster.publish(capacity(UUID.randomUUID(), 2));

        assertThat(broadcaster.watcherCount(UUID.randomUUID())).isZero();
    }

    @Test
    @DisplayName("a disconnected watcher is dropped on the next publish")
    void dropsDisconnectedWatchers() {
        // A closed connection is only detectable by writing to it, so the failed send is
        // the removal signal.
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();
        UUID eventId = UUID.randomUUID();
        RecordingEmitter watcher = new RecordingEmitter();
        broadcaster.subscribe(eventId, watcher);
        watcher.disconnected = true;

        broadcaster.publish(capacity(eventId, 1));

        assertThat(broadcaster.watcherCount(eventId)).isZero();
    }

    @Test
    @DisplayName("a heartbeat drops connections that have gone away")
    void heartbeatPrunesDeadConnections() {
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();
        UUID eventId = UUID.randomUUID();
        RecordingEmitter alive = new RecordingEmitter();
        RecordingEmitter dead = new RecordingEmitter();
        broadcaster.subscribe(eventId, alive);
        broadcaster.subscribe(eventId, dead);
        dead.disconnected = true;

        broadcaster.heartbeat();

        assertThat(broadcaster.watcherCount(eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a completed stream is unregistered on the next publish")
    void completedStreamIsUnregistered() {
        // Without pruning, the map grows for the lifetime of the process. A real emitter
        // is used here because a completed one rejects further writes, which is the
        // signal the broadcaster acts on.
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();
        UUID eventId = UUID.randomUUID();
        SseEmitter watcher = new SseEmitter();
        broadcaster.subscribe(eventId, watcher);
        assertThat(broadcaster.watcherCount(eventId)).isEqualTo(1);

        watcher.complete();
        broadcaster.publish(capacity(eventId, 1));

        assertThat(broadcaster.watcherCount(eventId)).isZero();
    }

    @Test
    @DisplayName("an unbounded event reports no limit rather than a number")
    void unboundedCapacityIsSerialisedAsNull() {
        CapacityBroadcaster broadcaster = new CapacityBroadcaster();
        UUID eventId = UUID.randomUUID();
        RecordingEmitter watcher = new RecordingEmitter();
        broadcaster.subscribe(eventId, watcher);

        broadcaster.publish(new EventCapacity(eventId, null, 4));

        assertThat(watcher.sent.get(0))
                .contains("\"totalCapacity\":null")
                .contains("\"spotsRemaining\":null")
                .contains("\"full\":false");
    }
}
