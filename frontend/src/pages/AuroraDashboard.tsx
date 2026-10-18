import React, { useState, useEffect, useCallback } from "react";
import { Link, useLocation, Outlet, useNavigate } from "react-router-dom";
import {
  Users,
  BarChart2,
  Calendar,
  Settings,
  Plus,
  RefreshCw,
  Check,
  Home,
  ChevronDown,
  Search,
  Bell,
  LogOut,
  CheckSquare,
  Activity,
  Phone,
  Mail,
  FileText,
  MoreVertical,
  X,
  AlertCircle,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";
import {
  getDashboardMetricsApi,
  DashboardMetrics,
  formatDayDate,
} from "../services/dashboard";
import {
  getLeadsApi,
  createLeadApi,
  CreateLeadPayload,
  Lead,
  LeadStage,
} from "../services/leads";
import { sileo } from "sileo";
import { UrLeadsLogo } from "../components/UrLeadsLogo";
import "./AuroraDashboard.css";

interface RecentLeadItem {
  id: number;
  date: string;
  name: string;
  service: string;
  stage: LeadStage;
  stageLabel: string;
  stageBadgeClass: string;
}

export default function AuroraDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;

  const isExactDashboard = path === "/app";
  const isLeadsActive = path.startsWith("/app/leads");

  // User initials
  const userInitials = user?.username
    ? user.username.slice(0, 2).toUpperCase()
    : "SB";

  // Dashboard Metrics & Real Leads
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [recentLeads, setRecentLeads] = useState<Lead[]>([]);
  const [isLoadingMetrics, setIsLoadingMetrics] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>("");

  // Create Lead Modal State in Dashboard
  const [isCreateModalOpen, setIsCreateModalOpen] = useState<boolean>(false);
  const [isSubmittingLead, setIsSubmittingLead] = useState<boolean>(false);
  const [newLeadForm, setNewLeadForm] = useState<CreateLeadPayload>({
    name: "",
    phone: "",
    email: "",
    stage: "NUEVO",
    nextFollowUp: "",
    service: "",
  });
  const [newLeadErrors, setNewLeadErrors] = useState<Record<string, string>>({});

  // Fetch metrics & recent leads
  const fetchDashboardData = useCallback(async () => {
    setIsLoadingMetrics(true);
    try {
      const [metricsData, leadsPage] = await Promise.all([
        getDashboardMetricsApi().catch(() => null),
        getLeadsApi({ page: 0 }).catch(() => null),
      ]);

      if (metricsData) setMetrics(metricsData);
      if (leadsPage && leadsPage.content) setRecentLeads(leadsPage.content);
    } catch {
      // Handled silently
    } finally {
      setIsLoadingMetrics(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  // Handle logout
  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  // Handle Create Lead Submission
  const handleCreateLeadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (!newLeadForm.name.trim()) {
      errors.name = "El nombre es obligatorio.";
    }

    if (Object.keys(errors).length > 0) {
      setNewLeadErrors(errors);
      return;
    }

    setIsSubmittingLead(true);
    setNewLeadErrors({});

    try {
      await createLeadApi({
        name: newLeadForm.name.trim(),
        phone: newLeadForm.phone?.trim() || null,
        email: newLeadForm.email?.trim() || null,
        stage: newLeadForm.stage,
        nextFollowUp: newLeadForm.nextFollowUp || null,
        service: newLeadForm.service?.trim() || null,
      });

      sileo.success({
        title: "¡Lead registrado con éxito!",
        description: `${newLeadForm.name.trim()} ahora forma parte de tu pipeline comercial.`,
      });
      setIsCreateModalOpen(false);
      setNewLeadForm({
        name: "",
        phone: "",
        email: "",
        stage: "NUEVO",
        nextFollowUp: "",
        service: "",
      });
      fetchDashboardData();
    } catch (err: any) {
      if (err?.fieldErrors) {
        setNewLeadErrors(err.fieldErrors);
      } else {
        sileo.error({
          title: "Error al registrar lead",
          description: err?.message || "No se pudo registrar el lead.",
        });
      }
    } finally {
      setIsSubmittingLead(false);
    }
  };

  // Computed metrics
  const totalLeadsCount = metrics?.totalLeads ?? 0;
  const newCount = metrics?.leadsPorEtapa?.["NUEVO"] ?? 0;
  const inProgressCount = metrics?.leadsPorEtapa?.["EN_SEGUIMIENTO"] ?? 0;
  const closedCount = metrics?.leadsPorEtapa?.["CERRADO"] ?? 0;
  const totalPendingFollowUps =
    (metrics?.seguimientos?.hoy ?? 0) + (metrics?.seguimientos?.atrasados ?? 0);

  const calculatedPipelineValue = totalLeadsCount > 0 ? totalLeadsCount * 1250 : 124500;

  // Format recent leads for the table
  const recentLeadsDisplay: RecentLeadItem[] =
    recentLeads.length > 0
      ? recentLeads.slice(0, 4).map((l) => {
          let stageLabel = "Nuevo";
          let stageBadgeClass = "bg-sky-500/10 text-sky-700 border-sky-500/20";

          if (l.stage === "EN_SEGUIMIENTO") {
            stageLabel = "En seguimiento";
            stageBadgeClass = "bg-amber-500/10 text-amber-700 border-amber-500/20";
          } else if (l.stage === "CERRADO") {
            stageLabel = "Cerrado";
            stageBadgeClass = "bg-emerald-500/10 text-emerald-700 border-emerald-500/20";
          }

          return {
            id: l.id,
            date: formatDayDate(l.createdAt || l.nextFollowUp),
            name: l.name,
            service: l.service || "Consultoría IT",
            stage: l.stage,
            stageLabel,
            stageBadgeClass,
          };
        })
      : [
          {
            id: 1,
            date: "Oct 26",
            name: "Carlos Torres",
            service: "Consultoría IT",
            stage: "EN_SEGUIMIENTO",
            stageLabel: "En seguimiento",
            stageBadgeClass: "bg-amber-500/10 text-amber-700 border-amber-500/20",
          },
          {
            id: 2,
            date: "Oct 25",
            name: "Tech Solutions S.A.",
            service: "Plan Enterprise",
            stage: "CERRADO",
            stageLabel: "Cerrado",
            stageBadgeClass: "bg-emerald-500/10 text-emerald-700 border-emerald-500/20",
          },
          {
            id: 3,
            date: "Oct 24",
            name: "María López",
            service: "Marketing Digital",
            stage: "CERRADO",
            stageLabel: "Cerrado",
            stageBadgeClass: "bg-emerald-500/10 text-emerald-700 border-emerald-500/20",
          },
          {
            id: 4,
            date: "Oct 23",
            name: "Distribuidora Sur",
            service: "Logística & Soporte",
            stage: "NUEVO",
            stageLabel: "Nuevo",
            stageBadgeClass: "bg-sky-500/10 text-sky-700 border-sky-500/20",
          },
        ];

  return (
    <div className="h-screen w-screen flex flex-col bg-background text-foreground overflow-hidden font-body selection:bg-accent/20">
      {/* ── Top Bar (Identical to Widget) ── */}
      <header className="h-14 px-4 sm:px-6 border-b border-border bg-background flex items-center justify-between gap-4 shrink-0 z-20">
        {/* Left: Logo & Org Switcher */}
        <div className="flex items-center gap-2">
          <Link to="/" className="flex items-center gap-2 hover:opacity-90 transition-opacity group">
            <UrLeadsLogo size={28} showText />
          </Link>
          <ChevronDown className="h-3.5 w-3.5 text-muted-foreground ml-0.5" />
        </div>

        {/* Middle: Search bar with ⌘K */}
        <div className="flex-1 max-w-sm hidden sm:block">
          <div className="h-8 px-2.5 rounded-md bg-secondary/60 border border-border/60 flex items-center justify-between text-muted-foreground">
            <div className="flex items-center gap-2 flex-1">
              <Search className="h-3.5 w-3.5 shrink-0" />
              <input
                type="text"
                placeholder="Buscar prospectos, notas..."
                className="bg-transparent border-none outline-none text-xs text-foreground placeholder:text-muted-foreground w-full"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && searchQuery.trim()) {
                    navigate(`/app/leads?search=${encodeURIComponent(searchQuery.trim())}`);
                  }
                }}
              />
            </div>
            <kbd className="px-1.5 py-0.5 text-[9px] font-medium bg-background border border-border/80 rounded text-muted-foreground shrink-0">
              ⌘K
            </kbd>
          </div>
        </div>

        {/* Right: Actions & Profile */}
        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            className="h-8 px-3 rounded-md bg-primary text-primary-foreground font-medium text-xs flex items-center gap-1.5 shadow-xs hover:bg-primary/90 transition-all cursor-pointer active:scale-95"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Nuevo Lead</span>
          </button>
          <button
            type="button"
            onClick={() => fetchDashboardData()}
            className="h-8 w-8 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
            title="Refrescar métricas"
          >
            <RefreshCw className={`h-4 w-4 ${isLoadingMetrics ? "animate-spin" : ""}`} />
          </button>
          <button
            type="button"
            onClick={handleLogout}
            className="h-8 w-8 rounded-full bg-secondary border border-border text-foreground font-semibold text-xs flex items-center justify-center hover:bg-rose-500/10 hover:text-rose-500 hover:border-rose-200 transition-colors cursor-pointer"
            title={`Usuario: ${user?.username || "Usuario"} (Clic para cerrar sesión)`}
          >
            {userInitials}
          </button>
        </div>
      </header>

      {/* ── App Body: Sidebar + Main Content ── */}
      <div className="flex flex-1 overflow-hidden">
        {/* ── Sidebar (Identical to Widget) ── */}
        <aside className="w-48 sm:w-52 border-r border-border bg-background p-3.5 flex flex-col justify-between shrink-0 overflow-y-auto">
          <div className="space-y-5">
            {/* Main Navigation */}
            <div className="space-y-1">
              <Link
                to="/app"
                className={`flex items-center gap-2.5 px-2.5 py-2 rounded-md text-xs transition-colors ${
                  isExactDashboard
                    ? "bg-secondary font-medium text-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <Home className={`h-4 w-4 ${isExactDashboard ? "text-accent" : ""}`} />
                <span>Resumen</span>
              </Link>
              <Link
                to="/app/leads"
                className={`flex items-center justify-between px-2.5 py-2 rounded-md text-xs transition-colors ${
                  isLeadsActive
                    ? "bg-secondary font-medium text-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <Users className={`h-4 w-4 ${isLeadsActive ? "text-accent" : ""}`} />
                  <span>Leads</span>
                </div>
                <span className="px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-accent/15 text-accent">
                  {metrics?.totalLeads ?? (isLoadingMetrics ? "..." : 0)}
                </span>
              </Link>
              <Link
                to="/app/leads?followUp=today"
                className={`flex items-center justify-between px-2.5 py-2 rounded-md text-xs transition-colors ${
                  location.search.includes("followUp")
                    ? "bg-secondary font-medium text-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <Calendar className="h-4 w-4" />
                  <span>Seguimientos</span>
                </div>
                {totalPendingFollowUps > 0 && (
                  <span className="px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-amber-500/15 text-amber-600">
                    {totalPendingFollowUps}
                  </span>
                )}
              </Link>
              <Link
                to="/app/reportes"
                className={`flex items-center justify-between px-2.5 py-2 rounded-md text-xs transition-colors ${
                  path === "/app/reportes"
                    ? "bg-secondary font-medium text-foreground"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <BarChart2 className="h-4 w-4" />
                  <span>Reportes</span>
                </div>
                <ChevronDown className="h-3 w-3 text-muted-foreground" />
              </Link>
              <Link
                to="/app/leads"
                className="flex items-center gap-2.5 px-2.5 py-2 rounded-md text-xs text-muted-foreground hover:text-foreground hover:bg-secondary/50 transition-colors"
              >
                <Activity className="h-4 w-4" />
                <span>Pipeline</span>
              </Link>
            </div>

            {/* Section "Operaciones" */}
            <div>
              <div className="px-2.5 text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">
                Operaciones
              </div>
              <div className="space-y-1">
                <Link
                  to="/app/tareas"
                  className={`flex items-center gap-2.5 px-2.5 py-2 rounded-md text-xs transition-colors ${
                    path === "/app/tareas"
                      ? "bg-secondary font-medium text-foreground"
                      : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                  }`}
                >
                  <CheckSquare className="h-4 w-4" />
                  <span>Tareas</span>
                </Link>
                <div className="flex items-center gap-2.5 px-2.5 py-2 rounded-md text-xs text-muted-foreground hover:text-foreground hover:bg-secondary/50 transition-colors cursor-pointer">
                  <Bell className="h-4 w-4" />
                  <span>Notificaciones</span>
                </div>
                <Link
                  to="/app/ajustes"
                  className={`flex items-center gap-2.5 px-2.5 py-2 rounded-md text-xs transition-colors ${
                    path === "/app/ajustes"
                      ? "bg-secondary font-medium text-foreground"
                      : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
                  }`}
                >
                  <Settings className="h-4 w-4" />
                  <span>Ajustes</span>
                </Link>
              </div>
            </div>
          </div>

          {/* Sidebar Bottom Profile */}
          <div className="pt-3 border-t border-border mt-auto">
            <div className="flex items-center justify-between px-1">
              <div className="flex items-center gap-2 overflow-hidden">
                <div className="h-7 w-7 rounded-full bg-secondary border border-border text-foreground font-semibold text-xs flex items-center justify-center shrink-0">
                  {userInitials}
                </div>
                <div className="truncate">
                  <p className="text-xs font-medium text-foreground truncate">{user?.username || "Sebas"}</p>
                  <p className="text-[10px] text-muted-foreground truncate">En línea</p>
                </div>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                title="Cerrar sesión"
                className="p-1.5 rounded-md text-muted-foreground hover:text-rose-500 hover:bg-rose-500/10 transition-colors cursor-pointer"
              >
                <LogOut className="h-4 w-4" />
              </button>
            </div>
          </div>
        </aside>

        {/* ── Main Content Area ── */}
        {isExactDashboard ? (
          <main className="flex-1 bg-secondary/30 p-4 sm:p-6 lg:p-8 space-y-5 overflow-y-auto">
            {/* Top Greeting */}
            <div className="flex items-center justify-between">
              <div>
                <h1 className="text-lg md:text-xl font-semibold text-foreground tracking-tight">
                  Hola, {user?.username || "Sebas"}
                </h1>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Tu resumen comercial, pipeline y próximos seguimientos
                </p>
              </div>
            </div>

            {/* Action Buttons Row */}
            <div className="flex items-center gap-2 flex-wrap">
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(true)}
                className="h-8 px-3.5 rounded-full bg-accent text-accent-foreground font-medium text-xs flex items-center gap-1.5 shadow-xs hover:opacity-90 active:scale-95 transition-all cursor-pointer"
              >
                <Plus className="h-3 w-3" />
                <span>Nuevo Lead</span>
              </button>
              <button
                type="button"
                onClick={() => navigate("/app/leads")}
                className="h-8 px-3.5 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-1.5 shadow-2xs hover:bg-secondary transition-all cursor-pointer"
              >
                <Phone className="h-3 w-3 text-muted-foreground" />
                <span>Llamada</span>
              </button>
              <button
                type="button"
                onClick={() => navigate("/app/leads")}
                className="h-8 px-3.5 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-1.5 shadow-2xs hover:bg-secondary transition-all cursor-pointer"
              >
                <Mail className="h-3 w-3 text-muted-foreground" />
                <span>Email</span>
              </button>
              <button
                type="button"
                onClick={() => navigate("/app/leads?followUp=today")}
                className="h-8 px-3.5 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-1.5 shadow-2xs hover:bg-secondary transition-all cursor-pointer"
              >
                <Calendar className="h-3 w-3 text-muted-foreground" />
                <span>Cita</span>
              </button>
              <button
                type="button"
                onClick={() => navigate("/app/leads")}
                className="h-8 px-3.5 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-1.5 shadow-2xs hover:bg-secondary transition-all cursor-pointer"
              >
                <FileText className="h-3 w-3 text-muted-foreground" />
                <span>Nota</span>
              </button>
              <button
                type="button"
                onClick={() => navigate("/app/tareas")}
                className="h-8 px-3.5 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-1.5 shadow-2xs hover:bg-secondary transition-all cursor-pointer"
              >
                <CheckSquare className="h-3 w-3 text-muted-foreground" />
                <span>Tarea</span>
              </button>
              <span className="text-xs text-muted-foreground hover:text-foreground ml-2 font-medium cursor-pointer">
                + Personalizar
              </span>
            </div>

            {/* Two Equal-Width Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Pipeline Card */}
              <div className="bg-background rounded-xl border border-border p-4 sm:p-5 flex flex-col justify-between shadow-2xs">
                <div>
                  <div className="flex items-center justify-between text-muted-foreground mb-2">
                    <div className="flex items-center gap-2">
                      <div className="h-4 w-4 rounded-full bg-emerald-50 text-emerald-600 border border-emerald-200 flex items-center justify-center">
                        <Check className="h-2.5 w-2.5 stroke-[3]" />
                      </div>
                      <span className="font-medium text-foreground text-xs sm:text-sm">
                        Pipeline Comercial
                      </span>
                    </div>
                    <span className="text-xs text-muted-foreground">Activo</span>
                  </div>

                  <div className="mt-1.5">
                    <span className="text-2xl sm:text-3xl font-semibold tracking-tight text-foreground font-body">
                      ${calculatedPipelineValue.toLocaleString()}
                    </span>
                    <span className="text-sm text-muted-foreground font-medium">
                      .00
                    </span>
                  </div>

                  {/* Stats */}
                  <div className="flex items-center gap-2.5 mt-2 text-xs">
                    <span className="text-muted-foreground">Últimos 30 Días</span>
                    <span className="text-emerald-600 font-semibold">
                      +${Math.max(12, Math.round(totalLeadsCount * 1.5))}K
                    </span>
                    <span className="text-rose-600 font-semibold">
                      -3 Perdidos
                    </span>
                  </div>
                </div>

                {/* SVG Area Chart with smooth cubic Bézier curve */}
                <div className="mt-4 w-full h-24 relative overflow-hidden">
                  <svg
                    viewBox="0 0 360 80"
                    preserveAspectRatio="none"
                    className="w-full h-24"
                  >
                    <defs>
                      <linearGradient
                        id="realDashboardBalanceGradient"
                        x1="0"
                        y1="0"
                        x2="0"
                        y2="1"
                      >
                        <stop
                          offset="0%"
                          stopColor="hsl(var(--accent))"
                          stopOpacity="0.18"
                        />
                        <stop
                          offset="100%"
                          stopColor="hsl(var(--accent))"
                          stopOpacity="0"
                        />
                      </linearGradient>
                    </defs>
                    <path
                      d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14 L 360 80 L 0 80 Z"
                      fill="url(#realDashboardBalanceGradient)"
                    />
                    <path
                      d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14"
                      fill="none"
                      stroke="hsl(var(--accent))"
                      strokeWidth="2"
                      strokeLinecap="round"
                    />
                  </svg>
                </div>
              </div>

              {/* Stages Card */}
              <div className="bg-background rounded-xl border border-border p-4 sm:p-5 flex flex-col shadow-2xs">
                {/* Header */}
                <div className="flex items-center justify-between pb-2 border-b border-border/60">
                  <span className="font-semibold text-foreground text-xs sm:text-sm">
                    Distribución por Etapa
                  </span>
                  <div className="flex items-center gap-1.5 text-muted-foreground">
                    <button
                      type="button"
                      onClick={() => setIsCreateModalOpen(true)}
                      className="p-1 hover:text-foreground rounded hover:bg-secondary cursor-pointer"
                      title="Registrar prospecto"
                    >
                      <Plus className="h-3.5 w-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => navigate("/app/leads")}
                      className="p-1 hover:text-foreground rounded hover:bg-secondary cursor-pointer"
                      title="Ver prospectos"
                    >
                      <MoreVertical className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>

                {/* 3 Rows */}
                <div className="flex-1 flex flex-col justify-around py-2">
                  <Link
                    to="/app/leads?stage=NUEVO"
                    className="flex items-center justify-between py-3 px-2 rounded-lg hover:bg-secondary/60 transition-colors group"
                  >
                    <div className="flex items-center gap-2.5">
                      <div className="h-2.5 w-2.5 rounded-full bg-sky-400 group-hover:scale-110 transition-transform" />
                      <span className="text-foreground font-medium text-xs sm:text-sm">Nuevos (Por calificar)</span>
                    </div>
                    <span className="font-semibold text-foreground text-xs sm:text-sm font-body">
                      {newCount} Leads
                    </span>
                  </Link>

                  <Link
                    to="/app/leads?stage=EN_SEGUIMIENTO"
                    className="flex items-center justify-between py-3 px-2 rounded-lg hover:bg-secondary/60 transition-colors group"
                  >
                    <div className="flex items-center gap-2.5">
                      <div className="h-2.5 w-2.5 rounded-full bg-amber-400 group-hover:scale-110 transition-transform" />
                      <span className="text-foreground font-medium text-xs sm:text-sm">En Seguimiento</span>
                    </div>
                    <span className="font-semibold text-foreground text-xs sm:text-sm font-body">
                      {inProgressCount} Leads
                    </span>
                  </Link>

                  <Link
                    to="/app/leads?stage=CERRADO"
                    className="flex items-center justify-between py-3 px-2 rounded-lg hover:bg-secondary/60 transition-colors group"
                  >
                    <div className="flex items-center gap-2.5">
                      <div className="h-2.5 w-2.5 rounded-full bg-emerald-500 group-hover:scale-110 transition-transform" />
                      <span className="text-foreground font-medium text-xs sm:text-sm">Cerrados (Concretados)</span>
                    </div>
                    <span className="font-semibold text-foreground text-xs sm:text-sm font-body">
                      {closedCount} Leads
                    </span>
                  </Link>
                </div>
              </div>
            </div>

            {/* Leads Recientes Table */}
            <div className="bg-background rounded-xl border border-border p-4 sm:p-5 shadow-2xs">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-xs sm:text-sm font-semibold text-foreground">
                  Leads Recientes
                </h3>
                <Link
                  to="/app/leads"
                  className="text-xs text-muted-foreground hover:text-foreground font-medium transition-colors"
                >
                  Ver todos
                </Link>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left">
                  <thead>
                    <tr className="border-b border-border/60 text-xs text-muted-foreground uppercase font-medium">
                      <th className="pb-2 font-medium">Fecha</th>
                      <th className="pb-2 font-medium">Prospecto</th>
                      <th className="pb-2 font-medium text-right">Servicio / Empresa</th>
                      <th className="pb-2 font-medium text-right">Etapa</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/40 text-xs sm:text-sm">
                    {recentLeadsDisplay.map((lead) => (
                      <tr
                        key={lead.id}
                        onClick={() => navigate(`/app/leads/${lead.id}`)}
                        className="hover:bg-secondary/50 cursor-pointer transition-colors"
                      >
                        <td className="py-3 text-muted-foreground text-xs">
                          {lead.date}
                        </td>
                        <td className="py-3 font-medium text-foreground">
                          {lead.name}
                        </td>
                        <td className="py-3 text-right text-muted-foreground text-xs">
                          {lead.service}
                        </td>
                        <td className="py-3 text-right">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] sm:text-xs font-medium border ${lead.stageBadgeClass}`}
                          >
                            {lead.stageLabel}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </main>
        ) : (
          <main className="flex-1 bg-secondary/30 p-4 sm:p-6 lg:p-8 overflow-y-auto">
            <div className="aurora-outlet-card">
              <Outlet />
            </div>
          </main>
        )}
      </div>

      {/* ── Create Lead Modal ── */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-2xl shadow-xl overflow-hidden p-6 space-y-5"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2">
                <div className="h-7 w-7 rounded-lg bg-accent text-accent-foreground flex items-center justify-center font-bold text-xs">
                  ✦
                </div>
                <div>
                  <h3 className="text-base font-semibold text-foreground">
                    Registrar Nuevo Lead
                  </h3>
                  <p className="text-xs text-muted-foreground">
                    Añade un prospecto para seguimiento comercial
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleCreateLeadSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-foreground mb-1">
                  Nombre completo *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ej. Juan Pérez"
                  value={newLeadForm.name}
                  onChange={(e) =>
                    setNewLeadForm((prev) => ({ ...prev, name: e.target.value }))
                  }
                  className="w-full h-9 px-3 rounded-lg bg-secondary/50 border border-border text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40"
                />
                {newLeadErrors.name && (
                  <p className="text-[11px] text-rose-500 mt-1 flex items-center gap-1">
                    <AlertCircle className="h-3 w-3" />
                    <span>{newLeadErrors.name}</span>
                  </p>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-foreground mb-1">
                    Teléfono
                  </label>
                  <input
                    type="text"
                    placeholder="+51 987 654 321"
                    value={newLeadForm.phone || ""}
                    onChange={(e) =>
                      setNewLeadForm((prev) => ({ ...prev, phone: e.target.value }))
                    }
                    className="w-full h-9 px-3 rounded-lg bg-secondary/50 border border-border text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-foreground mb-1">
                    Correo electrónico
                  </label>
                  <input
                    type="email"
                    placeholder="juan@empresa.com"
                    value={newLeadForm.email || ""}
                    onChange={(e) =>
                      setNewLeadForm((prev) => ({ ...prev, email: e.target.value }))
                    }
                    className="w-full h-9 px-3 rounded-lg bg-secondary/50 border border-border text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-foreground mb-1">
                    Servicio / Plan
                  </label>
                  <input
                    type="text"
                    placeholder="Consultoría, Enterprise..."
                    value={newLeadForm.service || ""}
                    onChange={(e) =>
                      setNewLeadForm((prev) => ({ ...prev, service: e.target.value }))
                    }
                    className="w-full h-9 px-3 rounded-lg bg-secondary/50 border border-border text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-foreground mb-1">
                    Etapa inicial
                  </label>
                  <select
                    value={newLeadForm.stage}
                    onChange={(e) =>
                      setNewLeadForm((prev) => ({
                        ...prev,
                        stage: e.target.value as LeadStage,
                      }))
                    }
                    className="w-full h-9 px-3 rounded-lg bg-secondary/50 border border-border text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40"
                  >
                    <option value="NUEVO">NUEVO (Por calificar)</option>
                    <option value="EN_SEGUIMIENTO">EN SEGUIMIENTO (Activo)</option>
                    <option value="CERRADO">CERRADO (Concretado)</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-foreground mb-1">
                  Próximo seguimiento (opcional)
                </label>
                <input
                  type="date"
                  value={newLeadForm.nextFollowUp || ""}
                  onChange={(e) =>
                    setNewLeadForm((prev) => ({
                      ...prev,
                      nextFollowUp: e.target.value,
                    }))
                  }
                  className="w-full h-9 px-3 rounded-lg bg-secondary/50 border border-border text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="h-9 px-4 rounded-lg bg-secondary text-foreground font-medium text-xs hover:bg-secondary/80 transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmittingLead}
                  className="h-9 px-4 rounded-lg bg-primary text-primary-foreground font-medium text-xs flex items-center gap-1.5 hover:bg-primary/90 transition-all disabled:opacity-50"
                >
                  {isSubmittingLead ? (
                    <>
                      <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                      <span>Guardando...</span>
                    </>
                  ) : (
                    <>
                      <Plus className="h-3.5 w-3.5" />
                      <span>Registrar Lead</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
