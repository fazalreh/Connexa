package com.connexa.api.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * Conversions shared by the JDBC persistence adapters.
 *
 * <p>Timestamps cross the boundary as {@link OffsetDateTime} in UTC rather than
 * {@code java.sql.Timestamp}, so a {@code timestamp with time zone} column round-trips
 * without depending on the JVM default zone.
 */
public final class SqlValues {

    /**
     * Escape character declared alongside every generated LIKE predicate.
     *
     * <p>A backslash would be ambiguous: whether {@code '\'} is a one-character literal or an
     * incomplete escape depends on engine settings such as PostgreSQL's
     * {@code standard_conforming_strings}. {@code !} carries no such meaning in a string
     * literal on any engine, and {@link #containsPattern} escapes it when a search term
     * contains one.
     */
    public static final char LIKE_ESCAPE = '!';

    private SqlValues() {
    }

    public static OffsetDateTime toDatabase(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant readInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static UUID readUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    public static <E extends Enum<E>> E readEnum(ResultSet rs, String column, Class<E> type)
            throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    public static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * Builds a case-insensitive {@code LIKE} argument in which caller-supplied
     * wildcards are treated as literal characters.
     */
    public static String containsPattern(String term) {
        StringBuilder escaped = new StringBuilder(term.length() + 2);
        escaped.append('%');
        for (int index = 0; index < term.length(); index++) {
            char character = term.charAt(index);
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.append('%').toString().toLowerCase(Locale.ROOT);
    }
}
