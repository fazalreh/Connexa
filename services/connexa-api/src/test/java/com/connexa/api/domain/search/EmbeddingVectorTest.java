package com.connexa.api.domain.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddingVectorTest {

    @Test
    @DisplayName("an identical direction scores 1")
    void identicalVectorsAreMaximallySimilar() {
        EmbeddingVector vector = EmbeddingVector.of(new float[] {0.2f, 0.5f, -0.3f});

        assertThat(vector.similarityTo(vector)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("magnitude does not affect similarity")
    void similarityIgnoresMagnitude() {
        // The provider in use returns un-normalised vectors, so this is the property that
        // stops ranking being driven by vector length instead of meaning.
        EmbeddingVector small = EmbeddingVector.of(new float[] {1f, 2f, 3f});
        EmbeddingVector large = EmbeddingVector.of(new float[] {100f, 200f, 300f});

        assertThat(small.similarityTo(large)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("opposite directions score -1")
    void oppositeVectorsAreMaximallyDissimilar() {
        EmbeddingVector forward = EmbeddingVector.of(new float[] {1f, 0f});
        EmbeddingVector backward = EmbeddingVector.of(new float[] {-1f, 0f});

        assertThat(forward.similarityTo(backward)).isCloseTo(-1.0, within(1e-6));
    }

    @Test
    @DisplayName("perpendicular directions score 0")
    void orthogonalVectorsAreUnrelated() {
        EmbeddingVector x = EmbeddingVector.of(new float[] {1f, 0f});
        EmbeddingVector y = EmbeddingVector.of(new float[] {0f, 1f});

        assertThat(x.similarityTo(y)).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("similarity stays inside the documented range")
    void similarityIsClamped() {
        EmbeddingVector vector = EmbeddingVector.of(new float[] {0.577f, 0.577f, 0.577f});

        assertThat(vector.similarityTo(vector)).isBetween(-1.0, 1.0);
    }

    @Test
    @DisplayName("a vector survives the storage round trip")
    void roundTripsThroughBytes() {
        EmbeddingVector original = EmbeddingVector.of(new float[] {0.1f, -0.4f, 0.9f, 0.2f});

        EmbeddingVector restored = EmbeddingVector.fromBytes(original.toBytes());

        assertThat(restored.dimensions()).isEqualTo(4);
        assertThat(restored.similarityTo(original)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("stored size is four bytes per component")
    void storesAsFloat32() {
        float[] raw = new float[768];
        java.util.Arrays.fill(raw, 0.05f);

        EmbeddingVector vector = EmbeddingVector.of(raw);

        assertThat(vector.dimensions()).isEqualTo(768);
        // 768 components at float32 is 3 KB per event; float64 would double every row
        // for precision that cannot change the ordering.
        assertThat(vector.toBytes()).hasSize(768 * 4);
    }

    @Test
    @DisplayName("comparing different dimensions is refused rather than silently wrong")
    void refusesMismatchedDimensions() {
        // Different models produce different widths; comparing them would return a
        // number that looks like a score but means nothing.
        EmbeddingVector small = EmbeddingVector.of(new float[] {1f, 0f});
        EmbeddingVector large = EmbeddingVector.of(new float[] {1f, 0f, 0f});

        assertThatThrownBy(() -> small.similarityTo(large))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensions");
    }

    @Test
    @DisplayName("a zero vector is refused")
    void refusesZeroVector() {
        assertThatThrownBy(() -> EmbeddingVector.of(new float[] {0f, 0f, 0f}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an empty or truncated stored vector is refused")
    void refusesMalformedStoredBytes() {
        assertThatThrownBy(() -> EmbeddingVector.fromBytes(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmbeddingVector.fromBytes(new byte[] {1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encoding is host independent")
    void encodingIsLittleEndian() {
        // 1.0f little-endian is 00 00 80 3F; a host-order encoding would differ on a
        // big-endian machine and make stored vectors unreadable there.
        byte[] bytes = EmbeddingVector.of(new float[] {1f, 0f}).toBytes();

        assertThat(bytes).hasSize(8);
        assertThat(bytes[0]).isZero();
        assertThat(bytes[3]).isEqualTo((byte) 0x3F);
    }
}
