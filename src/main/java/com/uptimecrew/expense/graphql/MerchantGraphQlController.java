package com.uptimecrew.expense.graphql;

import com.uptimecrew.expense.llm.LlmSummaryService;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.readmodel.MerchantReadModelRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class MerchantGraphQlController {

    private static final Logger LOG = LoggerFactory.getLogger(MerchantGraphQlController.class);

    private final MerchantReadModelRepository readModelRepository;
    private final LlmSummaryService llmSummaryService;

    public MerchantGraphQlController(MerchantReadModelRepository readModelRepository,
                                     LlmSummaryService llmSummaryService) {
        this.readModelRepository = readModelRepository;
        this.llmSummaryService = llmSummaryService;
    }

    @QueryMapping
    public MerchantReadModel merchant(@Argument String id) {
        LOG.info("graphql query merchant id={}", id);
        return readModelRepository.findById(id).orElse(null);
    }

    @QueryMapping
    public List<MerchantReadModel> latestMerchants(@Argument Integer limit) {
        int effective = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        LOG.info("graphql query latestMerchants limit={}", effective);
        return readModelRepository.findAll(Sort.by(Sort.Direction.DESC, "capturedAt"))
                .stream().limit(effective).toList();
    }

    @MutationMapping
    public MerchantSummary summarizeMerchant(@Argument String id) {
        LOG.info("graphql mutation summarizeMerchant id={}", id);
        return llmSummaryService.summarize(id);
    }

    @BatchMapping(typeName = "Merchant", field = "lines")
    public Map<MerchantReadModel, List<MerchantReadModel.EmbeddedLine>> lines(List<MerchantReadModel> parents) {
        Map<MerchantReadModel, List<MerchantReadModel.EmbeddedLine>> byParent =
                parents.stream().collect(Collectors.toMap(
                        p -> p,
                        p -> p.getTransactions() == null ? List.of() : p.getTransactions()));
        int totalLines = byParent.values().stream().mapToInt(List::size).sum();
        LOG.info("batch-loaded {} lines for {} parents", totalLines, parents.size());
        return byParent;
    }
}
