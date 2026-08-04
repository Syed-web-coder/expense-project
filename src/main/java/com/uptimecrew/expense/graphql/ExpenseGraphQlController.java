package com.uptimecrew.expense.graphql;

import com.uptimecrew.expense.mcp.TransactionView;
import com.uptimecrew.expense.repository.MerchantRepository;
import com.uptimecrew.expense.repository.RuleRepository;
import com.uptimecrew.expense.repository.TransactionRepository;
import com.uptimecrew.expense.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
public class ExpenseGraphQlController {

    private static final Logger LOG = LoggerFactory.getLogger(ExpenseGraphQlController.class);

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final RuleRepository ruleRepository;
    private final TransactionService transactionService;

    public ExpenseGraphQlController(MerchantRepository merchantRepository,
                                    TransactionRepository transactionRepository,
                                    RuleRepository ruleRepository,
                                    TransactionService transactionService) {
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.ruleRepository = ruleRepository;
        this.transactionService = transactionService;
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public int totalMerchants() {
        int count = (int) merchantRepository.count();
        LOG.info("graphql query totalMerchants count={}", count);
        return count;
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public int totalTransactions() {
        int count = (int) transactionRepository.count();
        LOG.info("graphql query totalTransactions count={}", count);
        return count;
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public BigDecimal totalSpend() {
        BigDecimal total = transactionRepository.sumAllAmounts();
        LOG.info("graphql query totalSpend total={}", total);
        return total;
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public int categoryCount() {
        int count = (int) ruleRepository.countDistinctCategories();
        LOG.info("graphql query categoryCount count={}", count);
        return count;
    }

    @MutationMapping
    @Transactional
    public TransactionView addExpense(@Argument String merchantId, @Argument Double amount) {
        LOG.info("graphql mutation addExpense merchantId={} amount={}", merchantId, amount);
        BigDecimal scaled = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        // kind hardcoded to DEBIT for manual expense entry; kind mapping
        // (PURCHASE/REFUND vs DEBIT/CREDIT) may need revisiting when refund flows are added
        var entity = transactionService.recordTransaction(merchantId, scaled, "DEBIT");
        return TransactionView.from(entity);
    }
}
