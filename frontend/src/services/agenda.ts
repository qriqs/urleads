import { fetchCsrfToken, getCookie } from './auth';

export type TaskStatus = 'PENDIENTE' | 'COMPLETADA';
export type AppointmentStatus = 'PROGRAMADA' | 'REALIZADA' | 'CANCELADA';
export type AgendaItemType = 'CITA' | 'TAREA';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AgendaItem {
  id: number;
  tipo: AgendaItemType;
  titulo: string;
  descripcion?: string | null;
  fecha: string;
  estado: string;
  leadId?: number | null;
  duracionMinutos?: number | null;
  ubicacion?: string | null;
}

export interface AppointmentOutcome {
  id?: number;
  appointmentId: number;
  resumen?: string | null;
  acuerdos?: string | null;
  proximasAcciones?: string | null;
  registradoEn?: string;
}

export interface Appointment {
  id: number;
  leadId?: number | null;
  titulo: string;
  descripcion?: string | null;
  inicio: string;
  fin: string;
  zonaHoraria: string;
  estado: AppointmentStatus;
  ubicacion?: string | null;
  linkReunion?: string | null;
  outcome?: AppointmentOutcome | null;
  creadoEn?: string;
  actualizadoEn?: string;
}

export interface AgendaTask {
  id: number;
  leadId?: number | null;
  titulo: string;
  descripcion?: string | null;
  fechaLimite?: string | null;
  completada: boolean;
  creadoEn?: string;
  actualizadoEn?: string;
}

export interface DailyPendingSummary {
  fecha: string;
  zonaHoraria: string;
  citasPendientes: number;
  tareasPendientes: number;
  totalPendientes: number;
  citas: Appointment[];
  tareas: AgendaTask[];
}

export interface NotificationItem {
  id: number;
  sourceEventId?: string | null;
  tipo: string;
  titulo: string;
  mensaje: string;
  leida: boolean;
  creadoEn: string;
  data?: Record<string, unknown> | null;
}

export interface UserPreferences {
  zonaHoraria: string;
  horaInicioLaboral: string;
  horaFinLaboral: string;
  canalWebsocket: boolean;
  canalEmail: boolean;
}

export interface CreateAppointmentPayload {
  leadId?: number | null;
  titulo: string;
  descripcion?: string | null;
  inicio: string;
  fin: string;
  zonaHoraria?: string;
  ubicacion?: string | null;
  linkReunion?: string | null;
}

export interface CreateTaskPayload {
  leadId?: number | null;
  titulo: string;
  descripcion?: string | null;
  fechaLimite?: string | null;
}

export interface AppointmentOutcomePayload {
  resumen: string;
  acuerdos?: string;
  proximasAcciones?: string;
}

async function getMutateHeaders(): Promise<Record<string, string>> {
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

  return headers;
}

// ---------------------------------------------------------------------------
// Agenda & Feed combinado
// ---------------------------------------------------------------------------

