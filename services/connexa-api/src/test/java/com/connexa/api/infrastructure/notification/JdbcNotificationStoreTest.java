package com.connexa.api.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.notification.NotificationItem;
import com.connexa.api.domain.notification.NotificationType;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import com.connexa.api.infrastructure.persistence.SqlValues;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcNotificationStoreTest {

    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");
    private static final IdentityKey BOB = new IdentityKey("https://identity.connexa", "bob");
    private static final Instant CREATED = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant READ_AT = Instant.parse("2026-06-02T10:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcNotificationStore store;

    @BeforeEach
    void setUp() {
        jdbc = JdbcAdapterTestSupport.freshDatabase();
        store = new JdbcNotificationStore(jdbc);
    }

    @Test
    @DisplayName("an inbox round-trips every stored field")
    void readsBackAllFields() {
        UUID eventId = UUID.randomUUID();
        UUID id = insert(ALICE, NotificationType.EVENT_REMINDER, "Starting soon", "Doors open at six.",
                eventId, CREATED, null);

        NotificationItem item = store.findFor(ALICE, 0, 20).items().get(0);

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.type()).isEqualTo(NotificationType.EVENT_REMINDER);
        assertThat(item.title()).isEqualTo("Starting soon");
        assertThat(item.body()).isEqualTo("Doors open at six.");
        assertThat(item.eventId()).isEqualTo(eventId);
        assertThat(item.createdAt()).isEqualTo(CREATED);
        assertThat(item.readAt()).isNull();
        assertThat(item.read()).isFalse();
    }

    @Test
    @DisplayName("a notification without an event id is allowed")
    void allowsNotificationWithoutEvent() {
        insert(ALICE, NotificationType.SYSTEM, "Welcome", "Your account is ready.", null, CREATED, null);

        assertThat(store.findFor(ALICE, 0, 20).items().get(0).eventId()).isNull();
    }

    @Test
    @DisplayName("the inbox is ordered newest first")
    void ordersNewestFirst() {
        insert(ALICE, NotificationType.SYSTEM, "Oldest", "First message", null, CREATED, null);
        insert(ALICE, NotificationType.SYSTEM, "Newest", "Third message", null,
                CREATED.plusSeconds(7_200), null);
        insert(ALICE, NotificationType.SYSTEM, "Middle", "Second message", null,
                CREATED.plusSeconds(3_600), null);

        assertThat(store.findFor(ALICE, 0, 20).items()).extracting(NotificationItem::title)
                .containsExactly("Newest", "Middle", "Oldest");
    }

    @Test
    @DisplayName("paging reports the full total on every page")
    void pagesInboxWhileReportingFullTotal() {
        insert(ALICE, NotificationType.SYSTEM, "One", "Body one", null, CREATED, null);
        insert(ALICE, NotificationType.SYSTEM, "Two", "Body two", null, CREATED.plusSeconds(60), null);
        insert(ALICE, NotificationType.SYSTEM, "Three", "Body three", null, CREATED.plusSeconds(120), null);

        PageResponse<NotificationItem> first = store.findFor(ALICE, 0, 2);
        PageResponse<NotificationItem> second = store.findFor(ALICE, 1, 2);

        assertThat(first.total()).isEqualTo(3L);
        assertThat(first.items()).extracting(NotificationItem::title).containsExactly("Three", "Two");
        assertThat(second.total()).isEqualTo(3L);
        assertThat(second.items()).extracting(NotificationItem::title).containsExactly("One");
    }

    @Test
    @DisplayName("a page past the end is empty but still reports the total")
    void returnsEmptyPageBeyondEnd() {
        insert(ALICE, NotificationType.SYSTEM, "Only", "Body", null, CREATED, null);

        PageResponse<NotificationItem> page = store.findFor(ALICE, 5, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(1L);
    }

    @Test
    @DisplayName("one identity never sees another identity's inbox")
    void isolatesInboxByIdentity() {
        insert(ALICE, NotificationType.SYSTEM, "For Alice", "Body", null, CREATED, null);
        insert(BOB, NotificationType.SYSTEM, "For Bob", "Body", null, CREATED, null);

        assertThat(store.findFor(ALICE, 0, 20).items()).extracting(NotificationItem::title)
                .containsExactly("For Alice");
        assertThat(store.findFor(BOB, 0, 20).items()).extracting(NotificationItem::title)
                .containsExactly("For Bob");
    }

    @Test
    @DisplayName("marking read records the timestamp and survives a re-read")
    void marksNotificationRead() {
        UUID id = insert(ALICE, NotificationType.SYSTEM, "Unread", "Body", null, CREATED, null);

        NotificationItem marked = store.markRead(ALICE, id, READ_AT).orElseThrow();

        assertThat(marked.readAt()).isEqualTo(READ_AT);
        assertThat(marked.read()).isTrue();
        assertThat(store.findFor(ALICE, 0, 20).items().get(0).readAt()).isEqualTo(READ_AT);
    }

    @Test
    @DisplayName("marking an already-read notification keeps the original timestamp")
    void keepsOriginalReadTimestamp() {
        UUID id = insert(ALICE, NotificationType.SYSTEM, "Read", "Body", null, CREATED, READ_AT);

        NotificationItem marked = store.markRead(ALICE, id, READ_AT.plusSeconds(3_600)).orElseThrow();

        assertThat(marked.readAt()).isEqualTo(READ_AT);
        assertThat(store.findFor(ALICE, 0, 20).items().get(0).readAt()).isEqualTo(READ_AT);
    }

    @Test
    @DisplayName("an identity cannot mark another identity's notification read")
    void refusesToMarkAnotherIdentitysNotification() {
        UUID bobsNotification =
                insert(BOB, NotificationType.SYSTEM, "For Bob", "Body", null, CREATED, null);

        Optional<NotificationItem> result = store.markRead(ALICE, bobsNotification, READ_AT);

        assertThat(result).isEmpty();
        assertThat(store.findFor(BOB, 0, 20).items().get(0).readAt()).isNull();
    }

    @Test
    @DisplayName("marking an unknown notification is empty")
    void returnsEmptyForUnknownNotification() {
        assertThat(store.markRead(ALICE, UUID.randomUUID(), READ_AT)).isEmpty();
    }

    @Test
    @DisplayName("page and size bounds are rejected")
    void rejectsInvalidPaging() {
        assertThatThrownBy(() -> store.findFor(ALICE, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.findFor(ALICE, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.findFor(ALICE, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID insert(
            IdentityKey identity,
            NotificationType type,
            String title,
            String body,
            UUID eventId,
            Instant createdAt,
            Instant readAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into notifications (id, identity_issuer, identity_subject, type, title,"
                        + " body, event_id, created_at, read_at)"
                        + " values (:id, :issuer, :subject, :type, :title, :body, :eventId,"
                        + " :createdAt, :readAt)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("issuer", identity.issuer())
                        .addValue("subject", identity.subject())
                        .addValue("type", type.name())
                        .addValue("title", title)
                        .addValue("body", body)
                        .addValue("eventId", eventId)
                        .addValue("createdAt", SqlValues.toDatabase(createdAt))
                        .addValue("readAt", SqlValues.toDatabase(readAt)));
        return id;
    }
}
