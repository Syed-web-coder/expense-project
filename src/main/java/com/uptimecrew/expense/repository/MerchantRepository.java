package com.uptimecrew.expense.repository;

import com.uptimecrew.expense.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantRepository extends JpaRepository<MerchantEntity, String> {

    // Derived: find all merchants registered under a given MCC code
    List<MerchantEntity> findByMccCode(String mccCode);

    // Derived: look up an existing merchant by name, so classify() can
    // reuse a merchant instead of creating a duplicate per transaction
    Optional<MerchantEntity> findByName(String name);

    // JPQL: naming convention cannot express GROUP BY / HAVING; returns merchants
    // that have accumulated at least :minCount linked transactions
    @Query("SELECT m FROM MerchantEntity m JOIN m.transactions t " +
           "GROUP BY m HAVING COUNT(t) >= :minCount")
    List<MerchantEntity> findMerchantsWithAtLeastTransactions(@Param("minCount") long minCount);
}
