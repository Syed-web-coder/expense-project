/* eslint-disable */
import * as types from './graphql';
import type { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core';

/**
 * Map of all GraphQL operations in the project.
 *
 * This map has several performance disadvantages:
 * 1. It is not tree-shakeable, so it will include all operations in the project.
 * 2. It is not minifiable, so the string of a GraphQL query will be multiple times inside the bundle.
 * 3. It does not support dead code elimination, so it will add unused operations.
 *
 * Therefore it is highly recommended to use the babel or swc plugin for production.
 * Learn more about it here: https://the-guild.dev/graphql/codegen/plugins/presets/preset-client#reducing-bundle-size
 */
type Documents = {
    "\n  query MerchantDetail($id: ID!) {\n    merchant(id: $id) {\n      id\n      mccCode\n      lines {\n        line\n        amount\n      }\n    }\n  }\n": typeof types.MerchantDetailDocument,
    "\n  query LatestMerchants {\n    latestMerchants(limit: 20) {\n      id\n      mccCode\n      capturedAt\n      lines {\n        line\n        amount\n      }\n    }\n  }\n": typeof types.LatestMerchantsDocument,
    "\n  query DashboardStats {\n    totalMerchants\n    totalTransactions\n    totalSpend\n    categoryCount\n  }\n": typeof types.DashboardStatsDocument,
    "\n  mutation AddExpense($merchantId: ID!, $amount: Float!) {\n    addExpense(merchantId: $merchantId, amount: $amount) {\n      id\n      merchantId\n      merchantName\n      amount\n      occurredAt\n      kind\n    }\n  }\n": typeof types.AddExpenseDocument,
    "\n  mutation SummarizeMerchant($id: ID!) {\n    summarizeMerchant(id: $id) {\n      mccCode\n      totalSpend\n      transactionCount\n      primaryCategory\n      confidence\n      tokensIn\n      tokensOut\n    }\n  }\n": typeof types.SummarizeMerchantDocument,
};
const documents: Documents = {
    "\n  query MerchantDetail($id: ID!) {\n    merchant(id: $id) {\n      id\n      mccCode\n      lines {\n        line\n        amount\n      }\n    }\n  }\n": types.MerchantDetailDocument,
    "\n  query LatestMerchants {\n    latestMerchants(limit: 20) {\n      id\n      mccCode\n      capturedAt\n      lines {\n        line\n        amount\n      }\n    }\n  }\n": types.LatestMerchantsDocument,
    "\n  query DashboardStats {\n    totalMerchants\n    totalTransactions\n    totalSpend\n    categoryCount\n  }\n": types.DashboardStatsDocument,
    "\n  mutation AddExpense($merchantId: ID!, $amount: Float!) {\n    addExpense(merchantId: $merchantId, amount: $amount) {\n      id\n      merchantId\n      merchantName\n      amount\n      occurredAt\n      kind\n    }\n  }\n": types.AddExpenseDocument,
    "\n  mutation SummarizeMerchant($id: ID!) {\n    summarizeMerchant(id: $id) {\n      mccCode\n      totalSpend\n      transactionCount\n      primaryCategory\n      confidence\n      tokensIn\n      tokensOut\n    }\n  }\n": types.SummarizeMerchantDocument,
};

/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 *
 *
 * @example
 * ```ts
 * const query = graphql(`query GetUser($id: ID!) { user(id: $id) { name } }`);
 * ```
 *
 * The query argument is unknown!
 * Please regenerate the types.
 */
export function graphql(source: string): unknown;

/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query MerchantDetail($id: ID!) {\n    merchant(id: $id) {\n      id\n      mccCode\n      lines {\n        line\n        amount\n      }\n    }\n  }\n"): (typeof documents)["\n  query MerchantDetail($id: ID!) {\n    merchant(id: $id) {\n      id\n      mccCode\n      lines {\n        line\n        amount\n      }\n    }\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query LatestMerchants {\n    latestMerchants(limit: 20) {\n      id\n      mccCode\n      capturedAt\n      lines {\n        line\n        amount\n      }\n    }\n  }\n"): (typeof documents)["\n  query LatestMerchants {\n    latestMerchants(limit: 20) {\n      id\n      mccCode\n      capturedAt\n      lines {\n        line\n        amount\n      }\n    }\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query DashboardStats {\n    totalMerchants\n    totalTransactions\n    totalSpend\n    categoryCount\n  }\n"): (typeof documents)["\n  query DashboardStats {\n    totalMerchants\n    totalTransactions\n    totalSpend\n    categoryCount\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation AddExpense($merchantId: ID!, $amount: Float!) {\n    addExpense(merchantId: $merchantId, amount: $amount) {\n      id\n      merchantId\n      merchantName\n      amount\n      occurredAt\n      kind\n    }\n  }\n"): (typeof documents)["\n  mutation AddExpense($merchantId: ID!, $amount: Float!) {\n    addExpense(merchantId: $merchantId, amount: $amount) {\n      id\n      merchantId\n      merchantName\n      amount\n      occurredAt\n      kind\n    }\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation SummarizeMerchant($id: ID!) {\n    summarizeMerchant(id: $id) {\n      mccCode\n      totalSpend\n      transactionCount\n      primaryCategory\n      confidence\n      tokensIn\n      tokensOut\n    }\n  }\n"): (typeof documents)["\n  mutation SummarizeMerchant($id: ID!) {\n    summarizeMerchant(id: $id) {\n      mccCode\n      totalSpend\n      transactionCount\n      primaryCategory\n      confidence\n      tokensIn\n      tokensOut\n    }\n  }\n"];

export function graphql(source: string) {
  return (documents as any)[source] ?? {};
}

export type DocumentType<TDocumentNode extends DocumentNode<any, any>> = TDocumentNode extends DocumentNode<  infer TType,  any>  ? TType  : never;