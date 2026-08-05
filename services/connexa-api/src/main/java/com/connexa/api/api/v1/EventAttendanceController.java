package com.connexa.api.api.v1;

import com.connexa.api.application.AttendanceService;
import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.VerifiedIdentity;
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

/**
 * Identity-scoped saved-event and RSVP endpoints.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class EventAttendanceController {

    private final AttendanceService attendanceService;
    private final IdentityAccessService identityAccessService;

    public EventAttendanceController(
            AttendanceService attendanceService,
            IdentityAccessService identityAccessService) {
        this.attendanceService = attendanceService;
        this.identityAccessService = identityAccessService;
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
