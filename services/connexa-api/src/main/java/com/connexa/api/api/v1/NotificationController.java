package com.connexa.api.api.v1;

import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.application.NotificationInboxService;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.notification.NotificationItem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity-scoped inbox contract. It remains empty until a trusted notification producer is connected.
 */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final IdentityAccessService identityAccessService;
    private final NotificationInboxService notificationInboxService;

    public NotificationController(
            IdentityAccessService identityAccessService,
            NotificationInboxService notificationInboxService) {
        this.identityAccessService = identityAccessService;
        this.notificationInboxService = notificationInboxService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<NotificationItem> notifications = notificationInboxService.findAll(
                identityAccessService.requireVerifiedIdentity(request), page, size);
        List<NotificationResponse> items = notifications.items().stream()
                .map(NotificationResponse::from)
                .toList();
        return new PageResponse<>(items, notifications.page(), notifications.size(), notifications.total());
    }

    @PutMapping("/{notificationId}/read")
    public NotificationResponse markRead(HttpServletRequest request, @PathVariable UUID notificationId) {
        return NotificationResponse.from(notificationInboxService.markRead(
                identityAccessService.requireVerifiedIdentity(request), notificationId));
    }
}
