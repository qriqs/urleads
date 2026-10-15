export interface User {
  id: number;
  username: string;
}

export interface CsrfTokenResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface ApiError {
  message: string;
  fieldErrors?: Record<string, string>;
}

/**
 * Extracts a cookie value by name from document.cookie safely.
 */
export function getCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(new RegExp('(^|;\\s*)(' + name + ')=([^;]*)'));
  return match ? decodeURIComponent(match[3]) : null;
}

let cachedCsrfToken: CsrfTokenResponse | null = null;

/**
 * Fetches the current CSRF token from Spring Security.
 */
export async function fetchCsrfToken(): Promise<CsrfTokenResponse> {
  try {
    const response = await fetch('/api/auth/csrf', {
      method: 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    });

    if (response.ok) {
      const data = await response.json();
      cachedCsrfToken = data;
      return data;
    }
  } catch (err) {
    // If endpoint fails, fallback to cookie if available
  }

  // Fallback to XSRF-TOKEN cookie if present
  const cookieToken = getCookie('XSRF-TOKEN');
  if (cookieToken) {
    return {
      headerName: 'X-XSRF-TOKEN',
      parameterName: '_csrf',
      token: cookieToken,
    };
  }

  return {
    headerName: 'X-XSRF-TOKEN',
    parameterName: '_csrf',
    token: '',
  };
}

/**
 * Logs in with Spring Security session-based auth.
 * Does not store credentials or tokens in localStorage/sessionStorage.
 */
export async function loginApi(credentials: LoginCredentials): Promise<User> {
  const trimmedUsername = credentials.username?.trim();
  const password = credentials.password;

  if (!trimmedUsername || !password) {
    throw new Error('El usuario y la contraseña son obligatorios.');
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
    response = await fetch('/api/auth/login', {
      method: 'POST',
      credentials: 'include', // Ensures session cookie JSESSIONID is sent & stored by browser
      headers,
      body: JSON.stringify({
        username: trimmedUsername,
        password: password,
      }),
    });
  } catch (netErr) {
    throw new Error('Error de conexión. Verifica que el servidor backend esté activo.');
  }

  if (response.ok) {
    return await response.json();
  }

  if (response.status === 401) {
    throw new Error('Credenciales inválidas.');
  }

  if (response.status === 400) {
    try {
      const errData = await response.json();
      throw new Error(errData.message || 'El usuario y la contraseña son obligatorios.');
    } catch {
      throw new Error('El usuario y la contraseña son obligatorios.');
    }
  }

  if (response.status === 403) {
    throw new Error('Error de validación CSRF. Por favor, recarga la página.');
  }

  throw new Error(`Error en el servidor (${response.status}). Intenta nuevamente.`);
}

/**
 * Verifies active session with Spring Security.
 * Returns the current authenticated user or null.
 */
export async function checkSessionApi(): Promise<User | null> {
  try {
    const response = await fetch('/api/auth/session', {
      method: 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    });

    if (response.ok) {
      return await response.json();
    }
    return null;
  } catch (err) {
    return null;
  }
}

/**
 * Logs out the user from Spring Security session.
 */
export async function logoutApi(): Promise<void> {
  const csrf = await fetchCsrfToken();
  const csrfHeaderName = csrf.headerName || 'X-XSRF-TOKEN';
  const csrfTokenValue = csrf.token || getCookie('XSRF-TOKEN') || '';

  const headers: Record<string, string> = {
    Accept: 'application/json',
  };

  if (csrfTokenValue) {
    headers[csrfHeaderName] = csrfTokenValue;
  }

  try {
    await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers,
    });
  } catch (err) {
    // Session invalidated on client even if network fails
  }
}
