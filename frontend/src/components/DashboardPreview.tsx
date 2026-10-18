import React from "react";
import { motion } from "framer-motion";
import {
  ChevronDown,
  Search,
  Bell,
  Home,
  CheckSquare,
  Users,
  BarChart2,
  Calendar,
  Settings,
  Plus,
  MoreVertical,
  Check,
  Send,
  Phone,
  Mail,
  FileText,
  Activity,
} from "lucide-react";
import { UrLeadsLogo } from "./UrLeadsLogo";

export const DashboardPreview: React.FC = () => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8, delay: 0.5, ease: [0.16, 1, 0.3, 1] }}
      className="mt-4 w-full max-w-5xl shrink-0"
    >
      {/* Frosted Glass Outer Container */}
      <div
        className="rounded-2xl overflow-hidden p-3 md:p-4 backdrop-blur-md"
        style={{
          background: "rgba(255, 255, 255, 0.4)",
          border: "1px solid rgba(255, 255, 255, 0.5)",
          boxShadow: "var(--shadow-dashboard)",
        }}
      >
        {/* Inner Dashboard Window */}
        <div className="bg-background rounded-xl border border-border/80 shadow-xs overflow-hidden text-[11px] select-none pointer-events-none">
          {/* Top Bar */}
          <div className="h-11 px-3.5 border-b border-border bg-background flex items-center justify-between gap-4">
            {/* Left: Logo & Org Switcher */}
            <div className="flex items-center gap-1.5">
              <UrLeadsLogo size={20} showText />
              <ChevronDown className="h-3 w-3 text-muted-foreground ml-0.5" />
            </div>

            {/* Middle: Search bar with ⌘K */}
            <div className="flex-1 max-w-sm">
              <div className="h-7 px-2.5 rounded-md bg-secondary/60 border border-border/60 flex items-center justify-between text-muted-foreground">
                <div className="flex items-center gap-2">
                  <Search className="h-3 w-3" />
                  <span className="text-[10px]">Buscar prospectos, notas...</span>
                </div>
                <kbd className="px-1.5 py-0.5 text-[9px] font-medium bg-background border border-border/80 rounded text-muted-foreground">
                  ⌘K
                </kbd>
              </div>
            </div>

            {/* Right: Actions & Profile */}
            <div className="flex items-center gap-2.5">
              <div className="h-6 px-2.5 rounded-md bg-primary text-primary-foreground font-medium text-[10px] flex items-center gap-1 shadow-xs">
                <Plus className="h-2.5 w-2.5" />
                <span>Nuevo Lead</span>
              </div>
              <div className="h-6 w-6 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground">
                <Bell className="h-3.5 w-3.5" />
              </div>
              <div className="h-6 w-6 rounded-full bg-secondary border border-border text-foreground font-semibold text-[10px] flex items-center justify-center">
                SB
              </div>
            </div>
          </div>

          {/* App Body: Sidebar + Main Content */}
          <div className="flex min-h-[380px]">
            {/* Sidebar (w-40) */}
            <aside className="w-40 border-r border-border bg-background p-3 flex flex-col justify-between shrink-0">
              <div className="space-y-4">
                {/* Main Navigation */}
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2 px-2 py-1.5 rounded-md bg-secondary text-foreground font-medium">
                    <Home className="h-3.5 w-3.5 text-accent" />
                    <span>Resumen</span>
                  </div>
                  <div className="flex items-center justify-between px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <div className="flex items-center gap-2">
                      <Users className="h-3.5 w-3.5" />
                      <span>Leads</span>
                    </div>
                    <span className="px-1.5 py-0.2 rounded-full text-[9px] font-semibold bg-accent/15 text-accent">
                      128
                    </span>
                  </div>
                  <div className="flex items-center justify-between px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <div className="flex items-center gap-2">
                      <Calendar className="h-3.5 w-3.5" />
                      <span>Seguimientos</span>
                    </div>
                    <span className="px-1.5 py-0.2 rounded-full text-[9px] font-semibold bg-amber-500/15 text-amber-600">
                      5
                    </span>
                  </div>
                  <div className="flex items-center justify-between px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <div className="flex items-center gap-2">
                      <BarChart2 className="h-3.5 w-3.5" />
                      <span>Reportes</span>
                    </div>
                    <ChevronDown className="h-3 w-3" />
                  </div>
                  <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <Activity className="h-3.5 w-3.5" />
                    <span>Pipeline</span>
                  </div>
                </div>

                {/* Section "Operaciones" */}
                <div>
                  <div className="px-2 text-[9px] font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                    Operaciones
                  </div>
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                      <CheckSquare className="h-3.5 w-3.5" />
                      <span>Tareas</span>
                    </div>
                    <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                      <Bell className="h-3.5 w-3.5" />
                      <span>Notificaciones</span>
                    </div>
                    <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                      <Settings className="h-3.5 w-3.5" />
                      <span>Ajustes</span>
                    </div>
                  </div>
                </div>
              </div>
            </aside>

            {/* Main Content Area */}
            <main className="flex-1 bg-secondary/30 p-4 space-y-4 overflow-hidden">
              {/* Top Greeting */}
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-semibold text-foreground tracking-tight">
                    Hola, Sebas
                  </h2>
                  <p className="text-[10px] text-muted-foreground">
                    Tu resumen comercial, pipeline y próximos seguimientos
                  </p>
                </div>
              </div>

              {/* Action Buttons Row */}
              <div className="flex items-center gap-1.5 flex-wrap">
                <button className="h-6 px-3 rounded-full bg-accent text-accent-foreground font-medium text-[10px] flex items-center gap-1 shadow-xs">
                  <Plus className="h-2.5 w-2.5" />
                  <span>Nuevo Lead</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <Phone className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Llamada</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <Mail className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Email</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <Calendar className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Cita</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <FileText className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Nota</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <CheckSquare className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Tarea</span>
                </button>
                <span className="text-[10px] text-muted-foreground hover:text-foreground ml-2 font-medium">
                  + Personalizar
                </span>
              </div>

              {/* Two Equal-Width Cards */}
              <div className="flex gap-3">
                {/* Pipeline Value Card */}
                <div className="flex-1 basis-0 bg-background rounded-lg border border-border p-3.5 flex flex-col justify-between shadow-2xs">
                  <div>
                    <div className="flex items-center justify-between text-muted-foreground mb-1.5">
                      <div className="flex items-center gap-1.5">
                        <div className="h-3.5 w-3.5 rounded-full bg-emerald-50 text-emerald-600 border border-emerald-200 flex items-center justify-center">
                          <Check className="h-2 w-2 stroke-[3]" />
                        </div>
                        <span className="font-medium text-foreground text-[11px]">
                          Pipeline Comercial
                        </span>
                      </div>
                      <span className="text-[10px]">Activo</span>
                    </div>

                    <div className="mt-1">
                      <span className="text-xl font-semibold tracking-tight text-foreground font-body">
                        $124,500
                      </span>
                      <span className="text-xs text-muted-foreground font-medium">
                        .00
                      </span>
                    </div>

                    {/* Stats */}
                    <div className="flex items-center gap-2 mt-1 text-[10px]">
                      <span className="text-muted-foreground">Últimos 30 Días</span>
                      <span className="text-emerald-600 font-semibold">
                        +$18.5K
                      </span>
                      <span className="text-rose-600 font-semibold">
                        -3 Perdidos
                      </span>
                    </div>
                  </div>

                  {/* SVG Area Chart with smooth cubic Bézier curve */}
                  <div className="mt-2.5 w-full h-20 relative overflow-hidden">
                    <svg
                      viewBox="0 0 360 80"
                      preserveAspectRatio="none"
                      className="w-full h-20"
                    >
                      <defs>
                        <linearGradient
                          id="dashboardBalanceGradient"
                          x1="0"
                          y1="0"
                          x2="0"
                          y2="1"
                        >
                          <stop
                            offset="0%"
                            stopColor="hsl(var(--accent))"
                            stopOpacity="0.15"
                          />
                          <stop
                            offset="100%"
                            stopColor="hsl(var(--accent))"
                            stopOpacity="0"
                          />
                        </linearGradient>
                      </defs>

                      {/* Area Fill */}
                      <path
                        d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14 L 360 80 L 0 80 Z"
                        fill="url(#dashboardBalanceGradient)"
                      />

                      {/* Area Stroke */}
                      <path
                        d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14"
                        fill="none"
                        stroke="hsl(var(--accent))"
                        strokeWidth="1.5"
                        strokeLinecap="round"
                      />
                    </svg>
                  </div>
                </div>

                {/* Stages Card */}
                <div className="flex-1 basis-0 bg-background rounded-lg border border-border p-3.5 flex flex-col shadow-2xs">
                  {/* Header */}
                  <div className="flex items-center justify-between pb-1.5 border-b border-border/50">
                    <span className="font-semibold text-foreground text-xs">
                      Distribución por Etapa
                    </span>
                    <div className="flex items-center gap-1 text-muted-foreground">
                      <div className="p-0.5 hover:text-foreground">
                        <Plus className="h-3 w-3" />
                      </div>
                      <div className="p-0.5 hover:text-foreground">
                        <MoreVertical className="h-3 w-3" />
                      </div>
                    </div>
                  </div>

                  {/* 3 Rows (py-3, no dividers, text-xs, justify-between) */}
                  <div className="flex-1 flex flex-col justify-around py-1">
                    <div className="flex items-center justify-between py-3 text-xs">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-2 rounded-full bg-sky-400" />
                        <span className="text-foreground font-medium">Nuevos (Por calificar)</span>
                      </div>
                      <span className="font-semibold text-foreground font-body">
                        32 Leads
                      </span>
                    </div>

                    <div className="flex items-center justify-between py-3 text-xs">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-2 rounded-full bg-amber-400" />
                        <span className="text-foreground font-medium">En Seguimiento</span>
                      </div>
                      <span className="font-semibold text-foreground font-body">
                        68 Leads
                      </span>
                    </div>

                    <div className="flex items-center justify-between py-3 text-xs">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-2 rounded-full bg-emerald-500" />
                        <span className="text-foreground font-medium">Cerrados (Concretados)</span>
                      </div>
                      <span className="font-semibold text-foreground font-body">
                        28 Leads
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Recent Leads Table */}
              <div className="bg-background rounded-lg border border-border p-3 shadow-2xs">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-xs font-semibold text-foreground">
                    Leads Recientes
                  </h3>
                  <span className="text-[10px] text-muted-foreground hover:text-foreground font-medium">
                    Ver todos
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left">
                    <thead>
                      <tr className="border-b border-border/60 text-[10px] text-muted-foreground uppercase font-medium">
                        <th className="pb-1.5 font-medium">Fecha</th>
                        <th className="pb-1.5 font-medium">Prospecto</th>
                        <th className="pb-1.5 font-medium text-right">Servicio / Empresa</th>
                        <th className="pb-1.5 font-medium text-right">Etapa</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40 text-[11px]">
                      {/* Row 1: Carlos Torres - En seguimiento (amber) */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 26</td>
                        <td className="py-2 font-medium text-foreground">Carlos Torres</td>
                        <td className="py-2 text-right text-muted-foreground">
                          Consultoría IT
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-amber-500/10 text-amber-700 border border-amber-500/20">
                            En seguimiento
                          </span>
                        </td>
                      </tr>

                      {/* Row 2: Tech Solutions - Cerrado (emerald) */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 25</td>
                        <td className="py-2 font-medium text-foreground">
                          Tech Solutions S.A.
                        </td>
                        <td className="py-2 text-right text-muted-foreground">
                          Plan Enterprise
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                            Cerrado
                          </span>
                        </td>
                      </tr>

                      {/* Row 3: María López - Cerrado (emerald) */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 24</td>
                        <td className="py-2 font-medium text-foreground">María López</td>
                        <td className="py-2 text-right text-muted-foreground">
                          Marketing Digital
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                            Cerrado
                          </span>
                        </td>
                      </tr>

                      {/* Row 4: Distribuidora Sur - Nuevo (sky) */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 23</td>
                        <td className="py-2 font-medium text-foreground">
                          Distribuidora Sur
                        </td>
                        <td className="py-2 text-right text-muted-foreground">
                          Logística & Soporte
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-sky-500/10 text-sky-700 border border-sky-500/20">
                            Nuevo
                          </span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </main>
          </div>
        </div>
      </div>
    </motion.div>
  );
};
