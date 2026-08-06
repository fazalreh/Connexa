package com.connexa.api.infrastructure.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcOrganizerEventDraftStoreTest {

    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");
    private static final IdentityKey BOB = new IdentityKey("https://identity.connexa", "bob");
    private static final Instant CREATED = Instant.parse("2026-06-01T09:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");

    private JdbcOrganizerEventDraftStore store;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate jdbc = JdbcAdapterTestSupport.freshDatabase();
        store = new JdbcOrganizerEventDraftStore(jdbc);
    }

    @Test
    @DisplayName("a saved draft round-trips every field")
    void savesAndReadsBackAllFields() {
        OrganizerEventDraft draft = draft(ALICE, "Robotics Workshop", CREATED);

        store.save(draft);
        OrganizerEventDraft stored = store.findByOwner(ALICE).get(0);

        assertThat(stored.id()).isEqualTo(draft.id());
        assertThat(stored.owner()).isEqualTo(ALICE);
        assertThat(stored.title()).isEqualTo("Robotics Workshop");
        assertThat(stored.description()).isEqualTo(draft.description());
        assertThat(stored.location()).isEqualTo("Main Auditorium");
        assertThat(stored.startsAt()).isEqualTo(STARTS);
        assertThat(stored.endsAt()).isEqualTo(STARTS.plusSeconds(7_200));
        assertThat(stored.timeZone()).isEqualTo("Asia/Karachi");
        assertThat(stored.category()).isEqualTo("Technology");
        assertThat(stored.capacity()).isEqualTo(120);
        assertThat(stored.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(stored.createdAt()).isEqualTo(CREATED);
        assertThat(stored.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    @DisplayName("re-saving the same id replaces the draft instead of duplicating it")
    void replacesDraftOnResave() {
        OrganizerEventDraft original = draft(ALICE, "Draft Title", CREATED);
        store.save(original);

        Instant edited = CREATED.plusSeconds(3_600);
        OrganizerEventDraft updated = new OrganizerEventDraft(
                original.id(),
                ALICE,
                "Corrected Title",
                original.description(),
                original.location(),
                original.startsAt(),
                original.endsAt(),
                original.timeZone(),
                original.category(),
                200,
                EventStatus.DRAFT,
                CREATED,
                edited);
        store.save(updated);

        List<OrganizerEventDraft> drafts = store.findByOwner(ALICE);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).title()).isEqualTo("Corrected Title");
        assertThat(drafts.get(0).capacity()).isEqualTo(200);
        assertThat(drafts.get(0).updatedAt()).isEqualTo(edited);
    }

    @Test
    @DisplayName("drafts are listed most recently updated first")
    void listsMostRecentlyUpdatedFirst() {
        store.save(draft(ALICE, "Older Draft", CREATED));
        store.save(draft(ALICE, "Newer Draft", CREATED.plusSeconds(7_200)));

        assertThat(store.findByOwner(ALICE)).extracting(OrganizerEventDraft::title)
                .containsExactly("Newer Draft", "Older Draft");
    }

    @Test
    @DisplayName("an organizer never sees another organizer's drafts")
    void isolatesDraftsByOwner() {
        store.save(draft(ALICE, "Alice Draft", CREATED));
        store.save(draft(BOB, "Bob Draft", CREATED));

        assertThat(store.findByOwner(ALICE)).extracting(OrganizerEventDraft::title)
                .containsExactly("Alice Draft");
        assertThat(store.findByOwner(BOB)).extracting(OrganizerEventDraft::title)
                .containsExactly("Bob Draft");
    }

    @Test
    @DisplayName("an organizer with no drafts reads as empty")
    void readsEmptyForOwnerWithoutDrafts() {
        assertThat(store.findByOwner(ALICE)).isEmpty();
    }

    private static OrganizerEventDraft draft(IdentityKey owner, String title, Instant updatedAt) {
        return new OrganizerEventDraft(
                UUID.randomUUID(),
                owner,
                title,
                "A hands-on session covering the full build and test workflow.",
                "Main Auditorium",
                STARTS,
                STARTS.plusSeconds(7_200),
                "Asia/Karachi",
                "Technology",
                120,
                EventStatus.DRAFT,
                CREATED,
                updatedAt);
    }
}
