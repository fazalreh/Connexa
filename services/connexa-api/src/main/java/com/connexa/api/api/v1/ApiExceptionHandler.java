package com.connexa.api.api.v1;

import com.connexa.api.domain.assistant.AssistantUnavailableException;
import com.connexa.api.domain.attendance.EventAtCapacityException;
import com.connexa.api.domain.checkin.InvalidCheckInPassException;
import com.connexa.api.domain.checkin.NotAttendingException;
import com.connexa.api.domain.event.EventNotFoundException;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.AuthenticationRequiredException;
import com.connexa.api.domain.media.MediaUnavailableException;
import com.connexa.api.domain.media.UnsupportedMediaException;
import com.connexa.api.domain.notification.NotificationNotFoundException;
import com.connexa.api.domain.organizer.DraftAlreadyPublishedException;
import com.connexa.api.domain.organizer.OrganizerDraftNotFoundException;
import com.connexa.api.domain.organizer.PublicationUnavailableException;
import com.connexa.api.domain.waitlist.EventNotFullException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String PROBLEM_TYPE_BASE = "https://api.connexa.local/problems/";

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEventNotFound(
            EventNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "event-not-found",
                "Event not found",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotificationNotFound(
            NotificationNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "notification-not-found",
                "Notification not found",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(EventAtCapacityException.class)
    public ResponseEntity<ProblemDetail> handleEventAtCapacity(
            EventAtCapacityException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "event-at-capacity",
                "Event is full",
                "This event has no remaining capacity.",
                request);
    }

    @ExceptionHandler(OrganizerDraftNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDraftNotFound(
            OrganizerDraftNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "organizer-draft-not-found",
                "Draft not found",
                "No draft exists for the supplied ID.",
                request);
    }

    @ExceptionHandler(DraftAlreadyPublishedException.class)
    public ResponseEntity<ProblemDetail> handleDraftAlreadyPublished(
            DraftAlreadyPublishedException exception,
            HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.CONFLICT,
                "draft-already-published",
                "Draft already published",
                "This draft has already been published as an event.",
                request);
        ProblemDetail problem = response.getBody();
        if (problem != null) {
            problem.setProperty("publishedEventId", exception.publishedEventId().toString());
        }
        return response;
    }

    @ExceptionHandler(PublicationUnavailableException.class)
    public ResponseEntity<ProblemDetail> handlePublicationUnavailable(
            PublicationUnavailableException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "publication-unavailable",
                "Publishing unavailable",
                "Publishing requires durable storage, which is not currently configured.",
                request);
    }

    @ExceptionHandler(InvalidCheckInPassException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCheckInPass(
            InvalidCheckInPassException exception,
            HttpServletRequest request) {
        // The message is written for the steward holding the scanner, and is safe to show:
        // it never distinguishes a forged signature from an unknown one.
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "invalid-check-in-pass",
                "Code not accepted",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(NotAttendingException.class)
    public ResponseEntity<ProblemDetail> handleNotAttending(
            NotAttendingException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "not-attending",
                "Not on the list",
                "This guest does not hold a seat for this event.",
                request);
    }

    @ExceptionHandler(EventNotFullException.class)
    public ResponseEntity<ProblemDetail> handleEventNotFull(
            EventNotFullException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "event-not-full",
                "Seats still available",
                "This event still has seats, so respond directly instead of queueing.",
                request);
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationRequired(
            AuthenticationRequiredException exception,
            HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.UNAUTHORIZED,
                "authentication-required",
                "Authentication required",
                "A verified identity is required for this operation.",
                request);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(response.getBody());
    }

    @ExceptionHandler(ActorNotAuthorizedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationFailure(
            ActorNotAuthorizedException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Not authorized",
                "Your account does not have permission for this operation.",
                request);
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleAssistantUnavailable(
            AssistantUnavailableException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "assistant-unavailable",
                "Assistant unavailable",
                "The assistant is not available at this time.",
                request);
    }

    @ExceptionHandler(MediaUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleMediaUnavailable(
            MediaUnavailableException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "media-unavailable",
                "Image uploads unavailable",
                "Image uploads are not available at this time.",
                request);
    }

    /**
     * The message is passed through rather than replaced: it names the format or size limit
     * the organizer needs to know, and a generic reply would leave them guessing.
     */
    @ExceptionHandler(UnsupportedMediaException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMedia(
            UnsupportedMediaException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "unsupported-media",
                "Image not accepted",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Invalid request",
                "One or more request fields are invalid.",
                request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + type));
        problem.setTitle(title);
        problem.setProperty("requestId", RequestIdFilter.current(request));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
