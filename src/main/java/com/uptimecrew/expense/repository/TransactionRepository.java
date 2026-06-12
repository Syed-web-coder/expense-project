package com.uptimecrew.expense.repository;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    // Derived: find all transactions of a specific kind (e.g. DEBIT, CREDIT)
    List<TransactionEntity> findByKind(String kind);

    // JPQL: SUM + HAVING across a related entity cannot be expressed via naming;
    // returns the MerchantEntity for every merchant whose total charged amount
    // exceeds :threshold
    @Query("SELECT t.merchant FROM TransactionEntity t " +
           "GROUP BY t.merchant HAVING SUM(t.amount) > :threshold")
    List<MerchantEntity> findMerchantsWithTotalSpendAbove(@Param("threshold") BigDecimal threshold);
}
