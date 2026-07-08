package com.uptimecrew.expense.lambda;

import com.fasterxml.jackson.annotation.JsonProperty;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MerchantRecord(
        @JsonProperty("id")                   String id,
        @JsonProperty("name")                 String name,
        @JsonProperty("averageTransactionValue") BigDecimal averageTransactionValue,
        @JsonProperty("createdAt")            Instant createdAt
) {
    public MerchantRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(averageTransactionValue, "averageTransactionValue must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        averageTransactionValue = averageTransactionValue.setScale(2, RoundingMode.HALF_UP);
    }

    public static MerchantRecord fromItem(Map<String, AttributeValue> item) {
        Objects.requireNonNull(item, "item must not be null");
        return new MerchantRecord(
                item.get("id").s(),
                item.get("name").s(),
                new BigDecimal(item.get("averageTransactionValue").n()),
                Instant.parse(item.get("createdAt").s())
        );
    }
}
