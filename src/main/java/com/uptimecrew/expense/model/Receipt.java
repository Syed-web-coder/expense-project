package com.uptimecrew.expense.model;

import java.time.Instant;
import java.util.Objects;

public final class Receipt {
    private final String id;
    private final String transactionId;
    private final String imageRef;
    private final Instant capturedAt;

    public Receipt(String id, String transactionId, String imageRef, Instant capturedAt) {
        this.id = requireNonBlank(Objects.requireNonNull(id, "id must not be null"), "id");
        this.transactionId = requireNonBlank(Objects.requireNonNull(transactionId, "transactionId must not be null"), "transactionId");
        this.imageRef = requireNonBlank(Objects.requireNonNull(imageRef, "imageRef must not be null"), "imageRef");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    private static String requireNonBlank(String value, String name) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getImageRef() { return imageRef; }
    public Instant getCapturedAt() { return capturedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Receipt other)) return false;
        return id.equals(other.id) && transactionId.equals(other.transactionId)
            && imageRef.equals(other.imageRef) && capturedAt.equals(other.capturedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transactionId, imageRef, capturedAt);
    }

    @Override
    public String toString() {
        return "Receipt{id=" + id + ", transactionId=" + transactionId +
            ", imageRef=" + imageRef + ", capturedAt=" + capturedAt + "}";
    }
}
