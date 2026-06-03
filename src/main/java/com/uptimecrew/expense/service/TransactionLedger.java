package com.uptimecrew.expense.service;

import com.uptimecrew.expense.model.Transaction;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Holds a ledger of transactions keyed by transaction ID.
 */
public final class TransactionLedger {

    private final Map<String, Transaction> transactions;

    /**
     * @param transactions collection of transactions to store
     * @throws NullPointerException if transactions is null
     */
    public TransactionLedger(Collection<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Map<String, Transaction> copy = new HashMap<>();
        for (Transaction t : transactions) {
            copy.put(t.getId(), t);
        }
        this.transactions = Collections.unmodifiableMap(copy);
    }

    /**
     * @return the number of transactions in this ledger
     */
    public int size() {
        return transactions.size();
    }

    /**
     * @param id the transaction ID to look up
     * @return an Optional containing the transaction if found, empty otherwise
     * @throws NullPointerException if id is null
     */
    public Optional<Transaction> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(transactions.get(id));
    }

    /**
     * Finds transactions whose merchant name contains the given fragment
     * and whose amount exceeds the given threshold.
     *
     * @param merchantFragment the substring to search for in merchant names
     * @param threshold        the minimum amount (exclusive)
     * @return an unmodifiable list of matching transactions
     * @throws NullPointerException if either argument is null
     */
    public List<Transaction> findByMerchantAbove(String merchantFragment, BigDecimal threshold) {
        Objects.requireNonNull(merchantFragment, "merchantFragment must not be null");
        Objects.requireNonNull(threshold, "threshold must not be null");
        return transactions.values().stream()
            .filter(t -> t.getMerchantName().toLowerCase()
                .contains(merchantFragment.toLowerCase())
                && t.getAmount().compareTo(threshold) > 0)
            .sorted(Comparator.comparing(Transaction::getAmount).reversed()
                .thenComparing(Transaction::getMerchantName))
            .collect(Collectors.toUnmodifiableList());
    }
}
