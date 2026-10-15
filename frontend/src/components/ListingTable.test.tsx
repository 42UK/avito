import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ListingsTable } from './ListingTable';
import type { Listing } from '../types';

describe('listing table', () => {
  it('shows the saved public Avito listing with a safe link to its page', () => {
    const listing: Listing = {
      id: 'listing-id',
      source: 'avito',
      dataOrigin: 'REAL',
      externalId: '8376354125',
      title: 'iPhone 17',
      description: 'Public listing',
      price: 45000,
      currency: 'RUB',
      condition: 'UNKNOWN',
      location: 'olenegorsk',
      url: 'https://www.avito.ru/olenegorsk/item_8376354125',
      status: 'ACTIVE',
      sellerId: '',
      firstSeenAt: '2026-10-14T12:00:00Z',
      lastSeenAt: '2026-10-14T12:00:00Z',
    };

    render(<MemoryRouter><ListingsTable listings={[listing]} /></MemoryRouter>);

    const link = screen.getByRole('link', { name: 'iPhone 17' });
    expect(link).toHaveAttribute('href', listing.url);
    expect(link).toHaveAttribute('target', '_blank');
    expect(screen.getByText(/Авито/)).toBeInTheDocument();
    expect(screen.getByText(/Внешний источник/)).toBeInTheDocument();
  });
});
