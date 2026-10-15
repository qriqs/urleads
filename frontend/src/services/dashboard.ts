export interface LeadSeguimientoResumen {
  id: number;
  nombre: string;
  etapa: string;
  proximoSeguimiento: string | null;
}

export interface SeguimientoMetricas {
  hoy: number;
  atrasados: number;
  leadsHoy: LeadSeguimientoResumen[];
  leadsAtrasados: LeadSeguimientoResumen[];
}

export interface DashboardMetrics {
  totalLeads: number;
  leadsPorEtapa: Record<string, number>;
  seguimientos: SeguimientoMetricas;
}

export interface DashboardApiError {
  status: number;
  code?: string;
  message: string;
}

/**
 * Formats a LocalDate string (YYYY-MM-DD) into Peruvian Spanish format without time.
 */
export function formatDayDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "Sin fecha";
  try {
    const [year, month, day] = dateStr.split("-").map(Number);
    if (!year || !month || !day) return dateStr;
    const date = new Date(year, month - 1, day);
    return date.toLocaleDateString("es-PE", {
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  } catch {
    return dateStr;
  }
}

/**
 * Consulta las métricas reales del dashboard para el usuario autenticado.
 * GET /api/dashboard
 */
export async function getDashboardMetricsApi(): Promise<DashboardMetrics> {
  let response: Response;
  try {
    response = await fetch("/api/dashboard", {
      method: "GET",
      credentials: "include",
      headers: {
        Accept: "application/json",
      },
    });
  } catch (netErr) {
    const error: DashboardApiError = {
      status: 0,
      code: "NETWORK_ERROR",
      message: "No se pudo conectar con el servidor. Verifica tu conexión de red.",
    };
    throw error;
  }

  if (response.ok) {
    return await response.json();
  }

  if (response.status === 401) {
    const error: DashboardApiError = {
      status: 401,
      code: "UNAUTHORIZED",
      message: "Tu sesión ha expirado. Por favor, inicia sesión nuevamente.",
    };
    throw error;
  }

  let errorData: any = {};
  try {
    errorData = await response.json();
  } catch {
    // Ignore parse error
  }

  const error: DashboardApiError = {
    status: response.status,
    code: errorData.code || "SERVER_ERROR",
    message:
      errorData.message ||
      `Error al obtener métricas del dashboard (${response.status}).`,
  };
  throw error;
}
