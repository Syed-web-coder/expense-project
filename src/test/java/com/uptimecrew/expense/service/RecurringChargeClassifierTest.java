package com.uptimecrew.expense.service;

import com.uptimecrew.expense.exception.UnrecognizedMerchantException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurringChargeClassifierTest {

    private RecurringChargeClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RecurringChargeClassifier();
    }

    @Test
    @DisplayName("Known subscription merchant is recognised as recurring")
    void classify_knownSubscriptionMerchant_returnsTrue() {
        // Arrange
        String merchant = "Netflix";

        // Act
        boolean result = classifier.classify(merchant);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Blank merchant name throws UnrecognizedMerchantException")
    void classify_blankMerchantName_throwsUnrecognizedMerchantException() {
        // Arrange
        String blank = "   ";

        // Act + Assert
        assertThatThrownBy(() -> classifier.classify(blank))
                .isInstanceOf(UnrecognizedMerchantException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("Edge case: single-character merchant name is not recurring")
    void classify_singleCharMerchantName_returnsFalse() {
        // Arrange
        String merchant = "X";

        // Act
        boolean result = classifier.classify(merchant);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Exception path: null merchant name throws UnrecognizedMerchantException")
    void classify_nullMerchantName_throwsUnrecognizedMerchantException() {
        // Arrange
        String merchant = null;

        // Act + Assert
        assertThatThrownBy(() -> classifier.classify(merchant))
                .isInstanceOf(UnrecognizedMerchantException.class)
                .hasMessageContaining("null");
    }
}
