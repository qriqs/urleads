import { fetchCsrfToken, getCookie } from './auth';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CatalogItem {
  id: number;
  ownerId: string;
  name: string;
  sku: string;
  description?: string | null;
  unitPrice: number;
  currency: string;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CatalogItemRequest {
  name: string;
  sku: string;
  description?: string;
  unitPrice: number;
  currency: string;
  active?: boolean;
}

export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';

export interface QuoteItem {
  id?: number;
  catalogItemId?: number | null;
  name: string;
  sku?: string;
  description?: string;
  quantity: number;
  unitPrice: number;
  subtotal?: number;
}

export interface QuoteResponse {
  id: number;
  ownerId: string;
  leadId: number;
  status: QuoteStatus;
  currency: string;
  subtotal: number;
  discountPercentage?: number;
  discountAmount?: number;
  taxPercentage?: number;
  taxAmount?: number;
  total: number;
  validUntil?: string;
  version: number;
  createdAt: string;
  updatedAt?: string;
}

export interface QuoteDetailResponse extends QuoteResponse {
  notes?: string;
  terms?: string;
  items: QuoteItem[];
}

export interface QuoteCreateRequest {
  leadId: number;
  currency: string;
  discountPercentage?: number;
  taxPercentage?: number;
  validUntil?: string;
  notes?: string;
  terms?: string;
  items: Array<{
    catalogItemId?: number;
    name: string;
    sku?: string;
    description?: string;
    quantity: number;
    unitPrice: number;
  }>;
}

export interface CommercialMetrics {
  currency: string;
  totalQuotes: number;
  draftQuotes: number;
  sentQuotes: number;
  acceptedQuotes: number;
  rejectedQuotes: number;
  expiredQuotes: number;
  conversionRate: number;
  totalPipelineAmount: number;
  totalClosedWonAmount: number;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;
let tokenUnavailableUntil = 0;

/**
 * Obtains a JWT access token for communicating with microservices (RS256/Bearer).
 */
export async function getAccessToken(): Promise<string | null> {
  const now = Date.now();
  if (cachedAccessToken && cachedAccessToken.expiresAt > now + 30000) {
    return cachedAccessToken.token;
  }

  if (now < tokenUnavailableUntil) {
    return null;
  }

  try {
    const csrf = await fetchCsrfToken();
    const csrfHeaderName = csrf.headerName || 'X-XSRF-TOKEN';
    const csrfTokenValue = csrf.token || getCookie('XSRF-TOKEN') || '';

    const headers: Record<string, string> = {
      Accept: 'application/json',
    };
    if (csrfTokenValue) {
      headers[csrfHeaderName] = csrfTokenValue;
    }

    const res = await fetch('/api/auth/access-token', {
      method: 'POST',
      credentials: 'include',
      headers,
    });

    if (res.ok) {
      const data = await res.json();
      const token = data.accessToken || data.token;
      const expiresIn = data.expiresIn || 600;
      if (token) {
        cachedAccessToken = {
          token,
          expiresAt: now + expiresIn * 1000,
        };
        tokenUnavailableUntil = 0;
        return token;
      }
    } else if (res.status === 503 || res.status === 404) {
      // Servicio de emisión JWT no configurado en este entorno.
      // Pausa de 5 minutos para evitar spam de peticiones 503 en consola y operar en modo REST.
      tokenUnavailableUntil = now + 5 * 60 * 1000;
    }
  } catch {
    // Si ocurre error de red, pausar reintentos por 30 segundos
    tokenUnavailableUntil = now + 30 * 1000;
  }
  return null;
}

async function getComercialHeaders(): Promise<Record<string, string>> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };

  const token = await getAccessToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return headers;
}

// ---------------------------------------------------------------------------
// Catálogo de Productos y Servicios
// ---------------------------------------------------------------------------

export async function getCatalogApi(params?: {
  currency?: string;
  active?: boolean;
  page?: number;
  size?: number;
}): Promise<PageResponse<CatalogItem>> {
  const headers = await getComercialHeaders();
  const query = new URLSearchParams();
  if (params?.currency) query.set('currency', params.currency);
  if (params?.active !== undefined) query.set('active', String(params.active));
  query.set('page', String(params?.page ?? 0));
  query.set('size', String(params?.size ?? 50));

  const res = await fetch(`/api/v1/catalog?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Error al consultar catálogo (${res.status})`);
  }
  return await res.json();
}

export async function createCatalogItemApi(payload: CatalogItemRequest): Promise<CatalogItem> {
  const headers = await getComercialHeaders();
  const res = await fetch('/api/v1/catalog', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Error al crear ítem de catálogo (${res.status})`);
  }
  return await res.json();
}

// ---------------------------------------------------------------------------
// Cotizaciones (Quotes)
// ---------------------------------------------------------------------------

export async function getQuotesApi(params?: {
  leadId?: number;
  status?: QuoteStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<QuoteResponse>> {
  const headers = await getComercialHeaders();
  const query = new URLSearchParams();
  if (params?.leadId) query.set('leadId', String(params.leadId));
  if (params?.status) query.set('status', params.status);
  query.set('page', String(params?.page ?? 0));
  query.set('size', String(params?.size ?? 20));

  const res = await fetch(`/api/v1/quotes?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Error al consultar cotizaciones (${res.status})`);
  }
  return await res.json();
}

export async function getQuoteByIdApi(id: number): Promise<QuoteDetailResponse> {
  const headers = await getComercialHeaders();
  const res = await fetch(`/api/v1/quotes/${id}`, {
    method: 'GET',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Error al obtener cotización (${res.status})`);
  }
  return await res.json();
}

export async function createQuoteApi(payload: QuoteCreateRequest): Promise<QuoteDetailResponse> {
  const headers = await getComercialHeaders();
  const res = await fetch('/api/v1/quotes', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Error al crear cotización (${res.status})`);
  }
  return await res.json();
}

export async function acceptQuoteApi(id: number): Promise<{ message: string; quoteId: number }> {
  const headers = await getComercialHeaders();
  const res = await fetch(`/api/v1/quotes/${id}/accept`, {
    method: 'POST',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Error al aceptar cotización (${res.status})`);
  }
  return await res.json();
}

export async function rejectQuoteApi(id: number, reason: string): Promise<void> {
  const headers = await getComercialHeaders();
  const res = await fetch(`/api/v1/quotes/${id}/reject`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ reason }),
  });

  if (!res.ok) {
    throw new Error(`Error al rechazar cotización (${res.status})`);
  }
}

// ---------------------------------------------------------------------------
// Métricas Comerciales
// ---------------------------------------------------------------------------

export async function getCommercialMetricsApi(currency?: string): Promise<CommercialMetrics> {
  const headers = await getComercialHeaders();
  const query = new URLSearchParams();
  if (currency) query.set('currency', currency);

  const res = await fetch(`/api/v1/metrics?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Error al obtener métricas comerciales (${res.status})`);
  }
  return await res.json();
}
