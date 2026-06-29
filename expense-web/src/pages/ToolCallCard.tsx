interface ToolCallCardProps {
  readonly toolName: string;
  readonly toolState: string;
  readonly input: unknown;
  readonly output?: unknown;
}

export function ToolCallCard({
  toolName,
  toolState,
  input,
  output,
}: ToolCallCardProps): React.ReactElement {
  return (
    <aside aria-label="tool-call" data-tool={toolName} data-state={toolState}>
      <header>
        called <code>{toolName}</code>
      </header>
      <pre>{JSON.stringify(input, null, 2)}</pre>
      {toolState === 'output-available' && (
        <pre data-testid="tool-result">{JSON.stringify(output, null, 2)}</pre>
      )}
    </aside>
  );
}
