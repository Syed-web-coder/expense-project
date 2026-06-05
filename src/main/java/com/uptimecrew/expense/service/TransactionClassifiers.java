package com.uptimecrew.expense.service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Factory class for creating TransactionClassifier instances.
 * This class is not meant to be instantiated.
 */
public final class TransactionClassifiers {

    /**
     * Private constructor — this is a utility class.
     * @throws AssertionError always
     */
    private TransactionClassifiers() {
        throw new AssertionError("TransactionClassifiers is not instantiable");
    }

    /**
     * @return a TransactionClassifier that classifies by merchant name
     */
    public static TransactionClassifier byMerchantName() {
        return new MerchantNameClassifier();
    }

    /**
     * @param threshold the amount threshold, must not be null or negative
     * @return a TransactionClassifier that classifies by amount threshold
     * @throws NullPointerException if threshold is null
     * @throws IllegalArgumentException if threshold is negative
     */
    public static TransactionClassifier byAmountThreshold(BigDecimal threshold) {
        return new AmountThresholdClassifier(threshold);
    }

    /**
     * @param refundCodes set of merchant codes that indicate a refund
     * @return a TransactionClassifier that classifies by MCC code
     * @throws NullPointerException if refundCodes is null
     */
    public static TransactionClassifier byMccCode(Set<String> refundCodes) {
        return new MccCodeClassifier(refundCodes);
    }
}
