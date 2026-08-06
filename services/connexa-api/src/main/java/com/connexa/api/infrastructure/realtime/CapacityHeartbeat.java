package com.connexa.api.infrastructure.realtime;

import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps open capacity streams alive.
 *
 * <p>Proxies and load balancers close a connection that has been silent, so an event nobody
 * is booking would lose its watchers within minutes. A periodic comment costs almost nothing
 * and is invisible to clients, which ignore comment lines.
 */
@Component
public class CapacityHeartbeat {

    private final CapacityBroadcaster broadcaster;

    public CapacityHeartbeat(CapacityBroadcaster broadcaster) {
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster is required");
    }

    /** Comfortably inside the 60s idle timeout most intermediaries default to. */
    @Scheduled(fixedDelay = 25_000L)
    void keepStreamsOpen() {
        broadcaster.heartbeat();
    }
}
