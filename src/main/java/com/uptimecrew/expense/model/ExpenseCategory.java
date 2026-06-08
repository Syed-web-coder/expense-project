package com.uptimecrew.expense.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Represents a category of expense with a deductible percentage.
 */
public final class ExpenseCategory {
    private final String id;
    private final String name;
    private final BigDecimal deductiblePercent;

    /**
     * @param id                unique category ID
     * @param name              category name, must be non-blank
     * @param deductiblePercent percentage deductible, must be between 0 and 100
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if name is blank or percent out of range
     */
    public ExpenseCategory(String id, String name, BigDecimal deductiblePercent) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.deductiblePercent = Objects.requireNonNull(deductiblePercent, "deductiblePercent must not be null")
                .setScale(2, RoundingMode.HALF_UP);
        if (id.isBlank())
            throw new IllegalArgumentException("id must not be blank");
        if (name.isBlank())
            throw new IllegalArgumentException("name must not be blank");
        if (deductiblePercent.compareTo(BigDecimal.ZERO) < 0 ||
            deductiblePercent.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("deductiblePercent must be between 0 and 100");
    }

    /** @return the category ID */
    public String getId() { return id; }
    /** @return the category name */
    public String getName() { return name; }
    /** @return the deductible percentage */
    public BigDecimal getDeductiblePercent() { return deductiblePercent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseCategory other)) return false;
        return id.equals(other.id) && name.equals(other.name)
            && deductiblePercent.compareTo(other.deductiblePercent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, deductiblePercent.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "ExpenseCategory{id=" + id + ", name=" + name +
            ", deductiblePercent=" + deductiblePercent + "}";
    }
}
