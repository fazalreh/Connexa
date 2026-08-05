package com.connexa.api.api.v1;

import com.connexa.api.domain.event.EventNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Invalid request",
                exception.getMessage(),
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
