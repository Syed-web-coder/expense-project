// src/test/ToolCallCard.test.tsx
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ToolCallCard } from '../pages/ToolCallCard';

describe('ToolCallCard', () => {
  it('renders the tool name while input is still streaming', () => {
    render(
      <ToolCallCard
        toolName="lookupMerchant"
        toolState="input-streaming"
        input={{ id: 'stub-1' }}
      />,
    );
    const card = screen.getByLabelText('tool-call');
    expect(card).toHaveAttribute('data-tool', 'lookupMerchant');
    expect(card).toHaveAttribute('data-state', 'input-streaming');
    expect(screen.queryByTestId('tool-result')).not.toBeInTheDocument();
  });

  it('renders the full input once input-available', () => {
    render(
      <ToolCallCard
        toolName="lookupMerchant"
        toolState="input-available"
        input={{ id: 'stub-1' }}
      />,
    );
    expect(screen.getByText(/"id": "stub-1"/)).toBeInTheDocument();
    expect(screen.queryByTestId('tool-result')).not.toBeInTheDocument();
  });

  it('renders the result payload once output-available', () => {
    render(
      <ToolCallCard
        toolName="lookupMerchant"
        toolState="output-available"
        input={{ id: 'stub-1' }}
        output={{ id: 'stub-1', mccCode: '5411' }}
      />,
    );
    const result = screen.getByTestId('tool-result');
    expect(result).toHaveTextContent('5411');
  });
});
