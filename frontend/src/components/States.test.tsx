import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EmptyState, ErrorState, LoadingState } from './States';

describe('request states', () => {
  it('exposes loading, empty and API failure states accessibly', () => {
    const { rerender } = render(<LoadingState />);
    expect(screen.getByRole('status')).toHaveTextContent('Загружаем данные');
    rerender(<EmptyState title="Ничего не найдено" />);
    expect(screen.getByText('Ничего не найдено')).toBeInTheDocument();
    rerender(<ErrorState message="Backend недоступен" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Backend недоступен');
  });
});
