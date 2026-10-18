import { fetchCsrfToken, getCookie } from './auth';

export interface LeadNoteResponse {
  id: number;
  content: string;
  createdAt: string;
}

export interface NoteApiError {
  status: number;
  code?: string;
  message: string;
}

/**
 * Fetches all internal notes for a specific lead in chronological ascending order.
 * GET /api/leads/{id}/notes
 */
export async function getNotesByLeadIdApi(leadId: string | number): Promise<LeadNoteResponse[]> {
  let response: Response;
  try {
    response = await fetch(`/api/leads/${leadId}/notes`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    });
  } catch (netErr) {
    const error: NoteApiError = {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Error de conexión. Verifica que el servidor backend esté activo.',
    };
    throw error;
  }

  if (response.ok) {
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  }

  if (response.status === 401) {
    const error: NoteApiError = {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
    };
    throw error;
  }

  if (response.status === 404) {
    const error: NoteApiError = {
      status: 404,
      code: 'NOT_FOUND',
      message: 'El lead solicitado no existe o no tienes permisos para ver sus notas.',
    };
    throw error;
  }

  let errorData: any = {};
  try {
    errorData = await response.json();
  } catch {
    // Ignore JSON parse error
  }

  const error: NoteApiError = {
    status: response.status,
    code: errorData.code || 'SERVER_ERROR',
    message: errorData.message || `Error al obtener notas (${response.status}).`,
  };
  throw error;
}

/**
 * Creates a new internal note for a specific lead.
 * POST /api/leads/{id}/notes
 * Body: { content: string }
 * Requires CSRF token and valid session.
 */
export async function createNoteApi(
  leadId: string | number,
  content: string
): Promise<LeadNoteResponse> {
  const trimmedContent = content.trim();

  // Defensive client-side validations
  if (!trimmedContent) {
    const error: NoteApiError = {
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'El contenido de la nota no puede estar vacío.',
    };
    throw error;
  }

  if (trimmedContent.length > 2000) {
    const error: NoteApiError = {
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'El contenido de la nota no puede exceder 2.000 caracteres.',
    };
    throw error;
  }

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
    response = await fetch(`/api/leads/${leadId}/notes`, {
      method: 'POST',
      credentials: 'include',
      headers,
      body: JSON.stringify({
        content: trimmedContent,
      }),
    });
  } catch (netErr) {
    const error: NoteApiError = {
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Error de conexión. Verifica que el servidor backend esté activo.',
    };
    throw error;
  }

  if (response.status === 201 || response.ok) {
    return (await response.json()) as LeadNoteResponse;
  }

  if (response.status === 401) {
    const error: NoteApiError = {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.',
    };
    throw error;
  }

  if (response.status === 403) {
    const error: NoteApiError = {
      status: 403,
      code: 'FORBIDDEN',
      message: 'Acceso no autorizado o token de seguridad inválido.',
    };
    throw error;
  }

  if (response.status === 404) {
    const error: NoteApiError = {
      status: 404,
      code: 'NOT_FOUND',
      message: 'El lead solicitado no existe o no tienes permisos para agregarle notas.',
    };
    throw error;
  }

  let errorData: any = {};
  try {
    errorData = await response.json();
  } catch {
    // Ignore JSON parse error
  }

  const error: NoteApiError = {
    status: response.status,
    code: errorData.code || 'SERVER_ERROR',
    message: errorData.message || `Error al guardar la nota (${response.status}).`,
  };
  throw error;
}
