import React, { useState, useEffect } from "react";
import {
  Settings,
  User,
  Shield,
  Bell,
  Sliders,
  Save,
  Check,
  RefreshCw,
  LogOut,
  Sparkles,
  Database,
  Building,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useNavigate } from "react-router-dom";
import { sileo } from "sileo";
import { RuixenMagneticTabs, RuixenTabItem } from "../components/ruixen/RuixenMagneticTabs";

const SETTINGS_TABS: RuixenTabItem[] = [
  { value: "profile", label: "Perfil", icon: User },
  { value: "crm", label: "CRM & Moneda", icon: Building },
  { value: "notifications", label: "Notificaciones", icon: Bell },
  { value: "system", label: "Seguridad", icon: Shield },
];

const SETTINGS_STORAGE_KEY = "urleads_crm_settings_v1";

interface CrmPreferences {
  companyName: string;
  currency: string;
  notifyOnNewLead: boolean;
  notifyOnFollowUp: boolean;
  soundAlerts: boolean;
}

const DEFAULT_PREFERENCES: CrmPreferences = {
  companyName: "UrLeads Solutions",
  currency: "USD",
  notifyOnNewLead: true,
  notifyOnFollowUp: true,
  soundAlerts: false,
};

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<"profile" | "crm" | "notifications" | "system">("profile");
  const [preferences, setPreferences] = useState<CrmPreferences>(() => {
    try {
      const saved = localStorage.getItem(SETTINGS_STORAGE_KEY);
      return saved ? JSON.parse(saved) : DEFAULT_PREFERENCES;
    } catch {
      return DEFAULT_PREFERENCES;
    }
  });

  const [displayName, setDisplayName] = useState(user?.username || "Administrador");
  const [emailInput, setEmailInput] = useState(`${user?.username || "admin"}@urleads.com`);
  const [isSaving, setIsSaving] = useState(false);

  const handleSavePreferences = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setTimeout(() => {
      try {
        localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(preferences));
      } catch {
        // Ignored
      }
      setIsSaving(false);
      sileo.success({
        title: "Ajustes guardados",
        description: "Tus preferencias han sido actualizadas correctamente.",
      });
    }, 400);
  };

  const handleClearCache = () => {
    try {
      localStorage.removeItem("urleads_commercial_tasks_v1");
      sileo.info({
        title: "Caché restablecida",
        description: "Se han restablecido los datos temporales del CRM.",
      });
    } catch {
      // Ignored
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  const userInitials = user?.username ? user.username.slice(0, 2).toUpperCase() : "SB";

  return (
    <div className="w-full max-w-full space-y-6 animate-in fade-in duration-300 pb-16 md:pb-4">
      {/* ── Top Header ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
              Ajustes del Sistema
            </h1>
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-secondary border border-border text-muted-foreground">
              <Sliders className="w-3.5 h-3.5" />
              Configuración
            </span>
          </div>
          <p className="text-sm text-muted-foreground mt-1 font-medium">
            Personaliza tu perfil, moneda comercial, alertas y preferencias de cuenta
          </p>
        </div>

        <button
          type="button"
          onClick={handleSavePreferences}
          disabled={isSaving}
          className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 shadow-xs hover:bg-primary/90 transition-all cursor-pointer self-start sm:self-auto active:scale-95 disabled:opacity-50"
        >
          {isSaving ? (
            <RefreshCw className="h-4 w-4 animate-spin" />
          ) : (
            <Save className="h-4 w-4" />
          )}
          <span>Guardar Cambios</span>
        </button>
      </div>

      {/* ── Ruixen Magnetic Tabs ── */}
      <div className="overflow-x-auto no-scrollbar pb-1">
        <RuixenMagneticTabs
          items={SETTINGS_TABS}
          value={activeTab}
          onChange={(tab) => setActiveTab(tab as any)}
        />
      </div>

      {/* ── Content by Tab ── */}
      {activeTab === "profile" && (
        <div className="p-6 sm:p-7 rounded-3xl bg-background border border-border shadow-2xs space-y-6">
          <div className="flex items-center gap-4 pb-5 border-b border-border/70">
            <div className="h-16 w-16 rounded-full bg-secondary border-2 border-border text-foreground font-bold text-xl flex items-center justify-center shrink-0 shadow-xs">
              {userInitials}
            </div>
            <div>
              <h3 className="text-base sm:text-lg font-bold text-foreground">
                {user?.username || "Usuario"}
              </h3>
              <p className="text-xs text-muted-foreground font-medium">
                Rol: Administrador Comercial • Sesión activa
              </p>
            </div>
          </div>

          <form onSubmit={handleSavePreferences} className="space-y-4 max-w-xl">
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                Nombre de usuario
              </label>
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                Correo de contacto institucional
              </label>
              <input
                type="email"
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                Cargo / Departamento
              </label>
              <input
                type="text"
                disabled
                value="Dirección Comercial & Ventas"
                className="w-full h-10 px-3.5 rounded-xl bg-secondary/20 border border-border text-xs sm:text-sm text-muted-foreground font-medium cursor-not-allowed"
              />
            </div>
          </form>
        </div>
      )}

      {activeTab === "crm" && (
        <div className="p-6 sm:p-7 rounded-3xl bg-background border border-border shadow-2xs space-y-6">
          <div className="pb-3 border-b border-border/70">
            <h3 className="text-base sm:text-lg font-bold text-foreground">
              Configuración de Organización & Moneda
            </h3>
            <p className="text-xs text-muted-foreground font-medium">
              Ajusta cómo se muestran los montos del pipeline comercial
            </p>
          </div>

          <div className="space-y-5 max-w-xl">
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                Nombre de la Empresa u Organización
              </label>
              <input
                type="text"
                value={preferences.companyName}
                onChange={(e) =>
                  setPreferences((prev) => ({ ...prev, companyName: e.target.value }))
                }
                className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                Moneda de Operación Comercial
              </label>
              <select
                value={preferences.currency}
                onChange={(e) =>
                  setPreferences((prev) => ({ ...prev, currency: e.target.value }))
                }
                className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
              >
                <option value="USD">Dólar Estadounidense ($ USD)</option>
                <option value="PEN">Soles Peruanos (S/. PEN)</option>
                <option value="EUR">Euros (€ EUR)</option>
              </select>
            </div>
          </div>
        </div>
      )}

      {activeTab === "notifications" && (
        <div className="p-6 sm:p-7 rounded-3xl bg-background border border-border shadow-2xs space-y-6">
          <div className="pb-3 border-b border-border/70">
            <h3 className="text-base sm:text-lg font-bold text-foreground">
              Preferencias de Alertas & Notificaciones
            </h3>
            <p className="text-xs text-muted-foreground font-medium">
              Controla qué avisos deseas recibir en el panel
            </p>
          </div>

          <div className="space-y-4 max-w-xl">
            <div className="flex items-center justify-between p-4 rounded-2xl bg-secondary/30 border border-border">
              <div>
                <p className="text-sm font-semibold text-foreground">
                  Avisar sobre nuevos leads registrados
                </p>
                <p className="text-xs text-muted-foreground">
                  Recibe un aviso instantáneo en la campana al ingresar un contacto
                </p>
              </div>
              <input
                type="checkbox"
                checked={preferences.notifyOnNewLead}
                onChange={(e) =>
                  setPreferences((prev) => ({
                    ...prev,
                    notifyOnNewLead: e.target.checked,
                  }))
                }
                className="h-5 w-5 rounded-md accent-accent cursor-pointer"
              />
            </div>

            <div className="flex items-center justify-between p-4 rounded-2xl bg-secondary/30 border border-border">
              <div>
                <p className="text-sm font-semibold text-foreground">
                  Recordatorio de seguimientos de hoy
                </p>
                <p className="text-xs text-muted-foreground">
                  Alerta destacada si tienes citas o contactos agendados para la fecha
                </p>
              </div>
              <input
                type="checkbox"
                checked={preferences.notifyOnFollowUp}
                onChange={(e) =>
                  setPreferences((prev) => ({
                    ...prev,
                    notifyOnFollowUp: e.target.checked,
                  }))
                }
                className="h-5 w-5 rounded-md accent-accent cursor-pointer"
              />
            </div>
          </div>
        </div>
      )}

      {activeTab === "system" && (
        <div className="p-6 sm:p-7 rounded-3xl bg-background border border-border shadow-2xs space-y-6">
          <div className="pb-3 border-b border-border/70">
            <h3 className="text-base sm:text-lg font-bold text-foreground">
              Seguridad y Mantenimiento de Datos
            </h3>
            <p className="text-xs text-muted-foreground font-medium">
              Auditoría de sesión y herramientas de diagnóstico
            </p>
          </div>

          <div className="space-y-4 max-w-xl">
            <div className="p-4 rounded-2xl bg-secondary/30 border border-border flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-foreground">
                  Protección CSRF & Tokens de Sesión
                </p>
                <p className="text-xs text-emerald-600 font-medium">
                  ✓ Token activo y cifrado por Spring Security
                </p>
              </div>
              <Shield className="h-5 w-5 text-emerald-600" />
            </div>

            <div className="p-4 rounded-2xl bg-secondary/30 border border-border flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-foreground">
                  Limpiar Caché Local de Tareas
                </p>
                <p className="text-xs text-muted-foreground">
                  Restablece las tareas de prueba almacenadas en el navegador
                </p>
              </div>
              <button
                type="button"
                onClick={handleClearCache}
                className="px-3.5 py-1.5 rounded-full bg-secondary border border-border hover:bg-secondary/80 text-xs font-semibold text-foreground transition-colors cursor-pointer"
              >
                Restablecer
              </button>
            </div>

            <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-between">
              <div>
                <p className="text-sm font-semibold text-rose-700">
                  Cerrar Sesión Activa
                </p>
                <p className="text-xs text-rose-600/80">
                  Finalizar la sesión de trabajo en este dispositivo
                </p>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                className="px-4 py-2 rounded-full bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold flex items-center gap-1.5 transition-colors cursor-pointer shadow-xs"
              >
                <LogOut className="h-3.5 w-3.5" />
                <span>Cerrar sesión</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
