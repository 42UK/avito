import type { ReactNode } from 'react';

export function LoadingState({ label = 'Загружаем данные…' }: { label?: string }) {
  return <div className="state-panel" role="status"><span className="spinner" />{label}</div>;
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="state-panel empty-state"><span className="empty-mark">∅</span><strong>{title}</strong>{children && <p>{children}</p>}</div>;
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state-panel error-state" role="alert">
      <span className="error-mark">!</span>
      <div><strong>Не удалось загрузить данные</strong><p>{message}</p></div>
      {onRetry && <button className="button button-secondary" onClick={onRetry}>Повторить</button>}
    </div>
  );
}

export function PageError({ message, onBack }: { message: string; onBack: () => void }) {
  return <div className="page-error"><div className="not-found-code">404</div><h1>Страница недоступна</h1><p>{message}</p><button className="button button-primary" onClick={onBack}>На главную</button></div>;
}
