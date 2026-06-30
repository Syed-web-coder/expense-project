declare module 'jest-axe' {
  interface AxeResults {
    violations: {
      id: string;
      impact?: string;
      description: string;
      help: string;
      helpUrl?: string;
      nodes: { html: string; target: string[]; failureSummary?: string }[];
    }[];
    toolOptions?: { impactLevels?: string[] };
  }

  type AxeFn = (
    html: Element | string,
    options?: Record<string, unknown>,
  ) => Promise<AxeResults>;

  export const axe: AxeFn;
  export function configureAxe(options?: Record<string, unknown>): AxeFn;
  export const toHaveNoViolations: Record<
    string,
    (received: unknown) => { pass: boolean; message(): string }
  >;
}
