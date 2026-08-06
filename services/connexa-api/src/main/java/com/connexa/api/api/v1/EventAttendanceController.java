package com.connexa.api.api.v1;

import com.connexa.api.application.AttendanceService;
import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.realtime.CapacityBroadcaster;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Identity-scoped saved-event and RSVP endpoints.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class EventAttendanceController {

    /**
     * Longer than a proxy's idle timeout would allow on its own; the broadcaster's
     * heartbeat is what actually keeps the connection open, and this is the backstop
     * that eventually retires a forgotten stream.
     */
    private static final long STREAM_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final AttendanceService attendanceService;
    private final IdentityAccessService identityAccessService;
    private final CapacityBroadcaster capacityBroadcaster;

    public EventAttendanceController(
            AttendanceService attendanceService,
            IdentityAccessService identityAccessService,
            CapacityBroadcaster capacityBroadcaster) {
        this.attendanceService = attendanceService;
        this.identityAccessService = identityAccessService;
        this.capacityBroadcaster = capacityBroadcaster;
    }

    /**
     * Streams seat availability as it changes.
     *
     * <p>The current count is sent immediately on subscribe, so a client renders a real
     * number straight away rather than an empty placeholder until someone books.
     *
     * <p>Unlike the one-shot read below, this stays behind a verified identity. The value it
     * carries is equally public, but a stream is a connection held open for as long as the
     * client likes, and anonymous callers could open them without limit. A signed-out visitor
     * still sees the count from the plain read; they just do not see it change live.
     */
    @GetMapping(value = "/events/{eventId}/capacity/stream", produces = "text/event-stream")
    public SseEmitter streamCapacity(HttpServletRequest request, @PathVariable UUID eventId) {
        requireIdentity(request);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        capacityBroadcaster.subscribe(eventId, emitter);
        try {
            emitter.send(SseEmitter.event().name("capacity")
                    .data(EventCapacityResponse.from(attendanceService.findCapacity(eventId))));
        } catch (java.io.IOException disconnected) {
            emitter.completeWithError(disconnected);
        }
        return emitter;
    }

    @GetMapping("/me")
    public CurrentIdentityResponse currentIdentity(HttpServletRequest request) {
        return CurrentIdentityResponse.from(requireIdentity(request));
    }

    @GetMapping("/me/attendance")
    public PageResponse<AttendanceStateResponse> listAttendance(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<AttendanceState> attendance = attendanceService.findAll(requireIdentity(request), page, size);
        List<AttendanceStateResponse> items = attendance.items().stream()
                .map(AttendanceStateResponse::from)
                .toList();
        return new PageResponse<>(items, attendance.page(), attendance.size(), attendance.total());
    }

    @GetMapping("/events/{eventId}/attendance")
    public AttendanceStateResponse getAttendance(
            HttpServletRequest request,
            @PathVariable UUID eventId) {
        return AttendanceStateResponse.from(attendanceService.find(requireIdentity(request), eventId));
    }

    /**
     * How many seats remain. Readable without signing in.
     *
     * <p>Seat availability is not private: it is the same number printed on a poster. Gating
     * it meant a visitor browsing the catalogue could not tell a full event from an open one,
     * which is precisely the fact that decides whether they bother to sign up at all.
     *
     * <p>The count is about the event, never about who is attending it. Nothing here reveals
     * an individual's plans — those stay behind {@link #getAttendance}.
     */
    @GetMapping("/events/{eventId}/capacity")
    public EventCapacityResponse getCapacity(@PathVariable UUID eventId) {
        return EventCapacityResponse.from(attendanceService.findCapacity(eventId));
    }

    @PutMapping("/events/{eventId}/saved")
    public AttendanceStateResponse saveEvent(HttpServletRequest request, @PathVariable UUID eventId) {
        return AttendanceStateResponse.from(attendanceService.save(requireIdentity(request), eventId));
    }

    @DeleteMapping("/events/{eventId}/saved")
    public AttendanceStateResponse unsaveEvent(HttpServletRequest request, @PathVariable UUID eventId) {
        return AttendanceStateResponse.from(attendanceService.unsave(requireIdentity(request), eventId));
    }

    @PutMapping("/events/{eventId}/rsvp")
    public AttendanceStateResponse setRsvp(
            HttpServletRequest request,
            @PathVariable UUID eventId,
            @Valid @RequestBody RsvpUpdateRequest update) {
        return AttendanceStateResponse.from(
                attendanceService.setRsvp(requireIdentity(request), eventId, update.status()));
    }

    @DeleteMapping("/events/{eventId}/rsvp")
    public AttendanceStateResponse clearRsvp(HttpServletRequest request, @PathVariable UUID eventId) {
        return AttendanceStateResponse.from(attendanceService.clearRsvp(requireIdentity(request), eventId));
    }

    private VerifiedIdentity requireIdentity(HttpServletRequest request) {
        return identityAccessService.requireVerifiedIdentity(request);
    }
}
