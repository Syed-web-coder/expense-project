package com.uptimecrew.expense.model;

import java.time.Instant;
import java.util.Objects;

public final class Receipt {

    private final String id;
    private final String transactionId;
    private final String imageRef;
    private final Instant capturedAt;

    public Receipt(String id, String transactionId, String imageRef, Instant capturedAt) {
        this.id = requireNonBlank(id, "id");
        this.transactionId = requireNonBlank(transactionId, "transactionId");
        this.imageRef = requireNonBlank(imageRef, "imageRef");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    public String getId()            { return id; }
    public String getTransactionId() { return transactionId; }
    public String getImageRef()      { return imageRef; }
    public Instant getCapturedAt()   { return capturedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Receipt r)) return false;
        return id.equals(r.id)
                && transactionId.equals(r.transactionId)
                && imageRef.equals(r.imageRef)
                && capturedAt.equals(r.capturedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transactionId, imageRef, capturedAt);
    }

    @Override
    public String toString() {
        return "Receipt{id='" + id + "', transactionId='" + transactionId
                + "', imageRef='" + imageRef + "', capturedAt=" + capturedAt + '}';
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
