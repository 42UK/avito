import { useEffect, useState } from 'react';
import { Link, NavLink, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from './api';
import { ErrorState, EmptyState, LoadingState, PageError } from './components/States';
import { ListingsTable } from './components/ListingTable';
import { Pagination } from './components/Pagination';
import { ProfitabilityCalculator } from './components/ProfitabilityCalculator';
import { StatCard } from './components/StatCard';
import { formatDate, formatMoney, formatPercent, formatScore, humanize } from './format';
import type { DealDetails, Listing, Search, SearchInput } from './types';

function App() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setMobileNavOpen(false), [location.pathname]);

  return (
    <div className="app-shell">
      <aside className={`sidebar${mobileNavOpen ? ' sidebar-open' : ''}`}>
        <Link to="/" className="brand">
          <span className="brand-mark">V</span>
          <span><strong>выгодные</strong><small>находки</small></span>
        </Link>
        <div className="nav-caption">РАБОЧИЙ СТОЛ</div>
        <nav className="side-nav">
          <NavLink to="/" end><span className="nav-icon">▦</span>Обзор</NavLink>
          <NavLink to="/deals"><span className="nav-icon">↗</span>Выгодные сделки</NavLink>
          <NavLink to="/listings"><span className="nav-icon">▤</span>Объявления</NavLink>
          <NavLink to="/searches"><span className="nav-icon">⌕</span>Поиски</NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="connection-indicator">REST API</div>
          <div className="sidebar-footnote">Данные поступают<br />из вашего backend</div>
        </div>
      </aside>
      <div className="main-column">
        <header className="topbar">
          <button className="mobile-menu" aria-label="Открыть меню" onClick={() => setMobileNavOpen(!mobileNavOpen)}>☰</button>
          <div className="breadcrumb"><span>Панель управления</span><span className="breadcrumb-separator">/</span><strong>{getPageName(location.pathname)}</strong></div>
          <div className="topbar-right"><span className="api-tag">REST API</span><div className="avatar">В</div></div>
        </header>
        <main className="page-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/deals" element={<DealsPage />} />
            <Route path="/deals/:id" element={<DealPage />} />
            <Route path="/listings" element={<ListingsPage />} />
            <Route path="/searches" element={<SearchesPage />} />
            <Route path="*" element={<PageError message="Такого раздела нет." onBack={() => window.location.assign('/')} />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}

function getPageName(path: string) {
  if (path.startsWith('/deals/')) return 'Карточка сделки';
  if (path === '/deals') return 'Сделки';
  if (path === '/listings') return 'Объявления';
  if (path === '/searches') return 'Поиски';
  return 'Обзор';
}

function useLoad<T>(load: () => Promise<T>, dependencies: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const retry = () => {
    setLoading(true);
    setError(null);
    setReloadKey((key) => key + 1);
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    load().then((result) => {
      if (active) setData(result);
    }).catch((reason: unknown) => {
      if (active) setError(reason instanceof Error ? reason : new Error('Произошла неизвестная ошибка.'));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
    // Reloads are explicitly triggered by retry and pagination.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencies, reloadKey]);

  return { data, error, loading, retry, setData };
}

function Dashboard() {
  const { data, error, loading, retry } = useLoad(async () => {
    const [stats, deals, listings] = await Promise.all([api.stats(), api.deals(0, 5), api.listings(0, 5)]);
    return { stats, deals, listings };
  }, []);
  return (
    <>
      <PageHeading eyebrow="СВОДКА СЕРВИСА" title="Обзор" subtitle="Состояние поисков и объявлений — данные из вашего API." />
      {loading ? <LoadingState label="Собираем статистику и последние записи…" /> : error ? <ErrorState message={error.message} onRetry={retry} /> : data && (
        <>
          <section className="stats-grid">
            <StatCard icon="⌕" label="Активные поиски" value={data.stats.activeSearches} note={`из ${data.stats.searches} сохранённых`} />
            <StatCard icon="▤" label="Активные объявления" value={data.stats.activeListings} note={`из ${data.stats.listings} в каталоге`} />
            <StatCard icon="↗" label="Выгодные сделки" value={data.stats.deals} note="доступны в API" accent />
          </section>
          <section className="dashboard-grid">
            <Panel title="Последние выгодные находки" subtitle="Сохранённые оценки со статусом DEAL" action={<Link className="text-link" to="/deals">Все сделки <span>→</span></Link>}>
              {data.deals.content.length ? <ListingsTable listings={data.deals.content} deals /> : <EmptyState title="Пока нет сделок">Когда API сохранит подходящие оценки, они появятся здесь.</EmptyState>}
            </Panel>
            <Panel title="Недавно замечены" subtitle="Объявления из каталога" action={<Link className="text-link" to="/listings">Все объявления <span>→</span></Link>}>
              {data.listings.content.length ? <div className="recent-list">
                {data.listings.content.slice(0, 4).map((listing) => <RecentListing key={listing.id} listing={listing} />)}
              </div> : <EmptyState title="Объявлений пока нет">После поступления данных они отобразятся здесь.</EmptyState>}
            </Panel>
          </section>
          <section className="notice-bar"><span className="notice-icon">i</span><span>Сделки показываются только из сохранённых оценок backend. Панель не рассчитывает цены и не выводит неподтверждённые предложения.</span></section>
        </>
      )}
    </>
  );
}

function DealsPage() {
  const [page, setPage] = useState(0);
  const { data, error, loading, retry } = useLoad(() => api.deals(page, 10), [page]);
  return (
    <>
      <PageHeading eyebrow="ПОДБОРКА" title="Выгодные сделки" subtitle="Только актуальные DEAL-оценки, сохранённые сервисом." />
      <Panel title="Найденные сделки" subtitle={data ? `${data.totalElements} в API` : 'Последняя сохранённая оценка для активных объявлений'}>
        {loading ? <LoadingState /> : error ? <ErrorState message={error.message} onRetry={retry} /> : data?.content.length ?
          <><ListingsTable listings={data.content} deals /><Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} /></> :
          <EmptyState title="Подходящих сделок пока нет">Здесь появятся только сделки, которые backend уже сохранил и признал актуальными.</EmptyState>}
      </Panel>
    </>
  );
}

function ListingsPage() {
  const [page, setPage] = useState(0);
  const { data, error, loading, retry } = useLoad(() => api.listings(page, 10), [page]);
  return (
    <>
      <PageHeading eyebrow="КАТАЛОГ" title="Объявления" subtitle="Сохранённые публичные предложения. Нажмите на название, чтобы открыть объявление на Avito."
        action={<button className="button button-secondary" onClick={retry} disabled={loading}>Обновить</button>} />
      <Panel title="Каталог объявлений" subtitle={data ? `${data.totalElements} записей` : 'Сортировка по времени последнего наблюдения'}>
        {loading ? <LoadingState /> : error ? <ErrorState message={error.message} onRetry={retry} /> : data?.content.length ?
          <><ListingsTable listings={data.content} /><Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} /></> :
          <EmptyState title="Каталог пуст">Сохранённых объявлений пока нет.</EmptyState>}
      </Panel>
    </>
  );
}

function SearchesPage() {
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Search | null | false>(false);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const { data, error, loading, retry, setData } = useLoad(() => api.searches(page, 10), [page]);

  async function save(input: SearchInput) {
    setMutationError(null);
    try {
      if (editing) await api.updateSearch(editing.id, input);
      else await api.createSearch(input);
      setEditing(false);
      retry();
    } catch (reason) {
      setMutationError(reason instanceof Error ? reason.message : 'Не удалось сохранить поиск.');
      throw reason;
    }
  }

  async function deleteSearch(search: Search) {
    if (!window.confirm(`Удалить поиск «${search.name}»?`)) return;
    setMutationError(null);
    setBusyId(search.id);
    try {
      await api.deleteSearch(search.id);
      setData((current) => current ? { ...current, content: current.content.filter((item) => item.id !== search.id), totalElements: Math.max(0, current.totalElements - 1) } : current);
      retry();
    } catch (reason) {
      setMutationError(reason instanceof Error ? reason.message : 'Не удалось удалить поиск.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <PageHeading eyebrow="УПРАВЛЕНИЕ" title="Сохранённые поиски" subtitle="Настройте запросы и порог оценки для мониторинга." action={<button className="button button-primary" onClick={() => { setMutationError(null); setEditing(null); }}>＋ Новый поиск</button>} />
      {mutationError && <div className="inline-error" role="alert">{mutationError}</div>}
      <Panel title="Ваши поиски" subtitle={data ? `${data.totalElements} сохранено` : 'Настройки запросов'}>
        {loading ? <LoadingState /> : error ? <ErrorState message={error.message} onRetry={retry} /> : data?.content.length ?
          <>
            <div className="search-list">
              {data.content.map((search) => <SearchRow key={search.id} search={search} busy={busyId === search.id} onEdit={() => setEditing(search)} onDelete={() => void deleteSearch(search)} />)}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </> : <EmptyState title="Поисков пока нет">Создайте первый поисковый запрос, чтобы сохранить его в сервисе.</EmptyState>}
      </Panel>
      {editing !== false && <SearchDialog search={editing} onClose={() => setEditing(false)} onSave={save} />}
    </>
  );
}

function SearchRow({ search, busy, onEdit, onDelete }: { search: Search; busy: boolean; onEdit: () => void; onDelete: () => void }) {
  return (
    <article className="search-row">
      <div className="search-row-icon">⌕</div>
      <div className="search-row-main">
        <div className="search-row-heading"><strong>{search.name}</strong><span className={`status-pill ${search.active ? 'status-active' : ''}`}>{search.active ? 'Активен' : 'Приостановлен'}</span></div>
        <div className="search-query">{search.query}</div>
        <div className="search-meta">
          <span>Порог {formatScore(search.minDealScore)}</span><span>Каждые {search.intervalMinutes} мин</span><span>Последний запуск: {formatDate(search.lastRunAt)}</span>
        </div>
      </div>
      <div className="search-row-actions">
        <button className="button button-secondary" onClick={onEdit}>Изменить</button>
        <button className="icon-button danger-text" aria-label={`Удалить ${search.name}`} disabled={busy} onClick={onDelete}>{busy ? '…' : '×'}</button>
      </div>
    </article>
  );
}

function SearchDialog({ search, onClose, onSave }: { search: Search | null; onClose: () => void; onSave: (input: SearchInput) => Promise<void> }) {
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [name, setName] = useState(search?.name || '');
  const [query, setQuery] = useState(search?.query || '');
  const [intervalMinutes, setIntervalMinutes] = useState(String(search?.intervalMinutes ?? 60));
  const [minDealScore, setMinDealScore] = useState(String(search?.minDealScore ?? 40));
  const [active, setActive] = useState(search?.active ?? true);
  const [priceMin, setPriceMin] = useState(String(search?.filters.priceMin ?? ''));
  const [priceMax, setPriceMax] = useState(String(search?.filters.priceMax ?? ''));
  const [location, setLocation] = useState(String(search?.filters.location ?? ''));
  const [category, setCategory] = useState(String(search?.filters.category ?? ''));

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const filters: Record<string, unknown> = {};
    if (priceMin !== '') filters.priceMin = Number(priceMin);
    if (priceMax !== '') filters.priceMax = Number(priceMax);
    if (location.trim()) filters.location = location.trim();
    if (category.trim()) filters.category = category.trim();
    setSaving(true);
    try {
      await onSave({
        name: name.trim(),
        query: query.trim(),
        filters,
        intervalMinutes: Number(intervalMinutes),
        minDealScore: Number(minDealScore),
        active,
      });
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : 'Не удалось сохранить поиск.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="search-dialog-title">
        <div className="dialog-heading"><div><span className="eyebrow">ПОИСКОВЫЙ ЗАПРОС</span><h2 id="search-dialog-title">{search ? 'Изменить поиск' : 'Новый поиск'}</h2></div><button className="icon-button" aria-label="Закрыть" onClick={onClose}>×</button></div>
        <form onSubmit={(event) => void submit(event)}>
          <label>Название<input required maxLength={500} value={name} onChange={(event) => setName(event.target.value)} placeholder="Например, iPhone 14" /></label>
          <label>Поисковый запрос<input required maxLength={5000} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Что искать" /></label>
          <div className="form-grid">
            <label>Цена от<input type="number" min="0" step="any" value={priceMin} onChange={(event) => setPriceMin(event.target.value)} placeholder="Без ограничения" /></label>
            <label>Цена до<input type="number" min="0" step="any" value={priceMax} onChange={(event) => setPriceMax(event.target.value)} placeholder="Без ограничения" /></label>
            <label>Город<input value={location} onChange={(event) => setLocation(event.target.value)} placeholder="Любой город" /></label>
            <label>Категория<input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="Любая категория" /></label>
            <label>Интервал, минут<input type="number" required min="1" max="10080" value={intervalMinutes} onChange={(event) => setIntervalMinutes(event.target.value)} /></label>
            <label>Минимальная оценка<input type="number" required min="0" max="100" step="0.01" value={minDealScore} onChange={(event) => setMinDealScore(event.target.value)} /></label>
          </div>
          <label className="checkbox-label"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /> Поиск активен</label>
          {formError && <div className="inline-error" role="alert">{formError}</div>}
          <div className="dialog-actions"><button type="button" className="button button-secondary" onClick={onClose}>Отмена</button><button className="button button-primary" disabled={saving}>{saving ? 'Сохраняем…' : search ? 'Сохранить изменения' : 'Создать поиск'}</button></div>
        </form>
      </section>
    </div>
  );
}

function DealPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { data, error, loading, retry } = useLoad(() => api.deal(id), [id]);
  if (loading) return <LoadingState label="Загружаем сохранённую оценку сделки…" />;
  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    if (notFound) return <PageError message={error.message} onBack={() => navigate('/deals')} />;
    return <ErrorState message={error.message} onRetry={retry} />;
  }
  if (!data) return null;
  return <DealDetailsView deal={data} onBack={() => navigate('/deals')} />;
}

function DealDetailsView({ deal, onBack }: { deal: DealDetails; onBack: () => void }) {
  const listing = deal.listing;
  return (
    <>
      <button className="back-link" onClick={onBack}>← Все сделки</button>
      <PageHeading eyebrow="СОХРАНЁННАЯ ОЦЕНКА" title={listing.title} subtitle={`${listing.location || 'Местоположение не указано'} · ${humanize(listing.condition)} · обновлено ${formatDate(listing.lastSeenAt)}`} action={listing.url ? <a className="button button-secondary" href={listing.url} target="_blank" rel="noreferrer">Открыть объявление ↗</a> : undefined} />
      <section className="deal-hero">
        <div className="deal-price-block"><span className="field-label">Цена объявления</span><strong>{formatMoney(listing.price, listing.currency)}</strong><span className="deal-discount">−{formatPercent(deal.discountPercent)} от рынка</span></div>
        <div className="deal-market-block"><span className="field-label">Оценка рынка</span><strong>{formatMoney(deal.marketPrice.marketPrice, deal.marketPrice.currency)}</strong><span className="muted">Медиана: {formatMoney(deal.marketPrice.median, deal.marketPrice.currency)}</span></div>
        <div className="deal-score-block"><span className="field-label">Оценка сделки</span><strong>{formatScore(deal.dealScore)}<small> / 100</small></strong><span className="muted">Сохранена {formatDate(deal.computedAt)}</span></div>
      </section>
      <section className="stats-grid detail-stats">
        <StatCard icon="!" label="Риск" value={`${formatScore(deal.riskScore)} / 100`} note={deal.flags.length ? `${deal.flags.length} флага оценки` : 'Флаги не зафиксированы'} />
        <StatCard icon="◉" label="Уверенность оценки" value={formatPercent(deal.confidence * 100)} note="Из сохранённого score" />
        <StatCard icon="▥" label="Уверенность рынка" value={humanize(deal.marketPrice.confidenceLevel)} note={`${deal.marketPrice.sampleSize} объявлений в снимке`} />
      </section>
      <ProfitabilityCalculator
        key={listing.id}
        purchasePrice={listing.price}
        marketPrice={deal.marketPrice.marketPrice}
        p25={deal.marketPrice.p25}
        p75={deal.marketPrice.p75}
        currency={deal.marketPrice.currency}
      />
      <section className="dashboard-grid deal-sections">
        <Panel title="Снимок рынка" subtitle={`Метод: ${humanize(deal.marketPrice.method)}`}>
          <div className="market-grid">
            <Metric label="Медианная цена" value={formatMoney(deal.marketPrice.median, deal.marketPrice.currency)} />
            <Metric label="25-й процентиль" value={formatMoney(deal.marketPrice.p25, deal.marketPrice.currency)} />
            <Metric label="75-й процентиль" value={formatMoney(deal.marketPrice.p75, deal.marketPrice.currency)} />
            <Metric label="Размер выборки" value={deal.marketPrice.sampleSize.toLocaleString('ru-RU')} />
          </div>
          <p className="muted small">Снимок рассчитан {formatDate(deal.marketPrice.computedAt)}. Повторное вычисление при просмотре не выполняется.</p>
        </Panel>
        <Panel title="Почему сделка отмечена" subtitle="Объяснение сохранённой оценки">
          {deal.explanation.length ? <div className="explanation-list">{deal.explanation.map((item, index) => <Explanation key={index} item={item} />)}</div> : <EmptyState title="Пояснение отсутствует">API не вернуло текстовых причин для этой оценки.</EmptyState>}
          {deal.flags.length > 0 && <div className="flag-list">{deal.flags.map((flag) => <span key={flag} className="status-pill">{humanize(flag)}</span>)}</div>}
        </Panel>
      </section>
      <Panel title="Похожие объявления" subtitle={`${deal.comparables.length} в текущей выборке`}>
        <p className="notice-inline">Состав выборки: {humanize(deal.comparablesBasis)}. Это текущие совпавшие объявления, а не исторический список, использованный при расчёте.</p>
        {deal.comparables.length ? <div className="table-scroll"><table>
          <thead><tr><th>Объявление</th><th>Цена</th><th>Состояние</th><th>Последнее наблюдение</th><th>Пометка</th></tr></thead>
          <tbody>{deal.comparables.map(({ listing: comparable, outlier }) => <tr key={comparable.id}>
            <td><div className="listing-title">{comparable.url ? <a href={comparable.url} target="_blank" rel="noreferrer">{comparable.title}</a> : comparable.title}</div><div className="muted small">{comparable.location}</div></td>
            <td className="nowrap">{formatMoney(comparable.price, comparable.currency)}</td><td>{humanize(comparable.condition)}</td><td>{formatDate(comparable.lastSeenAt)}</td>
            <td>{outlier ? <span className="outlier-pill">Выброс</span> : <span className="muted">—</span>}</td>
          </tr>)}</tbody>
        </table></div> : <EmptyState title="Сравнения не найдены">API не вернуло активных объявлений с совпавшим товаром и состоянием.</EmptyState>}
        {deal.outlierIds.length > 0 && <p className="muted small">Сохранённых выбросов: {deal.outlierIds.length}</p>}
      </Panel>
      <section className="dashboard-grid detail-sections">
        <Panel title="История цены" subtitle="Наблюдения для этого объявления">
          {deal.priceHistory.length ? <div className="history-list">{deal.priceHistory.map((point, index) => <div className="history-row" key={`${point.observedAt}-${index}`}><span className="history-dot" /><span>{formatDate(point.observedAt)}</span><strong>{formatMoney(point.price, point.currency)}</strong></div>)}</div> : <EmptyState title="История пока недоступна">API не вернуло сохранённых наблюдений цены.</EmptyState>}
        </Panel>
        <Panel title="Объявление" subtitle="Исходные данные каталога">
          <dl className="listing-facts">
            <Metric label="Источник" value={listing.source} /><Metric label="ID источника" value={listing.externalId} />
            <Metric label="Статус" value={humanize(listing.status)} /><Metric label="Впервые замечено" value={formatDate(listing.firstSeenAt)} />
          </dl>
          {listing.description && <p className="description-text">{listing.description}</p>}
        </Panel>
      </section>
    </>
  );
}

function Explanation({ item }: { item: Record<string, unknown> }) {
  const primary = ['message', 'reason', 'description', 'text', 'code'].find((key) => typeof item[key] === 'string');
  const title = primary ? String(item[primary]) : Object.entries(item).map(([key, value]) => `${humanize(key)}: ${String(value)}`).join(' · ');
  const rest = Object.entries(item).filter(([key]) => key !== primary);
  return <div className="explanation-item"><span className="explanation-check">✓</span><div><strong>{title || 'Учтено при оценке'}</strong>{rest.length > 0 && <p>{rest.map(([key, value]) => `${humanize(key)}: ${formatExplanationValue(value)}`).join(' · ')}</p>}</div></div>;
}

function formatExplanationValue(value: unknown): string {
  return typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value);
}

function RecentListing({ listing }: { listing: Listing }) {
  return <article className="recent-item"><div className="recent-item-mark">{listing.title.slice(0, 1).toLocaleUpperCase('ru-RU') || '•'}</div><div className="recent-item-copy"><strong>{listing.title}</strong><span>{listing.location || humanize(listing.condition)}</span></div><strong>{formatMoney(listing.price, listing.currency)}</strong></article>;
}

function PageHeading({ eyebrow, title, subtitle, action }: { eyebrow: string; title: string; subtitle: string; action?: React.ReactNode }) {
  return <div className="page-heading"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{subtitle}</p></div>{action && <div className="page-heading-action">{action}</div>}</div>;
}

function Panel({ title, subtitle, action, children }: { title: string; subtitle?: string; action?: React.ReactNode; children: React.ReactNode }) {
  return <section className="panel"><div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>{children}</section>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="metric"><dt>{label}</dt><dd>{value || '—'}</dd></div>;
}

export default App;
