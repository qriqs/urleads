import { fetchCsrfToken, getCookie } from './auth';

export type LeadStage = 'NUEVO' | 'EN_SEGUIMIENTO' | 'CERRADO';

export interface Lead {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  stage: LeadStage;
  nextFollowUp: string | null;
  service: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateLeadPayload {
  name: string;
  phone?: string | null;
  email?: string | null;
  stage: LeadStage;
  nextFollowUp?: string | null;
  service?: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LeadApiError {
  status: number;
  code?: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

/**
 * Fetches leads list from backend with search and pagination support.
 */
export async function getLeadsApi(
  search: string = '',
  page: number = 0
): Promise<PageResponse<Lead>> {
  const params = new URLSearchParams();
  if (search.trim()) {
    params.set('search', search.trim());
  }
  params.set('page', String(page));

  const url = `/api/leads?${params.toString()}`;

  let response: Response;
  try {
    response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    });
  } catch (netErr) {
    const error: LeadApiError = {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'No se pudo conectar con el servidor. Verifica tu conexión de red.',
    };
    throw error;
  }

  if (response.ok) {
    return await response.json();
  }

  if (response.status === 401) {
    const error: LeadApiError = {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
    };
    throw error;
  }

  let errorData: any = {};
  try {
    errorData = await response.json();
  } catch {
    // Ignore parse error
  }

  const error: LeadApiError = {
    status: response.status,
    code: errorData.code || 'SERVER_ERROR',
    message: errorData.message || `Error al obtener leads (${response.status}).`,
    fieldErrors: errorData.fieldErrors,
  };
  throw error;
}

/**
 * Creates a new lead through POST /api/leads with CSRF protection and real session cookie.
 */
export async function createLeadApi(payload: CreateLeadPayload): Promise<Lead> {
  const trimmedName = payload.name?.trim();
  const trimmedPhone = payload.phone?.trim() || null;
  const trimmedEmail = payload.email?.trim() || null;
  const stage = payload.stage || 'NUEVO';
  const nextFollowUp = payload.nextFollowUp || null;
  const trimmedService = payload.service?.trim() || null;

  // Obtain CSRF token
  const csrf = await fetchCsrfToken();
  const csrfHeaderName = csrf.headerName || 'X-XSRF-TOKEN';
  const csrfTokenValue = csrf.token || getCookie('XSRF-TOKEN') || '';

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };

  if (csrfTokenValue) {
    headers[csrfHeaderName] = csrfTokenValue;
  }

  let response: Response;
  try {
    response = await fetch('/api/leads', {
      method: 'POST',
      credentials: 'include',
      headers,
      body: JSON.stringify({
        name: trimmedName,
        phone: trimmedPhone,
        email: trimmedEmail,
        stage,
        nextFollowUp,
        service: trimmedService,
      }),
    });
  } catch (netErr) {
    const error: LeadApiError = {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Error de conexión. Verifica que el servidor backend esté activo.',
    };
    throw error;
  }

  if (response.ok) {
    return await response.json();
  }

  if (response.status === 401) {
    const error: LeadApiError = {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
    };
    throw error;
  }

  if (response.status === 403) {
    const error: LeadApiError = {
      status: 403,
      code: 'CSRF_ERROR',
      message: 'Error de validación de seguridad (CSRF). Por favor recarga la página.',
    };
    throw error;
  }

  let errorBody: any = {};
  try {
    errorBody = await response.json();
  } catch {
    // Non-JSON response
  }

  const error: LeadApiError = {
    status: response.status,
    code: errorBody.code || 'BAD_REQUEST',
    message: errorBody.message || `Error al crear el lead (${response.status}).`,
    fieldErrors: errorBody.fieldErrors,
  };
  throw error;
}
