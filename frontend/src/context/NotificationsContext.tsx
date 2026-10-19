import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import { sileo } from 'sileo';
import { useAuth } from './AuthContext';
import { getAccessToken } from '../services/comercial';
import {
  NotificationItem,
  getNotificationsApi,
  getUnreadNotificationsCountApi,
  markNotificationAsReadApi,
  markAllNotificationsAsReadApi,
  snoozeNotificationApi,
} from '../services/agenda';

interface NotificationsContextType {
  isConnected: boolean;
  notifications: NotificationItem[];
  unreadCount: number;
  refreshNotifications: () => Promise<void>;
  markAsRead: (id: number) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  snooze: (id: number, duracion: '15m' | '1h' | '1d') => Promise<void>;
}

const NotificationsContext = createContext<NotificationsContextType | undefined>(undefined);

export const NotificationsProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, user } = useAuth();
  const [isConnected, setIsConnected] = useState<boolean>(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const pingIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const reconnectAttemptsRef = useRef<number>(0);

  const refreshNotifications = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      const [pageData, count] = await Promise.all([
        getNotificationsApi(undefined, 0, 20),
        getUnreadNotificationsCountApi(),
      ]);
      setNotifications(pageData.content || []);
      setUnreadCount(count);
    } catch {
      // Backend not ready or offline; keep existing state
    }
  }, [isAuthenticated]);

  const markAsRead = async (id: number) => {
    try {
      await markNotificationAsReadApi(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, leida: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch {
      sileo.error({ title: 'Error', description: 'No se pudo marcar la notificación como leída.' });
    }
  };

  const markAllAsRead = async () => {
    try {
      await markAllNotificationsAsReadApi();
      setNotifications((prev) => prev.map((n) => ({ ...n, leida: true })));
      setUnreadCount(0);
      sileo.info({ title: 'Notificaciones', description: 'Todas las notificaciones marcadas como leídas.' });
    } catch {
      sileo.error({ title: 'Error', description: 'No se pudieron marcar las notificaciones.' });
    }
  };

  const snooze = async (id: number, duracion: '15m' | '1h' | '1d') => {
    try {
      await snoozeNotificationApi(id, duracion);
      setNotifications((prev) => prev.filter((n) => n.id !== id));
      setUnreadCount((prev) => Math.max(0, prev - 1));
      sileo.info({ title: 'Notificación pospuesta', description: `Se te recordará en ${duracion}.` });
    } catch {
      sileo.error({ title: 'Error', description: 'No se pudo posponer la notificación.' });
    }
  };

  // -------------------------------------------------------------------------
  // WebSocket Connection & Reconnection
  // -------------------------------------------------------------------------
  const connectWebSocket = useCallback(async () => {
    if (!isAuthenticated || typeof window === 'undefined') return;

    // Clear existing socket if any
    if (wsRef.current) {
      try {
        wsRef.current.close();
      } catch {
        // ignore
      }
      wsRef.current = null;
    }

    const token = await getAccessToken();
    if (!token) {
      // Servicio de emisión de token JWT no configurado o en pausa (503 Service Unavailable).
      // Se opera fluidamente en modo respaldo duradero (REST) sin saturar con bucles de reintentos.
      setIsConnected(false);
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/notifications`;

    try {
      const socket = new WebSocket(wsUrl);
      wsRef.current = socket;

      socket.onopen = () => {
        reconnectAttemptsRef.current = 0;
        // First frame: Mandatory RFC 6455 authentication
        socket.send(JSON.stringify({
          type: 'authenticate',
          accessToken: token,
        }));

        // Keepalive ping every 30 seconds
        if (pingIntervalRef.current) clearInterval(pingIntervalRef.current);
        pingIntervalRef.current = setInterval(() => {
          if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: 'PING' }));
          }
        }, 30000);
      };

      socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          if (payload.type === 'authenticated') {
            setIsConnected(true);
          } else if (payload.type === 'notification.created' && payload.data) {
            const newNotif: NotificationItem = {
              id: payload.data.id,
              sourceEventId: payload.data.sourceEventId,
              tipo: payload.data.tipo || 'INFO',
              titulo: payload.data.titulo || 'Nueva Notificación',
              mensaje: payload.data.mensaje || '',
              leida: false,
              creadoEn: payload.data.createdAt || new Date().toISOString(),
              data: payload.data.payloadData,
            };

            setNotifications((prev) => [newNotif, ...prev.filter((n) => n.id !== newNotif.id)]);
            setUnreadCount((prev) => prev + 1);

            sileo.info({
              title: newNotif.titulo,
              description: newNotif.mensaje,
            });
          }
        } catch {
          // Ignore non-json or unparsed frames
        }
      };

      socket.onclose = () => {
        setIsConnected(false);
        if (pingIntervalRef.current) {
          clearInterval(pingIntervalRef.current);
          pingIntervalRef.current = null;
        }

        // Exponential backoff reconnect
        if (isAuthenticated) {
          const attempts = reconnectAttemptsRef.current;
          const delay = Math.min(1000 * Math.pow(2, attempts), 30000);
          reconnectAttemptsRef.current += 1;
          reconnectTimeoutRef.current = setTimeout(connectWebSocket, delay);
        }
      };

      socket.onerror = () => {
        // Handled in onclose
      };
    } catch {
      setIsConnected(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (isAuthenticated) {
      refreshNotifications();
      connectWebSocket();
    } else {
      setIsConnected(false);
      setNotifications([]);
      setUnreadCount(0);
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (pingIntervalRef.current) {
        clearInterval(pingIntervalRef.current);
      }
    }

    return () => {
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (pingIntervalRef.current) {
        clearInterval(pingIntervalRef.current);
      }
    };
  }, [isAuthenticated, user?.id, connectWebSocket, refreshNotifications]);

  // Modo respaldo REST duradero: sincronizar periódicamente cada 60s si el WebSocket no está conectado
  useEffect(() => {
    if (!isAuthenticated || isConnected) return;
    const interval = setInterval(() => {
      refreshNotifications();
    }, 60000);
    return () => clearInterval(interval);
  }, [isAuthenticated, isConnected, refreshNotifications]);

  return (
    <NotificationsContext.Provider
      value={{
        isConnected,
        notifications,
        unreadCount,
        refreshNotifications,
        markAsRead,
        markAllAsRead,
        snooze,
      }}
    >
      {children}
    </NotificationsContext.Provider>
  );
};

export function useNotifications(): NotificationsContextType {
  const context = useContext(NotificationsContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationsProvider');
  }
  return context;
}