export async function getAgendaApi(params?: {
  desde?: string;
  hasta?: string;
  leadId?: number;
  tipo?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<AgendaItem>> {
  const query = new URLSearchParams();
  if (params?.desde) query.set('desde', params.desde);
  if (params?.hasta) query.set('hasta', params.hasta);
  if (params?.leadId) query.set('leadId', String(params.leadId));
  if (params?.tipo) query.set('tipo', params.tipo);
  query.set('page', String(params?.page ?? 0));
  query.set('size', String(params?.size ?? 20));

  const res = await fetch(`/api/v1/agenda?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });

  if (!res.ok) {
    throw new Error(`Error al consultar agenda (${res.status})`);
  }
  return await res.json();
}

export async function getDailyPendingApi(fecha?: string, zonaHoraria?: string): Promise<DailyPendingSummary> {
  const query = new URLSearchParams();
  if (fecha) query.set('fecha', fecha);
  if (zonaHoraria) query.set('zonaHoraria', zonaHoraria);

  const res = await fetch(`/api/v1/agenda/daily-pending?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });

  if (!res.ok) {
    throw new Error(`Error al obtener pendientes del día (${res.status})`);
  }
  return await res.json();
}

// ---------------------------------------------------------------------------
// Citas (Appointments)
// ---------------------------------------------------------------------------

export async function getAppointmentsApi(params?: {
  leadId?: number;
  estado?: AppointmentStatus;
  desde?: string;
  hasta?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<Appointment>> {
  const query = new URLSearchParams();
  if (params?.leadId) query.set('leadId', String(params.leadId));
  if (params?.estado) query.set('estado', params.estado);
  if (params?.desde) query.set('desde', params.desde);
  if (params?.hasta) query.set('hasta', params.hasta);
  query.set('page', String(params?.page ?? 0));
  query.set('size', String(params?.size ?? 20));

  const res = await fetch(`/api/v1/appointments?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });

  if (!res.ok) {
    throw new Error(`Error al consultar citas (${res.status})`);
  }
  return await res.json();
}

export async function createAppointmentApi(payload: CreateAppointmentPayload): Promise<Appointment> {
  const headers = await getMutateHeaders();
  const res = await fetch('/api/v1/appointments', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Error al crear cita (${res.status})`);
  }
  return await res.json();
}

export async function updateAppointmentStatusApi(id: number, estado: AppointmentStatus): Promise<Appointment> {
  const headers = await getMutateHeaders();
  const res = await fetch(`/api/v1/appointments/${id}/status`, {
    method: 'PATCH',
    credentials: 'include',
    headers,
    body: JSON.stringify({ estado }),
  });

  if (!res.ok) {
    throw new Error(`Error al actualizar estado de cita (${res.status})`);
  }
  return await res.json();
}

export async function recordAppointmentOutcomeApi(id: number, payload: AppointmentOutcomePayload): Promise<AppointmentOutcome> {
  const headers = await getMutateHeaders();
  const res = await fetch(`/api/v1/appointments/${id}/outcome`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Error al registrar resultado de cita (${res.status})`);
  }
  return await res.json();
}

export async function deleteAppointmentApi(id: number): Promise<void> {
  const headers = await getMutateHeaders();
  const res = await fetch(`/api/v1/appointments/${id}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Error al eliminar cita (${res.status})`);
  }
}

// ---------------------------------------------------------------------------
// Tareas (Tasks)
// ---------------------------------------------------------------------------

export async function getTasksApi(params?: {
  leadId?: number;
  estado?: TaskStatus;
  page?: number;
  size?: number;
}): Promise<PageResponse<AgendaTask>> {
  const query = new URLSearchParams();
  if (params?.leadId) query.set('leadId', String(params.leadId));
  if (params?.estado) query.set('estado', params.estado);
  query.set('page', String(params?.page ?? 0));
  query.set('size', String(params?.size ?? 50));

  const res = await fetch(`/api/v1/tasks?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });

  if (!res.ok) {
    throw new Error(`Error al obtener tareas (${res.status})`);
  }
  return await res.json();
}

export async function createTaskApi(payload: CreateTaskPayload): Promise<AgendaTask> {
  const headers = await getMutateHeaders();
  const res = await fetch('/api/v1/tasks', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Error al crear tarea (${res.status})`);
  }
  return await res.json();
}

export async function toggleTaskCompleteApi(id: number): Promise<AgendaTask> {
  const headers = await getMutateHeaders();
  const res = await fetch(`/api/v1/tasks/${id}/complete`, {
    method: 'PATCH',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Error al cambiar estado de tarea (${res.status})`);
  }
  return await res.json();
}

export async function deleteTaskApi(id: number): Promise<void> {
  const headers = await getMutateHeaders();
  const res = await fetch(`/api/v1/tasks/${id}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });

  if (!res.ok) {
    throw new Error(`Error al eliminar tarea (${res.status})`);
  }
}

// ---------------------------------------------------------------------------
// Notificaciones REST (Respaldo duradero)
// ---------------------------------------------------------------------------

export async function getNotificationsApi(read?: boolean, page = 0, size = 20): Promise<PageResponse<NotificationItem>> {
  const query = new URLSearchParams();
  if (read !== undefined) query.set('read', String(read));
  query.set('page', String(page));
  query.set('size', String(size));

  const res = await fetch(`/api/v1/notifications?${query.toString()}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });

  if (!res.ok) {
    throw new Error(`Error al obtener notificaciones (${res.status})`);
  }
  return await res.json();
}

export async function getUnreadNotificationsCountApi(): Promise<number> {
  const res = await fetch('/api/v1/notifications/unread-count', {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });

  if (!res.ok) return 0;
  const data = await res.json();
  return data.unreadCount ?? 0;
}

export async function markNotificationAsReadApi(id: number): Promise<void> {
  const headers = await getMutateHeaders();
  await fetch(`/api/v1/notifications/${id}/read`, {
    method: 'PATCH',
    credentials: 'include',
    headers,
  });
}

export async function markAllNotificationsAsReadApi(): Promise<void> {
  const headers = await getMutateHeaders();
  await fetch('/api/v1/notifications/mark-all-read', {
    method: 'POST',
    credentials: 'include',
    headers,
  });
}

export async function snoozeNotificationApi(id: number, duracion: '15m' | '1h' | '1d'): Promise<void> {
  const headers = await getMutateHeaders();
  await fetch(`/api/v1/notifications/${id}/snooze`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ duracion }),
  });
}
