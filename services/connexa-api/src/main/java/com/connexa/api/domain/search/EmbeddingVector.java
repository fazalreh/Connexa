package com.connexa.api.domain.search;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * A unit-length embedding.
 *
 * <p>Vectors are normalised on construction, which makes cosine similarity a plain dot
 * product and removes a whole class of silent ranking bugs: providers differ on whether they
 * return unit vectors, so comparing an un-normalised vector against a normalised one ranks
 * by magnitude as much as by meaning. Normalising here means the model can be swapped
 * without changing how results are ordered.
 *
 * <p>Stored as float32 rather than double: the extra precision has no effect on ranking and
 * would double the size of every row.
 */
public final class EmbeddingVector {

    private static final int BYTES_PER_COMPONENT = Float.BYTES;

    private final float[] components;

    private EmbeddingVector(float[] components) {
        this.components = components;
    }

    public static EmbeddingVector of(float[] raw) {
        Objects.requireNonNull(raw, "raw is required");
        if (raw.length == 0) {
            throw new IllegalArgumentException("an embedding must have at least one component");
        }
        double magnitude = 0.0;
        for (float component : raw) {
            magnitude += (double) component * component;
        }
        magnitude = Math.sqrt(magnitude);
        if (magnitude == 0.0 || Double.isNaN(magnitude)) {
            // A zero vector has no direction, so it cannot be compared meaningfully.
            throw new IllegalArgumentException("an embedding must have non-zero magnitude");
        }
        float[] normalised = new float[raw.length];
        for (int index = 0; index < raw.length; index++) {
            normalised[index] = (float) (raw[index] / magnitude);
        }
        return new EmbeddingVector(normalised);
    }

    public static EmbeddingVector of(double[] raw) {
        Objects.requireNonNull(raw, "raw is required");
        float[] narrowed = new float[raw.length];
        for (int index = 0; index < raw.length; index++) {
            narrowed[index] = (float) raw[index];
        }
        return of(narrowed);
    }

    public int dimensions() {
        return components.length;
    }

    /**
     * A copy of the components, for callers that must combine vectors rather than compare
     * them. Copied so the normalised state of this vector cannot be edited from outside.
     */
    public float[] components() {
        return components.clone();
    }

    /**
     * Cosine similarity in [-1, 1]. Both operands are unit length, so this is their dot
     * product.
     *
     * @throws IllegalArgumentException when dimensions differ, which means the two were
     *     produced by different models and are not comparable at all
     */
    public double similarityTo(EmbeddingVector other) {
        Objects.requireNonNull(other, "other is required");
        if (other.components.length != components.length) {
            throw new IllegalArgumentException(
                    "cannot compare embeddings of %d and %d dimensions"
                            .formatted(components.length, other.components.length));
        }
        double total = 0.0;
        for (int index = 0; index < components.length; index++) {
            total += (double) components[index] * other.components[index];
        }
        // Rounding can push a self-comparison a hair past 1.0; clamp so callers can rely
        // on the documented range.
        return Math.max(-1.0, Math.min(1.0, total));
    }

    /** Little-endian float32, so the encoding does not depend on the host architecture. */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(components.length * BYTES_PER_COMPONENT)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float component : components) {
            buffer.putFloat(component);
        }
        return buffer.array();
    }

    public static EmbeddingVector fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes is required");
        if (bytes.length == 0 || bytes.length % BYTES_PER_COMPONENT != 0) {
            throw new IllegalArgumentException("stored embedding is not a float32 array");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] components = new float[bytes.length / BYTES_PER_COMPONENT];
        for (int index = 0; index < components.length; index++) {
            components[index] = buffer.getFloat();
        }
        return of(components);
    }
}
