package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ExpenseCategory {

    private final String id;
    private final String name;
    private final BigDecimal deductiblePercent;

    public ExpenseCategory(String id, String name, BigDecimal deductiblePercent) {
        this.id = requireNonBlank(id, "id");
        this.name = requireNonBlank(name, "name");
        Objects.requireNonNull(deductiblePercent, "deductiblePercent must not be null");
        if (deductiblePercent.compareTo(BigDecimal.ZERO) < 0
                || deductiblePercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "deductiblePercent must be in [0, 100], got: " + deductiblePercent);
        }
        this.deductiblePercent = deductiblePercent.setScale(2, RoundingMode.HALF_UP);
    }

    public String getId()                    { return id; }
    public String getName()                  { return name; }
    public BigDecimal getDeductiblePercent() { return deductiblePercent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseCategory c)) return false;
        return id.equals(c.id)
                && name.equals(c.name)
                && deductiblePercent.equals(c.deductiblePercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, deductiblePercent);
    }

    @Override
    public String toString() {
        return "ExpenseCategory{id='" + id + "', name='" + name
                + "', deductiblePercent=" + deductiblePercent + '}';
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
