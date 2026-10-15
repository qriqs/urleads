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

export type FollowUpFilter = 'all' | 'today' | 'overdue';

export interface CreateLeadPayload {
  name: string;
  phone?: string | null;
  email?: string | null;
  stage: LeadStage;
  nextFollowUp?: string | null;
  service?: string | null;
}

export interface UpdateLeadPayload {
  name: string;
  phone?: string | null;
  email?: string | null;
  stage: LeadStage;
  nextFollowUp?: string | null;
  service?: string | null;
}

export interface GetLeadsParams {
  search?: string;
  page?: number;
  stage?: LeadStage | '' | null;
  followUp?: FollowUpFilter | '' | null;
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
 * Fetches leads list from backend with search, stage, followUp filters and pagination support.
 */
export async function getLeadsApi(
  searchOrParams?: string | GetLeadsParams,
  pageArg: number = 0,
  stageArg?: LeadStage | '' | null,
  followUpArg?: FollowUpFilter | '' | null
): Promise<PageResponse<Lead>> {
  let search = '';
  let page = 0;
  let stage: string | undefined = undefined;
  let followUp: string | undefined = undefined;

  if (typeof searchOrParams === 'object' && searchOrParams !== null) {
    search = searchOrParams.search || '';
    page = searchOrParams.page ?? 0;
    stage = searchOrParams.stage || undefined;
    followUp = searchOrParams.followUp || undefined;
  } else {
    search = searchOrParams || '';
    page = pageArg;
    stage = stageArg || undefined;
    followUp = followUpArg || undefined;
  }

  const params = new URLSearchParams();
  if (search && search.trim()) {
    params.set('search', search.trim());
  }
  if (stage && stage.trim()) {
    params.set('stage', stage.trim());
  }
  if (followUp && followUp.trim() && followUp !== 'all') {
    params.set('followUp', followUp.trim());
  }
  params.set('page', String(Math.max(0, page)));

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
 * Fetches a single lead by ID through GET /api/leads/{id}.
 */
export async function getLeadByIdApi(id: number | string): Promise<Lead> {
  let response: Response;
  try {
    response = await fetch(`/api/leads/${id}`, {
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

  if (response.status === 404) {
    const error: LeadApiError = {
      status: 404,
      code: 'NOT_FOUND',
      message: 'El lead solicitado no existe o fue eliminado.',
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
    message: errorData.message || `Error al obtener el lead (${response.status}).`,
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

/**
 * Deletes a lead by ID through DELETE /api/leads/{id} with CSRF protection.
 */
export async function deleteLeadApi(id: number): Promise<void> {
  const csrf = await fetchCsrfToken();
  const csrfHeaderName = csrf.headerName || 'X-XSRF-TOKEN';
  const csrfTokenValue = csrf.token || getCookie('XSRF-TOKEN') || '';

  const headers: Record<string, string> = {
    Accept: 'application/json',
  };

  if (csrfTokenValue) {
    headers[csrfHeaderName] = csrfTokenValue;
  }

  let response: Response;
  try {
    response = await fetch(`/api/leads/${id}`, {
      method: 'DELETE',
      credentials: 'include',
      headers,
    });
  } catch (netErr) {
    const error: LeadApiError = {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Error de conexión al eliminar el lead.',
    };
    throw error;
  }

  if (response.ok || response.status === 204) {
    return;
  }

  if (response.status === 401) {
    const error: LeadApiError = {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
    };
    throw error;
  }

  let errorBody: any = {};
  try {
    errorBody = await response.json();
  } catch {
    // Non-JSON
  }

  const error: LeadApiError = {
    status: response.status,
    code: errorBody.code || 'DELETE_ERROR',
    message: errorBody.message || `No se pudo eliminar el lead (${response.status}).`,
  };
  throw error;
}

/**
 * Updates an existing lead through PUT /api/leads/{id} with CSRF protection.
 */
export async function updateLeadApi(id: number, payload: UpdateLeadPayload): Promise<Lead> {
  const trimmedName = payload.name?.trim();
  const trimmedPhone = payload.phone?.trim() || null;
  const trimmedEmail = payload.email?.trim() || null;
  const stage = payload.stage || 'NUEVO';
  const nextFollowUp = stage === 'CERRADO' ? null : (payload.nextFollowUp || null);
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
    response = await fetch(`/api/leads/${id}`, {
      method: 'PUT',
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
    message: errorBody.message || `Error al actualizar el lead (${response.status}).`,
    fieldErrors: errorBody.fieldErrors,
  };
  throw error;
}

