package com.connexa.api.api.v1;

import com.connexa.api.application.IngestionService;
import com.connexa.api.infrastructure.ingestion.IngestionOutcome;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intake for approved announcement readers.
 *
 * <p>Authenticated by a shared token rather than a user identity: the caller is a scheduled
 * process with nobody to sign in. {@link IngestionTokenFilter} verifies that token before
 * this controller is reached, so an unauthorised request never reaches request binding.
 */
@Validated
@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/events")
    public ResponseEntity<IngestionEventResponse> submit(
            @Valid @RequestBody IngestionEventRequest submission) {
        IngestionOutcome outcome = ingestionService.submit(
                submission.sourceSystem(),
                submission.sourceRecordId(),
                submission.contentHash(),
                submission.title(),
                submission.summary(),
                submission.startsAt(),
                submission.endsAt(),
                submission.timeZone(),
                submission.venueName(),
                submission.category(),
                submission.organizerName(),
                submission.totalCapacity());

        // 200 rather than 201 on a repeat submission, so a reader can tell whether it
        // actually added anything without comparing identifiers.
        return ResponseEntity
                .status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(IngestionEventResponse.from(outcome));
    }
}
