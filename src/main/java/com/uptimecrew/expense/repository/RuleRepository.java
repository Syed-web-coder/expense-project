package com.uptimecrew.expense.repository;

import com.uptimecrew.expense.entity.RuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<RuleEntity, String> {

    // Derived: find all rules belonging to a given expense category
    List<RuleEntity> findByCategory(String category);

    // JPQL: returns category names that have more than :minRules rules defined;
    // an aggregate projection across a string column cannot be expressed via naming
    @Query("SELECT r.category FROM RuleEntity r " +
           "GROUP BY r.category HAVING COUNT(r) > :minRules")
    List<String> findCategoriesWithMoreThanRules(@Param("minRules") long minRules);

    @Query("SELECT COUNT(DISTINCT r.category) FROM RuleEntity r")
    long countDistinctCategories();
}
