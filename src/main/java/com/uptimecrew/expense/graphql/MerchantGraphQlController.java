package com.uptimecrew.expense.graphql;

import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.entity.TransactionEntity;
import com.uptimecrew.expense.llm.LlmSummaryService;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.repository.MerchantRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class MerchantGraphQlController {

    private static final Logger LOG = LoggerFactory.getLogger(MerchantGraphQlController.class);

    private final MerchantRepository merchantRepository;
    private final LlmSummaryService llmSummaryService;

    public MerchantGraphQlController(MerchantRepository merchantRepository,
                                     LlmSummaryService llmSummaryService) {
        this.merchantRepository = merchantRepository;
        this.llmSummaryService = llmSummaryService;
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public MerchantReadModel merchant(@Argument String id) {
        LOG.info("graphql query merchant id={}", id);
        return merchantRepository.findById(id).map(this::toReadModel).orElse(null);
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public List<MerchantReadModel> latestMerchants(@Argument Integer limit) {
        int effective = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        LOG.info("graphql query latestMerchants limit={}", effective);
        return merchantRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().limit(effective).map(this::toReadModel).toList();
    }

    @MutationMapping
    public MerchantSummary summarizeMerchant(@Argument String id) {
        LOG.info("graphql mutation summarizeMerchant id={}", id);
        return llmSummaryService.summarize(id);
    }

    @BatchMapping(typeName = "Merchant", field = "lines")
    public Map<MerchantReadModel, List<MerchantReadModel.EmbeddedLine>> lines(List<MerchantReadModel> parents) {
        Map<MerchantReadModel, List<MerchantReadModel.EmbeddedLine>> byParent = parents.stream()
                .collect(Collectors.toMap(p -> p, p -> p.getTransactions() == null ? List.of() : p.getTransactions()));
        LOG.info("batch-loaded {} lines for {} parents",
                byParent.values().stream().mapToInt(List::size).sum(), parents.size());
        return byParent;
    }

    private MerchantReadModel toReadModel(MerchantEntity entity) {
        List<TransactionEntity> txns = entity.getTransactions();
        List<MerchantReadModel.EmbeddedLine> lines = IntStream.range(0, txns.size())
                .mapToObj(i -> new MerchantReadModel.EmbeddedLine(i + 1, txns.get(i).getAmount()))
                .toList();
        return new MerchantReadModel(entity.getId(), entity.getName(), entity.getMccCode(), entity.getCreatedAt(), lines);
    }
}
