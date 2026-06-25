package com.uptimecrew.expense.api;

import com.uptimecrew.expense.clients.IdentityProfile;
import com.uptimecrew.expense.clients.IdentityService;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.service.ExpenseClassificationService;
import com.uptimecrew.expense.service.TransactionService;
import com.uptimecrew.expense.mcp.TransactionView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Pattern reference for Task 1 + 2 + 3.
//
// The controller moves under /api/v1/merchants (URI versioning). The
// summary route is now POST, requires an Idempotency-Key header, and enriches
// the response with the caller display name resolved via the Feign-backed
// IdentityService.
@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchants", description = "Merchants read API and LLM-summary POST endpoint")
public class MerchantController {

    private static final Logger LOG = LoggerFactory.getLogger(MerchantController.class);

    private final ExpenseClassificationService service;
    private final IdentityService identityService;
    private final IdempotencyService idempotency;
    private final TransactionService transactionService;

    public MerchantController(ExpenseClassificationService service,
                               IdentityService identityService,
                               IdempotencyService idempotency,
                               TransactionService transactionService) {
        this.service = service;
        this.identityService = identityService;
        this.idempotency = idempotency;
        this.transactionService = transactionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_transactions:write')")
    @Operation(summary = "Record a transaction for a merchant",
            description = "Writes the transaction and its outbox event in one DB transaction; the outbox poller ships it to Kafka.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transaction recorded"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "JWT present but lacks required scope"),
        @ApiResponse(responseCode = "404", description = "No merchant with that id")
    })
    public ResponseEntity<TransactionView> create(@RequestBody CreateTransactionRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        LOG.info("create transaction merchantId={} subject={}", request.merchantId(), jwt.getSubject());
        TransactionView view = transactionService.recordTransactionView(
                request.merchantId(), request.amount(), request.kind(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_merchants.read') and hasRole('MERCHANT_READER')")
    @Operation(summary = "Fetch a merchant by id",
            description = "Returns the denormalised read-model document for the given id.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "JWT present but lacks required scope or role"),
        @ApiResponse(responseCode = "404", description = "No merchant with that id")
    })
    public ResponseEntity<MerchantReadModel> getById(@PathVariable String id,
                                                @AuthenticationPrincipal Jwt jwt) {
        LOG.info("get id={} subject={}", id, jwt.getSubject());
        Optional<MerchantReadModel> found = service.findById(id);
        return found.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/summary")
    @PreAuthorize("hasAuthority('SCOPE_merchants.read') and hasRole('MERCHANT_READER')")
    @Operation(summary = "Generate an LLM summary for a merchant",
            description = "Idempotent POST: pass an Idempotency-Key header (UUID) so retries return the cached body.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary generated (or cached body returned)"),
        @ApiResponse(responseCode = "400", description = "Idempotency-Key missing or not a valid UUID"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "JWT present but lacks required scope or role"),
        @ApiResponse(responseCode = "409", description = "Idempotency key in flight for a different request")
    })
    public ResponseEntity<Map<String, String>> summary(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {

        final UUID parsed;
        try {
            parsed = UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException ex) {
            LOG.warn("rejected non-UUID Idempotency-Key for id={} subject={}", id, jwt.getSubject());
            return ResponseEntity.badRequest().build();
        }

        return idempotency.handle(parsed.toString(), "merchants.summary", () -> {
            try {
                Thread.sleep(100); // (1) stub LLM latency
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            IdentityProfile profile = identityService.getProfile(jwt.getSubject());
            LOG.info("summary id={} subject={} displayName={}",
                    id, jwt.getSubject(), profile.displayName());
            return ResponseEntity.ok(Map.of(
                    "summary", "Stub LLM summary for " + id,
                    "displayName", profile.displayName()
            ));
        });
    }
}
