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
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Holds a ledger of transactions keyed by transaction ID.
 */
public final class TransactionLedger {

    private static final Logger LOG =
        Logger.getLogger(TransactionLedger.class.getName());

    private final Map<String, Transaction> transactions;

    /**
     * @param transactions collection of transactions to store
     * @throws NullPointerException if transactions is null
     */
    public TransactionLedger(Collection<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        Map<String, Transaction> copy = new HashMap<>();
        for (Transaction t : transactions) {
            copy.put(t.id(), t);
        }
        this.transactions = Collections.unmodifiableMap(copy);
        LOG.info("TransactionLedger created with " + this.transactions.size() + " transactions");
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
        Optional<Transaction> result = Optional.ofNullable(transactions.get(id));
        if (result.isEmpty()) {
            LOG.warning("Transaction not found for id: " + id);
        } else {
            LOG.info("Transaction found for id: " + id);
        }
        return result;
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
        List<Transaction> result = transactions.values().stream()
            .filter(t -> t.merchantName().toLowerCase()
                .contains(merchantFragment.toLowerCase())
                && t.amount().compareTo(threshold) > 0)
            .sorted(Comparator.comparing(Transaction::amount).reversed()
                .thenComparing(Transaction::merchantName))
            .collect(Collectors.toUnmodifiableList());
        if (result.isEmpty()) {
            LOG.warning("No transactions found for merchant fragment: " + merchantFragment
                + " above threshold: " + threshold);
        } else {
            LOG.info("Found " + result.size() + " transactions for merchant fragment: "
                + merchantFragment);
        }
        return result;
    }
}
