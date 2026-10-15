export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Stats {
  searches: number;
  activeSearches: number;
  listings: number;
  activeListings: number;
  deals: number;
}

export interface Search {
  id: string;
  name: string;
  query: string;
  filters: Record<string, unknown>;
  intervalMinutes: number;
  minDealScore: number;
  active: boolean;
  lastRunAt: string | null;
}

export interface SearchInput {
  name: string;
  query: string;
  filters: Record<string, unknown>;
  intervalMinutes: number;
  minDealScore: number;
  active: boolean;
}

export interface IngestionReport {
  processed: number;
  duplicates: number;
  created: number;
  priceChanges: number;
  removed: number;
}

export interface Listing {
  id: string;
  source: string;
  dataOrigin: 'REAL' | 'IMPORTED' | 'DEMO';
  externalId: string;
  title: string;
  description: string;
  price: number;
  currency: string;
  condition: string;
  location: string;
  url: string;
  status: string;
  sellerId: string;
  firstSeenAt: string;
  lastSeenAt: string;
}

export interface DealCard {
  id: string;
  listing: Listing;
  discountPercent: number;
  dealScore: number;
  riskScore: number;
  confidence: number;
  flags: string[];
  explanation: Record<string, unknown>[];
  computedAt: string;
}

export interface MarketPrice {
  id: string;
  productId: string;
  condition: string;
  marketPrice: number;
  currency: string;
  median: number;
  p25: number;
  p75: number;
  sampleSize: number;
  confidenceScore: number;
  confidenceLevel: string;
  outlierIds: string[];
  method: string;
  computedAt: string;
}

export interface PricePoint {
  price: number;
  currency: string;
  observedAt: string;
}

export interface Comparable {
  listing: Listing;
  outlier: boolean;
}

export interface DealDetails extends DealCard {
  marketPrice: MarketPrice;
  comparables: Comparable[];
  outlierIds: string[];
  priceHistory: PricePoint[];
  comparablesBasis: string;
}

export interface ProfitabilityInput {
  purchasePrice: string;
  resalePrice: string;
  logisticsCost: string;
  repairCost: string;
  sellingFees: string;
  otherCosts: string;
  riskReserve: string;
  minimumRequiredProfit: string;
  pessimisticResalePrice: string;
  optimisticResalePrice: string;
  currency: string;
}

export interface ProfitabilityScenario {
  name: string;
  resalePrice: number;
  netProfit: number;
  roiPercent: number;
}

export interface ProfitabilityResult {
  currency: string;
  purchasePrice: number;
  resalePrice: number;
  totalInvestment: number;
  netProfit: number;
  roiPercent: number;
  breakEvenPurchasePrice: number;
  maximumPurchasePrice: number;
  scenarios: ProfitabilityScenario[];
}

export interface ApiProblem {
  status?: number;
  title?: string;
  detail?: string;
  message?: string;
}
