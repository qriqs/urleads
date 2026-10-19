import React from 'react';
import { Bell, X, Check, Clock, Radio, ShieldCheck, AlertTriangle } from 'lucide-react';
import { useNotifications } from '../../context/NotificationsContext';

interface NotificationsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigateLead?: (leadId: number) => void;
}

export const NotificationsModal: React.FC<NotificationsModalProps> = ({
  isOpen,
  onClose,
  onNavigateLead,
}) => {
  const {
    isConnected,
    notifications,
    unreadCount,
    markAsRead,
    markAllAsRead,
    snooze,
  } = useNotifications();

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-2xl overflow-hidden p-6 space-y-4 max-h-[85vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-border shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="relative">
              <Bell className="h-5 w-5 text-accent" />
              {unreadCount > 0 && (
                <span className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-rose-500 rounded-full animate-pulse" />
              )}
            </div>
            <div>
              <h3 className="text-base font-bold text-foreground">Notificaciones</h3>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span
                  className={`w-2 h-2 rounded-full ${
                    isConnected ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'
                  }`}
                />
                <span className="text-[11px] text-muted-foreground font-medium">
                  {isConnected
                    ? 'Conectado en vivo (WebSocket RFC 6455)'
                    : 'Modo respaldo duradero (REST)'}
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {notifications.length > 0 && (
              <button
                type="button"
                onClick={markAllAsRead}
                className="text-xs text-muted-foreground hover:text-accent font-semibold px-2 py-1 rounded-lg hover:bg-secondary transition-colors cursor-pointer"
              >
                Marcar todas leídas
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Content list */}
        <div className="flex-1 overflow-y-auto space-y-3 pr-1">
          {notifications.length === 0 ? (
            <div className="text-center py-12 px-4 space-y-2">
              <ShieldCheck className="h-10 w-10 text-muted-foreground/40 mx-auto" />
              <p className="text-sm font-semibold text-foreground">Todo al día</p>
              <p className="text-xs text-muted-foreground">
                No tienes notificaciones pendientes en este momento. Los nuevos avisos y eventos de RabbitMQ aparecerán aquí en vivo.
              </p>
            </div>
          ) : (
            notifications.map((notif) => (
              <div
                key={notif.id}
                className={`p-4 rounded-2xl border transition-all ${
                  notif.leida
                    ? 'bg-secondary/20 border-border/60 opacity-80'
                    : 'bg-background border-accent/30 shadow-xs'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="space-y-1 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-foreground">
                        {notif.titulo}
                      </span>
                      {!notif.leida && (
                        <span className="px-1.5 py-0.5 rounded-full text-[9px] font-extrabold bg-accent/20 text-accent uppercase">
                          Nueva
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {notif.mensaje}
                    </p>
                  </div>
                </div>

                {/* Actions: Mark read, Snooze */}
                <div className="flex items-center justify-between pt-3 mt-2 border-t border-border/40 text-[11px]">
                  <span className="text-muted-foreground">
                    {new Date(notif.creadoEn).toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>

                  <div className="flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => snooze(notif.id, '15m')}
                      className="px-2 py-0.5 rounded-md hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors flex items-center gap-1"
                      title="Posponer 15 minutos"
                    >
                      <Clock className="h-3 w-3" />
                      <span>15m</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => snooze(notif.id, '1h')}
                      className="px-2 py-0.5 rounded-md hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors flex items-center gap-1"
                      title="Posponer 1 hora"
                    >
                      <Clock className="h-3 w-3" />
                      <span>1h</span>
                    </button>
                    {!notif.leida && (
                      <button
                        type="button"
                        onClick={() => markAsRead(notif.id)}
                        className="px-2 py-0.5 rounded-md bg-accent/10 hover:bg-accent/20 text-accent font-semibold transition-colors flex items-center gap-1"
                      >
                        <Check className="h-3 w-3" />
                        <span>Leída</span>
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
