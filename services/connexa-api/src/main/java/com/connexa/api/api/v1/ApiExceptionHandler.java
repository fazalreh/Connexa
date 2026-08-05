package com.connexa.api.api.v1;

import com.connexa.api.domain.assistant.AssistantUnavailableException;
import com.connexa.api.domain.event.EventNotFoundException;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.AuthenticationRequiredException;
import com.connexa.api.domain.notification.NotificationNotFoundException;
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
