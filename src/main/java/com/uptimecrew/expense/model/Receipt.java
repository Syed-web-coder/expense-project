package com.uptimecrew.expense.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a receipt linked to a transaction.
 */
public final class Receipt {
    private final String id;
    private final String transactionId;
    private final String imageRef;
    private final LocalDate capturedAt;

    /**
     * @param id            unique receipt ID
     * @param transactionId ID of the linked transaction
     * @param imageRef      reference to the receipt image
     * @param capturedAt    date the receipt was captured
     * @throws NullPointerException if any argument is null
     */
    public Receipt(String id, String transactionId, String imageRef, LocalDate capturedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        this.imageRef = Objects.requireNonNull(imageRef, "imageRef must not be null");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    /** @return the receipt ID */
    public String getId() { return id; }
    /** @return the transaction ID */
    public String getTransactionId() { return transactionId; }
    /** @return the image reference */
    public String getImageRef() { return imageRef; }
    /** @return the date the receipt was captured */
    public LocalDate getCapturedAt() { return capturedAt; }

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
