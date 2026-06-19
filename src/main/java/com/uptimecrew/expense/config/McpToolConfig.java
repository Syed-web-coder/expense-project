package com.uptimecrew.expense.config;

import com.uptimecrew.expense.mcp.TransactionTools;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers @Tool-annotated beans with Spring AI's MCP server.
 *
 * @Tool methods are NOT auto-discovered just because the class carries
 * @Component (unlike the newer @McpTool annotation, which in principle
 * scans automatically). MethodToolCallbackProvider is the explicit bridge
 * that tells the MCP server runtime which bean(s) to scan for @Tool
 * methods. Skipping this bean means /mcp boots fine, tools/list might even
 * be empty or stale, and any tools/call for a real tool name comes back
 * "Unknown tool" (JSON-RPC code -32602) even though @PreAuthorize and the
 * service logic are all correct underneath.
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider transactionToolCallbackProvider(TransactionTools transactionTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(transactionTools)
                .build();
    }
}
