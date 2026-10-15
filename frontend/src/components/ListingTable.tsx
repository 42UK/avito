import { Link } from 'react-router-dom';
import { formatDate, formatMoney, humanize } from '../format';
import type { DealCard, Listing } from '../types';

export function ListingsTable({ listings, deals = false }: { listings: (Listing | DealCard)[]; deals?: boolean }) {
  const rows = deals ? (listings as DealCard[]) : null;
  return (
    <div className="table-scroll">
      <table>
        <thead><tr>
          <th>Объявление</th><th>Цена</th>{deals && <><th>Скидка</th><th>Оценка</th></>}<th>Состояние</th><th>Обновлено</th>
        </tr></thead>
        <tbody>
          {listings.map((row) => {
            const deal = rows ? row as DealCard : null;
            const listing = deal?.listing || row as Listing;
            return (
              <tr key={deal?.id || listing.id}>
                <td>
                  <div className="listing-title">
                    {deal ? <Link to={`/deals/${deal.id}`}>{listing.title}</Link> : listing.url
                      ? <a href={listing.url} target="_blank" rel="noopener noreferrer">{listing.title}</a>
                      : listing.title}
                  </div>
                  <div className="muted small">
                    {listing.location || 'Местоположение не указано'} · {listing.source === 'avito' ? 'Авито' : listing.source}
                  </div>
                </td>
                <td className="nowrap">{formatMoney(listing.price, listing.currency)}</td>
                {deal && <><td><span className="discount-pill">−{deal.discountPercent.toLocaleString('ru-RU')}%</span></td><td>{deal.dealScore.toLocaleString('ru-RU')}</td></>}
                <td><span className={`status-pill ${listing.status === 'ACTIVE' ? 'status-active' : ''}`}>{humanize(listing.condition)}</span></td>
                <td className="muted nowrap">{formatDate(listing.lastSeenAt)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
