import type {
  ApiProblem,
  DealDetails,
  DealCard,
  Listing,
  Page,
  ProfitabilityInput,
  ProfitabilityResult,
  Search,
  SearchInput,
  Stats,
} from './types';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    });
  } catch {
    throw new ApiError('Не удалось подключиться к API. Проверьте, запущен ли backend.');
  }

  if (!response.ok) {
    let problem: ApiProblem = {};
    try {
      problem = await response.json() as ApiProblem;
    } catch {
      // The API may return an empty or non-JSON error response.
    }
    const detail = problem.detail || problem.message || problem.title;
    const message = response.status === 404
      ? 'Запись не найдена или больше недоступна.'
      : detail || `API вернул ошибку ${response.status}.`;
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

const pageQuery = (page: number, size: number) => `?page=${page}&size=${size}`;

export const api = {
  stats: () => request<Stats>('/stats'),
  deals: (page = 0, size = 10) => request<Page<DealCard>>(`/deals${pageQuery(page, size)}`),
  deal: (id: string) => request<DealDetails>(`/deals/${encodeURIComponent(id)}`),
  listings: (page = 0, size = 10) => request<Page<Listing>>(`/listings${pageQuery(page, size)}`),
  searches: (page = 0, size = 10) => request<Page<Search>>(`/searches${pageQuery(page, size)}`),
  createSearch: (input: SearchInput) => request<Search>('/searches', { method: 'POST', body: JSON.stringify(input) }),
  updateSearch: (id: string, input: SearchInput) => request<Search>(`/searches/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  }),
  deleteSearch: (id: string) => request<void>(`/searches/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  calculateProfitability: (input: ProfitabilityInput) => request<ProfitabilityResult>('/profitability/calculate', {
    method: 'POST',
    body: JSON.stringify(input),
  }),
};
