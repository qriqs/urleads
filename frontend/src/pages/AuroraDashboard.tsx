import React, { useState, useEffect, useCallback, useMemo } from "react";
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
  DollarSign,
  User,
  Menu,
  ChevronRight,
  TrendingUp,
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
  updateLeadApi,
  CreateLeadPayload,
  Lead,
  LeadStage,
} from "../services/leads";
import { createNoteApi } from "../services/notes";
import { sileo } from "sileo";
import { UrLeadsLogo } from "../components/UrLeadsLogo";
import { RuixenSearchCommand } from "../components/ruixen/RuixenSearchCommand";
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

  // Currency state: USD ($) or PEN (S/.)
  const [currency, setCurrency] = useState<"USD" | "PEN">("USD");

  // Mobile menu / drawer state
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState<boolean>(false);

  // Dashboard Metrics & Real Leads
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [recentLeads, setRecentLeads] = useState<Lead[]>([]);
  const [isLoadingMetrics, setIsLoadingMetrics] = useState<boolean>(true);

  // Modals state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState<boolean>(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState<boolean>(false);
  const [isNotificationModalOpen, setIsNotificationModalOpen] = useState<boolean>(false);
  const [isPipelineModalOpen, setIsPipelineModalOpen] = useState<boolean>(false);

  // Quick Action Modals
  const [isCallModalOpen, setIsCallModalOpen] = useState<boolean>(false);
  const [isEmailModalOpen, setIsEmailModalOpen] = useState<boolean>(false);
  const [isMeetingModalOpen, setIsMeetingModalOpen] = useState<boolean>(false);
  const [isNoteModalOpen, setIsNoteModalOpen] = useState<boolean>(false);
  const [isTaskModalOpen, setIsTaskModalOpen] = useState<boolean>(false);

  // Quick Action Form fields
  const [selectedLeadId, setSelectedLeadId] = useState<string>("");
  const [actionNotes, setActionNotes] = useState<string>("");
  const [callOutcome, setCallOutcome] = useState<string>("Contestó - Interesado");
  const [emailSubject, setEmailSubject] = useState<string>("Seguimiento Comercial UrLeads");
  const [meetingDate, setMeetingDate] = useState<string>(new Date().toISOString().split("T")[0]);
  const [taskTitle, setTaskTitle] = useState<string>("");
  const [taskPriority, setTaskPriority] = useState<"ALTA" | "MEDIA" | "BAJA">("MEDIA");
  const [isSubmittingAction, setIsSubmittingAction] = useState<boolean>(false);

  // Notifications read state
  const [hasDismissedNotifications, setHasDismissedNotifications] = useState<boolean>(false);

  // Create Lead Modal State in Dashboard
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

  // Close mobile menu on navigation
  useEffect(() => {
    setIsMobileMenuOpen(false);
  }, [location.pathname]);

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

  // Default selected lead
  useEffect(() => {
    if (recentLeads.length > 0 && !selectedLeadId) {
      setSelectedLeadId(String(recentLeads[0].id));
    }
  }, [recentLeads, selectedLeadId]);

  // Toggle currency handler ("botón de cambio")
  const handleToggleCurrency = () => {
    const next = currency === "USD" ? "PEN" : "USD";
    setCurrency(next);
    sileo.info({
      title: `Moneda cambiada a ${next === "PEN" ? "Soles (S/.)" : "Dólares ($)"}`,
      description: `El pipeline y montos ahora se expresan en ${next}.`,
    });
  };

  // Currency formatter
  const formatMoney = (usdAmount: number) => {
    if (currency === "PEN") {
      const pen = Math.round(usdAmount * 3.75);
      return `S/. ${pen.toLocaleString()}`;
    }
    return `$${usdAmount.toLocaleString()}`;
  };

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

  // Quick Action: Llamada
  const handleCallSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLeadId) {
      sileo.error({ title: "Selecciona un lead", description: "Debes seleccionar a qué prospecto llamaste." });
      return;
    }
    setIsSubmittingAction(true);
    try {
      const content = `📞 Llamada comercial: [${callOutcome}] ${actionNotes ? `- ${actionNotes.trim()}` : ""}`;
      await createNoteApi(selectedLeadId, content);
      sileo.success({
        title: "Llamada registrada",
        description: "Se guardó la bitácora de la llamada en las notas del lead.",
      });
      setIsCallModalOpen(false);
      setActionNotes("");
      fetchDashboardData();
    } catch (err: any) {
      sileo.error({ title: "Error", description: err?.message || "No se pudo registrar la llamada." });
    } finally {
      setIsSubmittingAction(false);
    }
  };

  // Quick Action: Email
  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLeadId) {
      sileo.error({ title: "Selecciona un lead", description: "Debes seleccionar el prospecto destinatario." });
      return;
    }
    const targetLead = recentLeads.find((l) => String(l.id) === selectedLeadId);
    setIsSubmittingAction(true);
    try {
      const content = `✉️ Correo enviado: Asunto "${emailSubject}" ${actionNotes ? `- Mensaje: ${actionNotes.trim()}` : ""}`;
      await createNoteApi(selectedLeadId, content);
      if (targetLead?.email) {
        window.open(`mailto:${targetLead.email}?subject=${encodeURIComponent(emailSubject)}&body=${encodeURIComponent(actionNotes)}`);
      }
      sileo.success({
        title: "Correo registrado",
        description: "Se registró el envío del correo en el historial del prospecto.",
      });
      setIsEmailModalOpen(false);
      setActionNotes("");
      fetchDashboardData();
    } catch (err: any) {
      sileo.error({ title: "Error", description: err?.message || "No se pudo registrar el correo." });
    } finally {
      setIsSubmittingAction(false);
    }
  };

  // Quick Action: Cita
  const handleMeetingSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLeadId) {
      sileo.error({ title: "Selecciona un lead", description: "Debes seleccionar un prospecto." });
      return;
    }
    const targetLead = recentLeads.find((l) => String(l.id) === selectedLeadId);
    if (!targetLead) return;

    setIsSubmittingAction(true);
    try {
      await updateLeadApi(targetLead.id, {
        name: targetLead.name,
        phone: targetLead.phone,
        email: targetLead.email,
        stage: targetLead.stage,
        service: targetLead.service,
        nextFollowUp: meetingDate,
      });
      const content = `📅 Cita agendada para el ${meetingDate}${actionNotes ? ` - Detalle: ${actionNotes.trim()}` : ""}`;
      await createNoteApi(targetLead.id, content);
      sileo.success({
        title: "Cita programada",
        description: `Seguimiento agendado para el ${meetingDate}.`,
      });
      setIsMeetingModalOpen(false);
      setActionNotes("");
      fetchDashboardData();
    } catch (err: any) {
      sileo.error({ title: "Error", description: err?.message || "No se pudo agendar la cita." });
    } finally {
      setIsSubmittingAction(false);
    }
  };

  // Quick Action: Nota
  const handleNoteSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLeadId) {
      sileo.error({ title: "Selecciona un lead", description: "Debes seleccionar a qué prospecto pertenece la nota." });
      return;
    }
    if (!actionNotes.trim()) {
      sileo.error({ title: "Nota vacía", description: "Ingresa el texto de la nota." });
      return;
    }
    setIsSubmittingAction(true);
    try {
      await createNoteApi(selectedLeadId, actionNotes.trim());
      sileo.success({
        title: "Nota añadida",
        description: "La nota fue agregada a la bitácora comercial.",
      });
      setIsNoteModalOpen(false);
      setActionNotes("");
      fetchDashboardData();
    } catch (err: any) {
      sileo.error({ title: "Error", description: err?.message || "No se pudo crear la nota." });
    } finally {
      setIsSubmittingAction(false);
    }
  };

  // Quick Action: Tarea
  const handleTaskSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!taskTitle.trim()) {
      sileo.error({ title: "Campo requerido", description: "Ingresa una descripción para la tarea." });
      return;
    }
    const selectedLead = recentLeads.find((l) => String(l.id) === selectedLeadId);
    const newTask = {
      id: `task-${Date.now()}`,
      title: taskTitle.trim(),
      leadId: selectedLead?.id,
      leadName: selectedLead?.name,
      dueDate: meetingDate || new Date().toISOString().split("T")[0],
      priority: taskPriority,
      type: "LLAMADA" as const,
      completed: false,
      createdAt: new Date().toISOString(),
    };
    try {
      const existing = localStorage.getItem("urleads_commercial_tasks_v1");
      const parsed = existing ? JSON.parse(existing) : [];
      localStorage.setItem("urleads_commercial_tasks_v1", JSON.stringify([newTask, ...parsed]));
      sileo.success({
        title: "Tarea registrada",
        description: "Tu tarea comercial ha sido agregada a la lista de Tareas.",
      });
      setIsTaskModalOpen(false);
      setTaskTitle("");
      setActionNotes("");
    } catch {
      // Ignored
    }
  };

  // Computed metrics
  const totalLeadsCount = metrics?.totalLeads ?? recentLeads.length ?? 0;
  const newCount = metrics?.leadsPorEtapa?.["NUEVO"] ?? 0;
  const inProgressCount = metrics?.leadsPorEtapa?.["EN_SEGUIMIENTO"] ?? 0;
  const closedCount = metrics?.leadsPorEtapa?.["CERRADO"] ?? 0;
  const totalPendingFollowUps =
    (metrics?.seguimientos?.hoy ?? 0) + (metrics?.seguimientos?.atrasados ?? 0);

  const rawPipelineValue = totalLeadsCount > 0 ? (newCount * 1250) + (inProgressCount * 2500) + (closedCount * 3500) : 124500;

  // Unread alerts count
  const unreadAlertsCount = hasDismissedNotifications ? 0 : (totalPendingFollowUps > 0 ? totalPendingFollowUps : 1);

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
      {/* ── Top Bar (Responsive across all screens) ── */}
      <header className="h-16 px-3 sm:px-6 border-b border-border bg-background flex items-center justify-between gap-2 sm:gap-4 shrink-0 z-30">
        {/* Left: Mobile Hamburger + Logo */}
        <div className="flex items-center gap-2 sm:gap-3">
          <button
            type="button"
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="md:hidden h-10 w-10 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
            aria-label="Abrir menú de navegación"
          >
            {isMobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>

          <Link to="/" className="flex items-center gap-2 hover:opacity-90 transition-opacity">
            <UrLeadsLogo size={28} showText />
          </Link>
        </div>

        {/* Middle: RuixenSearchCommand (Indexed search with ⌘K clear-all) */}
        <div className="flex-1 max-w-md hidden md:block">
          <RuixenSearchCommand
            leads={recentLeads}
            onOpenPipelineModal={() => setIsPipelineModalOpen(true)}
          />
        </div>

        {/* Right: Actions, Currency Switcher, Bell & Profile */}
        <div className="flex items-center gap-1.5 sm:gap-2.5">
          {/* New Lead Button */}
          <button
            type="button"
            onClick={() => setIsCreateModalOpen(true)}
            className="h-10 px-3.5 sm:px-4.5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-1.5 shadow-xs hover:bg-primary/90 transition-all cursor-pointer active:scale-95"
          >
            <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
            <span className="hidden sm:inline">Nuevo Lead</span>
          </button>

          {/* Functional Currency Switcher ("Botón de cambio" al lado de notificaciones) */}
          <button
            type="button"
            onClick={handleToggleCurrency}
            className="h-10 px-2.5 sm:px-3 rounded-full bg-secondary hover:bg-secondary/80 border border-border text-foreground font-bold text-xs flex items-center gap-1 transition-all cursor-pointer shadow-2xs active:scale-95"
            title="Cambiar divisa entre USD ($) y Soles (S/.)"
          >
            <DollarSign className="h-3.5 w-3.5 text-accent" />
            <span className="font-semibold">{currency === "USD" ? "$ USD" : "S/. PEN"}</span>
          </button>

          {/* Functional Bell Button */}
          <button
            type="button"
            onClick={() => setIsNotificationModalOpen(true)}
            className="relative h-10 w-10 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer shrink-0"
            title="Ver notificaciones comerciales"
          >
            <Bell className="h-4 w-4" />
            {unreadAlertsCount > 0 && (
              <span className="absolute top-2.5 right-2.5 w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
            )}
          </button>

          {/* User Profile Button: opens interactive profile modal */}
          <button
            type="button"
            onClick={() => setIsProfileModalOpen(true)}
            className="h-10 w-10 rounded-full bg-secondary border border-border text-foreground font-bold text-xs sm:text-sm flex items-center justify-center hover:bg-accent/15 hover:border-accent/40 transition-all cursor-pointer shrink-0"
            title={`Perfil: ${user?.username || "Usuario"}`}
          >
            {userInitials}
          </button>
        </div>
      </header>

      {/* ── Mobile Search Bar (Only shown on small screens) ── */}
      <div className="md:hidden px-3 py-2 border-b border-border bg-background/95">
        <RuixenSearchCommand
          leads={recentLeads}
          onOpenPipelineModal={() => setIsPipelineModalOpen(true)}
        />
      </div>

      {/* ── App Body: Sidebar + Main Content ── */}
      <div className="flex flex-1 overflow-hidden relative">
        {/* ── Desktop Sidebar (Hidden on mobile) ── */}
        <aside className="hidden md:flex w-52 sm:w-56 border-r border-border bg-background p-4 flex-col justify-between shrink-0 overflow-y-auto">
          <div className="space-y-6">
            {/* Main Navigation */}
            <div className="space-y-1.5">
              <Link
                to="/app"
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-full text-xs sm:text-sm transition-colors ${
                  isExactDashboard
                    ? "bg-secondary font-semibold text-foreground shadow-2xs"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium"
                }`}
              >
                <Home className={`h-4 w-4 ${isExactDashboard ? "text-accent" : ""}`} />
                <span>Resumen</span>
              </Link>
              <Link
                to="/app/leads"
                className={`flex items-center justify-between px-3.5 py-2.5 rounded-full text-xs sm:text-sm transition-colors ${
                  isLeadsActive
                    ? "bg-secondary font-semibold text-foreground shadow-2xs"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium"
                }`}
              >
                <div className="flex items-center gap-3">
                  <Users className={`h-4 w-4 ${isLeadsActive ? "text-accent" : ""}`} />
                  <span>Leads</span>
                </div>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-accent/15 text-accent">
                  {metrics?.totalLeads ?? (isLoadingMetrics ? "..." : 0)}
                </span>
              </Link>
              <Link
                to="/app/leads?followUp=today"
                className={`flex items-center justify-between px-3.5 py-2.5 rounded-full text-xs sm:text-sm transition-colors ${
                  location.search.includes("followUp")
                    ? "bg-secondary font-semibold text-foreground shadow-2xs"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium"
                }`}
              >
                <div className="flex items-center gap-3">
                  <Calendar className="h-4 w-4" />
                  <span>Seguimientos</span>
                </div>
                {totalPendingFollowUps > 0 && (
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/15 text-amber-600">
                    {totalPendingFollowUps}
                  </span>
                )}
              </Link>
              <Link
                to="/app/reportes"
                className={`flex items-center justify-between px-3.5 py-2.5 rounded-full text-xs sm:text-sm transition-colors ${
                  path === "/app/reportes"
                    ? "bg-secondary font-semibold text-foreground shadow-2xs"
                    : "text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium"
                }`}
              >
                <div className="flex items-center gap-3">
                  <BarChart2 className="h-4 w-4" />
                  <span>Reportes</span>
                </div>
              </Link>
              <div
                onClick={() => setIsPipelineModalOpen(true)}
                className="flex items-center gap-3 px-3.5 py-2.5 rounded-full text-xs sm:text-sm text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium transition-colors cursor-pointer"
              >
                <Activity className="h-4 w-4" />
                <span>Pipeline</span>
              </div>
            </div>

            {/* Section "Operaciones" */}
            <div>
              <div className="px-3 text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-2">
                Operaciones
              </div>
              <div className="space-y-1.5">
                <Link
                  to="/app/tareas"
                  className={`flex items-center gap-3 px-3.5 py-2.5 rounded-full text-xs sm:text-sm transition-colors ${
                    path === "/app/tareas"
                      ? "bg-secondary font-semibold text-foreground shadow-2xs"
                      : "text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium"
                  }`}
                >
                  <CheckSquare className="h-4 w-4" />
                  <span>Tareas</span>
                </Link>
                <button
                  type="button"
                  onClick={() => setIsNotificationModalOpen(true)}
                  className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-full text-xs sm:text-sm text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium transition-colors cursor-pointer text-left"
                >
                  <div className="flex items-center gap-3">
                    <Bell className="h-4 w-4" />
                    <span>Notificaciones</span>
                  </div>
                  {unreadAlertsCount > 0 && (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-500/15 text-rose-600">
                      {unreadAlertsCount}
                    </span>
                  )}
                </button>
                <Link
                  to="/app/ajustes"
                  className={`flex items-center gap-3 px-3.5 py-2.5 rounded-full text-xs sm:text-sm transition-colors ${
                    path === "/app/ajustes"
                      ? "bg-secondary font-semibold text-foreground shadow-2xs"
                      : "text-muted-foreground hover:text-foreground hover:bg-secondary/50 font-medium"
                  }`}
                >
                  <Settings className="h-4 w-4" />
                  <span>Ajustes</span>
                </Link>
              </div>
            </div>
          </div>

          {/* Sidebar Bottom Profile */}
          <div className="pt-3.5 border-t border-border mt-auto">
            <div className="flex items-center justify-between px-1">
              <button
                type="button"
                onClick={() => setIsProfileModalOpen(true)}
                className="flex items-center gap-2.5 overflow-hidden text-left hover:opacity-85 transition-opacity cursor-pointer flex-1"
              >
                <div className="h-8 w-8 rounded-full bg-secondary border border-border text-foreground font-bold text-xs flex items-center justify-center shrink-0">
                  {userInitials}
                </div>
                <div className="truncate">
                  <p className="text-xs font-semibold text-foreground truncate">{user?.username || "Sebas"}</p>
                  <p className="text-[10px] text-muted-foreground truncate">En línea</p>
                </div>
              </button>
              <button
                type="button"
                onClick={handleLogout}
                title="Cerrar sesión"
                className="p-2 rounded-full text-muted-foreground hover:text-rose-500 hover:bg-rose-500/10 transition-colors cursor-pointer shrink-0"
              >
                <LogOut className="h-4 w-4" />
              </button>
            </div>
          </div>
        </aside>

        {/* ── Mobile Slide-Over Drawer (Accessible via hamburger) ── */}
        {isMobileMenuOpen && (
          <div className="md:hidden fixed inset-0 z-40 flex">
            <div
              className="fixed inset-0 bg-black/50 backdrop-blur-xs"
              onClick={() => setIsMobileMenuOpen(false)}
            />
            <div className="relative w-64 max-w-[80vw] bg-background border-r border-border p-5 flex flex-col justify-between z-50 animate-in slide-in-from-left duration-200">
              <div className="space-y-5">
                <div className="flex items-center justify-between pb-3 border-b border-border">
                  <UrLeadsLogo size={24} showText />
                  <button
                    type="button"
                    onClick={() => setIsMobileMenuOpen(false)}
                    className="p-1 rounded-full text-muted-foreground hover:text-foreground"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>

                <div className="space-y-1">
                  <Link
                    to="/app"
                    className="flex items-center gap-3 px-3.5 py-2.5 rounded-full text-sm font-medium hover:bg-secondary text-foreground"
                  >
                    <Home className="h-4 w-4 text-accent" />
                    <span>Resumen</span>
                  </Link>
                  <Link
                    to="/app/leads"
                    className="flex items-center justify-between px-3.5 py-2.5 rounded-full text-sm font-medium hover:bg-secondary text-foreground"
                  >
                    <div className="flex items-center gap-3">
                      <Users className="h-4 w-4" />
                      <span>Leads</span>
                    </div>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-accent/15 text-accent">
                      {totalLeadsCount}
                    </span>
                  </Link>
                  <Link
                    to="/app/reportes"
                    className="flex items-center gap-3 px-3.5 py-2.5 rounded-full text-sm font-medium hover:bg-secondary text-foreground"
                  >
                    <BarChart2 className="h-4 w-4" />
                    <span>Reportes</span>
                  </Link>
                  <Link
                    to="/app/tareas"
                    className="flex items-center gap-3 px-3.5 py-2.5 rounded-full text-sm font-medium hover:bg-secondary text-foreground"
                  >
                    <CheckSquare className="h-4 w-4" />
                    <span>Tareas</span>
                  </Link>
                  <Link
                    to="/app/ajustes"
                    className="flex items-center gap-3 px-3.5 py-2.5 rounded-full text-sm font-medium hover:bg-secondary text-foreground"
                  >
                    <Settings className="h-4 w-4" />
                    <span>Ajustes</span>
                  </Link>
                </div>
              </div>

              <div className="pt-4 border-t border-border flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="h-8 w-8 rounded-full bg-secondary flex items-center justify-center font-bold text-xs">
                    {userInitials}
                  </div>
                  <span className="text-xs font-semibold">{user?.username || "Usuario"}</span>
                </div>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="p-2 text-rose-500 hover:bg-rose-50 rounded-full"
                >
                  <LogOut className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── Main Content Area ── */}
        {isExactDashboard ? (
          <main className="flex-1 bg-secondary/30 p-4 sm:p-7 lg:p-9 space-y-6 overflow-y-auto pb-24 md:pb-8">
            {/* Top Greeting */}
            <div className="flex items-center justify-between">
              <div>
                <h1 className="text-xl sm:text-2xl font-bold text-foreground tracking-tight">
                  Hola, {user?.username || "Sebas"}
                </h1>
                <p className="text-xs sm:text-sm text-muted-foreground mt-1 font-medium">
                  Tu resumen comercial, pipeline y próximos seguimientos
                </p>
              </div>
            </div>

            {/* Action Buttons Row: Smooth Horizontal Scrollable on Mobile (.no-scrollbar) */}
            <div className="flex items-center gap-2 sm:gap-2.5 overflow-x-auto no-scrollbar pb-1">
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(true)}
                className="h-10 px-5 rounded-full bg-accent text-accent-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 shadow-xs hover:opacity-90 active:scale-95 transition-all cursor-pointer shrink-0"
              >
                <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
                <span>Nuevo Lead</span>
              </button>
              <button
                type="button"
                onClick={() => setIsCallModalOpen(true)}
                className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
              >
                <Phone className="h-3.5 w-3.5 text-sky-500" />
                <span>Llamada</span>
              </button>
              <button
                type="button"
                onClick={() => setIsEmailModalOpen(true)}
                className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
              >
                <Mail className="h-3.5 w-3.5 text-indigo-500" />
                <span>Email</span>
              </button>
              <button
                type="button"
                onClick={() => setIsMeetingModalOpen(true)}
                className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
              >
                <Calendar className="h-3.5 w-3.5 text-emerald-500" />
                <span>Cita</span>
              </button>
              <button
                type="button"
                onClick={() => setIsNoteModalOpen(true)}
                className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
              >
                <FileText className="h-3.5 w-3.5 text-amber-500" />
                <span>Nota</span>
              </button>
              <button
                type="button"
                onClick={() => setIsTaskModalOpen(true)}
                className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
              >
                <CheckSquare className="h-3.5 w-3.5 text-purple-500" />
                <span>Tarea</span>
              </button>
            </div>

            {/* Two Equal-Width Cards: Responsive Grid (1 col on mobile, 2 cols on lg) */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Pipeline Card: Clickable with live currency calculation */}
              <div
                onClick={() => setIsPipelineModalOpen(true)}
                className="bg-background/95 backdrop-blur-md rounded-3xl border border-border p-5 sm:p-7 flex flex-col justify-between shadow-2xs hover:shadow-xs hover:border-accent/40 transition-all cursor-pointer group"
                title="Haz clic para ver el desglose detallado del pipeline"
              >
                <div>
                  <div className="flex items-center justify-between text-muted-foreground mb-3">
                    <div className="flex items-center gap-2.5">
                      <div className="h-5 w-5 rounded-full bg-emerald-50 text-emerald-600 border border-emerald-200 flex items-center justify-center">
                        <Check className="h-3 w-3 stroke-[3]" />
                      </div>
                      <span className="font-semibold text-foreground text-sm sm:text-base group-hover:text-accent transition-colors">
                        Pipeline Comercial
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="px-3 py-1 rounded-full bg-secondary text-xs font-medium text-muted-foreground">
                        Activo
                      </span>
                      <span className="px-2.5 py-1 rounded-full bg-accent/10 text-accent text-xs font-semibold group-hover:bg-accent group-hover:text-accent-foreground transition-all">
                        Desglose ↗
                      </span>
                    </div>
                  </div>

                  <div className="mt-2">
                    <span className="text-3xl sm:text-4xl lg:text-5xl font-bold tracking-tight text-foreground font-body">
                      {formatMoney(rawPipelineValue)}
                    </span>
                    <span className="text-base sm:text-lg text-muted-foreground font-semibold">
                      .00
                    </span>
                  </div>

                  {/* Stats */}
                  <div className="flex items-center gap-3 mt-3 text-xs sm:text-sm">
                    <span className="text-muted-foreground">Últimos 30 Días</span>
                    <span className="text-emerald-600 font-semibold">
                      +{formatMoney(Math.max(12, Math.round(totalLeadsCount * 1.5)) * 1000)}
                    </span>
                    <span className="text-rose-500 font-semibold">
                      -{formatMoney(Math.round(totalLeadsCount * 0.4) * 1000)}
                    </span>
                  </div>
                </div>

                {/* SVG Area Chart */}
                <div className="mt-5 w-full h-24 sm:h-28 relative overflow-hidden">
                  <svg
                    viewBox="0 0 360 80"
                    preserveAspectRatio="none"
                    className="w-full h-full"
                  >
                    <defs>
                      <linearGradient
                        id="realDashboardBalanceGradient"
                        x1="0"
                        y1="0"
                        x2="0"
                        y2="1"
                      >
                        <stop offset="0%" stopColor="hsl(var(--accent))" stopOpacity="0.18" />
                        <stop offset="100%" stopColor="hsl(var(--accent))" stopOpacity="0" />
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
                      strokeWidth="2.5"
                      strokeLinecap="round"
                    />
                  </svg>
                </div>
              </div>

              {/* Stages Card */}
              <div className="bg-background/95 backdrop-blur-md rounded-3xl border border-border p-5 sm:p-7 flex flex-col shadow-2xs hover:shadow-xs transition-shadow">
                {/* Header */}
                <div className="flex items-center justify-between pb-3 border-b border-border/60">
                  <span className="font-semibold text-foreground text-sm sm:text-base">
                    Distribución por Etapa
                  </span>
                  <div className="flex items-center gap-2 text-muted-foreground">
                    <button
                      type="button"
                      onClick={() => setIsCreateModalOpen(true)}
                      className="h-8 w-8 rounded-full flex items-center justify-center hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                      title="Registrar prospecto"
                    >
                      <Plus className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => navigate("/app/leads")}
                      className="h-8 w-8 rounded-full flex items-center justify-center hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
                      title="Ver prospectos"
                    >
                      <MoreVertical className="h-4 w-4" />
                    </button>
                  </div>
                </div>

                {/* 3 Rows (Bubbly capsules) */}
                <div className="flex-1 flex flex-col justify-around py-3 gap-2.5">
                  <Link
                    to="/app/leads?stage=NUEVO"
                    className="flex items-center justify-between p-3.5 rounded-2xl bg-secondary/35 hover:bg-secondary/70 transition-colors group"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-3 w-3 rounded-full bg-sky-400 group-hover:scale-110 transition-transform" />
                      <span className="text-foreground font-medium text-xs sm:text-sm">Nuevos (Por calificar)</span>
                    </div>
                    <span className="font-bold text-foreground text-xs sm:text-sm font-body">
                      {newCount} Leads
                    </span>
                  </Link>

                  <Link
                    to="/app/leads?stage=EN_SEGUIMIENTO"
                    className="flex items-center justify-between p-3.5 rounded-2xl bg-secondary/35 hover:bg-secondary/70 transition-colors group"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-3 w-3 rounded-full bg-amber-400 group-hover:scale-110 transition-transform" />
                      <span className="text-foreground font-medium text-xs sm:text-sm">En Seguimiento</span>
                    </div>
                    <span className="font-bold text-foreground text-xs sm:text-sm font-body">
                      {inProgressCount} Leads
                    </span>
                  </Link>

                  <Link
                    to="/app/leads?stage=CERRADO"
                    className="flex items-center justify-between p-3.5 rounded-2xl bg-secondary/35 hover:bg-secondary/70 transition-colors group"
                  >
                    <div className="flex items-center gap-3">
                      <div className="h-3 w-3 rounded-full bg-emerald-500 group-hover:scale-110 transition-transform" />
                      <span className="text-foreground font-medium text-xs sm:text-sm">Cerrados (Concretados)</span>
                    </div>
                    <span className="font-bold text-foreground text-xs sm:text-sm font-body">
                      {closedCount} Leads
                    </span>
                  </Link>
                </div>
              </div>
            </div>

            {/* Leads Recientes Table */}
            <div className="bg-background/95 backdrop-blur-md rounded-3xl border border-border p-5 sm:p-7 shadow-2xs hover:shadow-xs transition-shadow">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm sm:text-base font-semibold text-foreground">
                  Leads Recientes
                </h3>
                <Link
                  to="/app/leads"
                  className="px-3.5 py-1.5 rounded-full bg-secondary hover:bg-secondary/80 text-xs font-semibold text-foreground transition-colors"
                >
                  Ver todos
                </Link>
              </div>

              <div className="overflow-x-auto no-scrollbar">
                <table className="w-full text-left min-w-[500px]">
                  <thead>
                    <tr className="border-b border-border/60 text-xs text-muted-foreground uppercase font-semibold">
                      <th className="pb-3 font-semibold">Fecha</th>
                      <th className="pb-3 font-semibold">Prospecto</th>
                      <th className="pb-3 font-semibold text-right">Servicio / Empresa</th>
                      <th className="pb-3 font-semibold text-right">Etapa</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/40 text-xs sm:text-sm">
                    {recentLeadsDisplay.map((lead) => (
                      <tr
                        key={lead.id}
                        onClick={() => navigate(`/app/leads/${lead.id}`)}
                        className="hover:bg-secondary/50 cursor-pointer transition-colors"
                      >
                        <td className="py-3.5 text-muted-foreground text-xs sm:text-sm">
                          {lead.date}
                        </td>
                        <td className="py-3.5 font-semibold text-foreground text-xs sm:text-sm">
                          {lead.name}
                        </td>
                        <td className="py-3.5 text-right text-muted-foreground text-xs sm:text-sm">
                          {lead.service}
                        </td>
                        <td className="py-3.5 text-right">
                          <span
                            className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border ${lead.stageBadgeClass}`}
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
          <main className="flex-1 bg-secondary/30 p-3 sm:p-6 lg:p-8 overflow-y-auto pb-24 md:pb-8">
            <div className="aurora-outlet-card">
              <Outlet />
            </div>
          </main>
        )}
      </div>

      {/* ── Mobile Bottom Navigation Bar (Shown only on small screens < md) ── */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 h-16 bg-background/95 backdrop-blur-xl border-t border-border z-30 flex items-center justify-around px-2 shadow-lg">
        <Link
          to="/app"
          className={`flex flex-col items-center justify-center gap-1 py-1 px-3 rounded-2xl transition-colors ${
            isExactDashboard ? "text-accent font-bold" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <Home className="h-5 w-5" />
          <span className="text-[10px]">Inicio</span>
        </Link>
        <Link
          to="/app/leads"
          className={`flex flex-col items-center justify-center gap-1 py-1 px-3 rounded-2xl transition-colors ${
            isLeadsActive ? "text-accent font-bold" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <Users className="h-5 w-5" />
          <span className="text-[10px]">Leads</span>
        </Link>
        <Link
          to="/app/tareas"
          className={`flex flex-col items-center justify-center gap-1 py-1 px-3 rounded-2xl transition-colors ${
            path === "/app/tareas" ? "text-accent font-bold" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <CheckSquare className="h-5 w-5" />
          <span className="text-[10px]">Tareas</span>
        </Link>
        <Link
          to="/app/reportes"
          className={`flex flex-col items-center justify-center gap-1 py-1 px-3 rounded-2xl transition-colors ${
            path === "/app/reportes" ? "text-accent font-bold" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <BarChart2 className="h-5 w-5" />
          <span className="text-[10px]">Reportes</span>
        </Link>
        <button
          type="button"
          onClick={() => setIsMobileMenuOpen(true)}
          className="flex flex-col items-center justify-center gap-1 py-1 px-3 rounded-2xl text-muted-foreground hover:text-foreground cursor-pointer"
        >
          <Menu className="h-5 w-5" />
          <span className="text-[10px]">Más</span>
        </button>
      </nav>

      {/* ── Modal: Perfil de Usuario ── */}
      {isProfileModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-sm bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 space-y-5"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Perfil de Usuario
              </span>
              <button
                type="button"
                onClick={() => setIsProfileModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="flex flex-col items-center text-center space-y-2">
              <div className="h-16 w-16 rounded-full bg-secondary border-2 border-border text-foreground font-bold text-xl flex items-center justify-center shadow-xs">
                {userInitials}
              </div>
              <div>
                <h4 className="text-base font-bold text-foreground">{user?.username || "Usuario"}</h4>
                <p className="text-xs text-muted-foreground font-medium">{user?.username || "admin"}@urleads.com</p>
                <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-500/10 text-emerald-700 border border-emerald-500/20 mt-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                  Sesión activa • Comercial
                </span>
              </div>
            </div>

            <div className="space-y-2 pt-2 border-t border-border">
              <button
                type="button"
                onClick={() => {
                  setIsProfileModalOpen(false);
                  navigate("/app/ajustes");
                }}
                className="w-full h-10 px-4 rounded-full bg-secondary hover:bg-secondary/80 text-foreground text-xs sm:text-sm font-semibold flex items-center justify-center gap-2 transition-colors cursor-pointer"
              >
                <Settings className="h-4 w-4" />
                <span>Configurar Cuenta & Preferencias</span>
              </button>

              <button
                type="button"
                onClick={handleLogout}
                className="w-full h-10 px-4 rounded-full bg-rose-500/10 hover:bg-rose-500/20 text-rose-600 text-xs sm:text-sm font-semibold flex items-center justify-center gap-2 transition-colors cursor-pointer"
              >
                <LogOut className="h-4 w-4" />
                <span>Cerrar sesión</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal: Notificaciones Comerciales ── */}
      {isNotificationModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-md bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 space-y-4 max-h-[85vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2">
                <Bell className="h-4 w-4 text-accent" />
                <h3 className="text-base font-bold text-foreground">Notificaciones</h3>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setHasDismissedNotifications(true);
                    sileo.info({ title: "Notificaciones", description: "Marcadas como leídas." });
                  }}
                  className="text-xs text-muted-foreground hover:text-foreground font-semibold cursor-pointer"
                >
                  Marcar leídas
                </button>
                <button
                  type="button"
                  onClick={() => setIsNotificationModalOpen(false)}
                  className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </div>

            <div className="space-y-2.5">
              {(metrics?.seguimientos?.hoy ?? 0) > 0 && (
                <div
                  onClick={() => {
                    setIsNotificationModalOpen(false);
                    navigate("/app/leads?followUp=today");
                  }}
                  className="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20 cursor-pointer hover:bg-amber-500/15 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-amber-700">Seguimientos para hoy</span>
                    <span className="text-[10px] text-amber-600 font-semibold">Hoy</span>
                  </div>
                  <p className="text-xs text-foreground mt-1">
                    Tienes {metrics?.seguimientos?.hoy} prospecto(s) programados para contactar hoy.
                  </p>
                </div>
              )}

              {(metrics?.seguimientos?.atrasados ?? 0) > 0 && (
                <div
                  onClick={() => {
                    setIsNotificationModalOpen(false);
                    navigate("/app/leads?followUp=overdue");
                  }}
                  className="p-3.5 rounded-2xl bg-rose-500/10 border border-rose-500/20 cursor-pointer hover:bg-rose-500/15 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-rose-700">Seguimientos atrasados</span>
                    <span className="text-[10px] text-rose-600 font-semibold">Atención</span>
                  </div>
                  <p className="text-xs text-foreground mt-1">
                    Existen {metrics?.seguimientos?.atrasados} seguimiento(s) con fecha vencida que requieren acción.
                  </p>
                </div>
              )}

              {recentLeads.length > 0 && (
                <div
                  onClick={() => {
                    setIsNotificationModalOpen(false);
                    navigate(`/app/leads/${recentLeads[0].id}`);
                  }}
                  className="p-3.5 rounded-2xl bg-secondary/40 border border-border cursor-pointer hover:bg-secondary/70 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-foreground">Lead Reciente</span>
                    <span className="text-[10px] text-muted-foreground font-semibold">Nuevo</span>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    {recentLeads[0].name} ({recentLeads[0].service || "Servicio"}) se encuentra registrado en etapa {recentLeads[0].stage}.
                  </p>
                </div>
              )}

              <div className="p-3.5 rounded-2xl bg-secondary/30 border border-border">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-foreground">Pipeline Operativo</span>
                  <span className="text-[10px] text-emerald-600 font-semibold">En línea</span>
                </div>
                <p className="text-xs text-muted-foreground mt-1">
                  Tu base comercial está sincronizada y operando en <strong className="text-foreground">{currency === "USD" ? "Dólares ($)" : "Soles (S/.)"}</strong>.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal: Desglose Interactivo del Pipeline Comercial ── */}
      {isPipelineModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-xl bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-5 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3.5 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-emerald-500/15 text-emerald-600 flex items-center justify-center font-bold text-xs">
                  <DollarSign className="h-4 w-4" />
                </div>
                <div>
                  <h3 className="text-base sm:text-lg font-bold text-foreground">
                    Desglose del Pipeline Comercial
                  </h3>
                  <p className="text-xs text-muted-foreground font-medium">
                    Valor total estimado acumulado: {formatMoney(rawPipelineValue)}.00
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsPipelineModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Stages Calculation Breakdown */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="p-3.5 rounded-2xl bg-sky-500/10 border border-sky-500/20">
                <span className="text-[10px] font-bold text-sky-700 uppercase">Nuevos</span>
                <p className="text-lg font-bold text-foreground mt-1">{newCount} Leads</p>
                <p className="text-xs text-muted-foreground mt-0.5">Est. {formatMoney(newCount * 1250)}</p>
                <span className="text-[10px] font-semibold text-sky-600">Prob. 25%</span>
              </div>

              <div className="p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/20">
                <span className="text-[10px] font-bold text-amber-700 uppercase">En Seguimiento</span>
                <p className="text-lg font-bold text-foreground mt-1">{inProgressCount} Leads</p>
                <p className="text-xs text-muted-foreground mt-0.5">Est. {formatMoney(inProgressCount * 2500)}</p>
                <span className="text-[10px] font-semibold text-amber-600">Prob. 60%</span>
              </div>

              <div className="p-3.5 rounded-2xl bg-emerald-500/10 border border-emerald-500/20">
                <span className="text-[10px] font-bold text-emerald-700 uppercase">Cerrados</span>
                <p className="text-lg font-bold text-emerald-600 mt-1">{closedCount} Leads</p>
                <p className="text-xs text-muted-foreground mt-0.5">Ingreso {formatMoney(closedCount * 3500)}</p>
                <span className="text-[10px] font-semibold text-emerald-600">100% Ganado</span>
              </div>
            </div>

            {/* Recent Leads in Pipeline */}
            <div>
              <p className="text-xs font-semibold text-foreground uppercase tracking-wider mb-2">
                Prospectos activos en el pipeline
              </p>
              <div className="space-y-2 max-h-48 overflow-y-auto">
                {recentLeads.slice(0, 5).map((l) => (
                  <div
                    key={l.id}
                    onClick={() => {
                      setIsPipelineModalOpen(false);
                      navigate(`/app/leads/${l.id}`);
                    }}
                    className="flex items-center justify-between p-3 rounded-2xl bg-secondary/30 hover:bg-secondary/70 border border-border cursor-pointer transition-colors"
                  >
                    <div>
                      <p className="text-xs font-semibold text-foreground">{l.name}</p>
                      <p className="text-[11px] text-muted-foreground">{l.service || "Servicio IT"}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-semibold text-foreground">
                        {l.stage === "CERRADO"
                          ? formatMoney(3500)
                          : l.stage === "EN_SEGUIMIENTO"
                          ? formatMoney(2500)
                          : formatMoney(1250)}
                      </span>
                      <ChevronRight className="h-4 w-4 text-muted-foreground" />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
              <button
                type="button"
                onClick={() => {
                  setIsPipelineModalOpen(false);
                  navigate("/app/reportes");
                }}
                className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
              >
                Ver en Reportes
              </button>
              <button
                type="button"
                onClick={() => {
                  setIsPipelineModalOpen(false);
                  navigate("/app/leads");
                }}
                className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm hover:bg-primary/90 transition-all cursor-pointer shadow-xs"
              >
                Ir a Leads
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal: Registrar Llamada ── */}
      {isCallModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-4 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-sky-500/15 text-sky-600 flex items-center justify-center font-bold text-xs">
                  <Phone className="h-4 w-4" />
                </div>
                <h3 className="text-base sm:text-lg font-bold text-foreground">
                  Registrar Llamada Comercial
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsCallModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleCallSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Seleccionar Prospecto *
                </label>
                <select
                  required
                  value={selectedLeadId}
                  onChange={(e) => setSelectedLeadId(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                >
                  {recentLeads.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name} ({l.phone || "Sin tlf"} - {l.stage})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Resultado de la llamada
                </label>
                <select
                  value={callOutcome}
                  onChange={(e) => setCallOutcome(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                >
                  <option value="Contestó - Interesado">Contestó - Interesado (Avanzar)</option>
                  <option value="Contestó - Enviar propuesta">Contestó - Enviar propuesta por email</option>
                  <option value="No contestó / Buzón">No contestó / Dejó buzón</option>
                  <option value="Volver a llamar luego">Volver a llamar en otro horario</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Notas de la llamada
                </label>
                <textarea
                  rows={3}
                  placeholder="Detalles sobre lo conversado con el cliente..."
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="w-full p-3 rounded-2xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsCallModalOpen(false)}
                  className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmittingAction}
                  className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 hover:bg-primary/90 transition-all cursor-pointer shadow-xs disabled:opacity-50"
                >
                  {isSubmittingAction ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Check className="h-4 w-4" />
                  )}
                  <span>Guardar Llamada</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Enviar / Registrar Email ── */}
      {isEmailModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-4 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-indigo-500/15 text-indigo-600 flex items-center justify-center font-bold text-xs">
                  <Mail className="h-4 w-4" />
                </div>
                <h3 className="text-base sm:text-lg font-bold text-foreground">
                  Registrar / Enviar Correo
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsEmailModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleEmailSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Destinatario *
                </label>
                <select
                  required
                  value={selectedLeadId}
                  onChange={(e) => setSelectedLeadId(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                >
                  {recentLeads.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name} ({l.email || "Sin email registrado"})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Asunto del Correo
                </label>
                <input
                  type="text"
                  required
                  value={emailSubject}
                  onChange={(e) => setEmailSubject(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Cuerpo del mensaje / Resumen enviado
                </label>
                <textarea
                  rows={3}
                  placeholder="Estimado cliente, adjuntamos la propuesta de servicios comercial..."
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="w-full p-3 rounded-2xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsEmailModalOpen(false)}
                  className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmittingAction}
                  className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 hover:bg-primary/90 transition-all cursor-pointer shadow-xs disabled:opacity-50"
                >
                  {isSubmittingAction ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Mail className="h-4 w-4" />
                  )}
                  <span>Registrar Email</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Agendar Cita ── */}
      {isMeetingModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-4 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-emerald-500/15 text-emerald-600 flex items-center justify-center font-bold text-xs">
                  <Calendar className="h-4 w-4" />
                </div>
                <h3 className="text-base sm:text-lg font-bold text-foreground">
                  Agendar Cita / Seguimiento
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsMeetingModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleMeetingSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Prospecto *
                </label>
                <select
                  required
                  value={selectedLeadId}
                  onChange={(e) => setSelectedLeadId(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                >
                  {recentLeads.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name} ({l.service || "Servicio"} - {l.stage})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Fecha de la cita *
                </label>
                <input
                  type="date"
                  required
                  value={meetingDate}
                  onChange={(e) => setMeetingDate(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Objetivo / Notas de la cita
                </label>
                <textarea
                  rows={3}
                  placeholder="Reunión de presentación, demostración del producto..."
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="w-full p-3 rounded-2xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsMeetingModalOpen(false)}
                  className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmittingAction}
                  className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 hover:bg-primary/90 transition-all cursor-pointer shadow-xs disabled:opacity-50"
                >
                  {isSubmittingAction ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <Calendar className="h-4 w-4" />
                  )}
                  <span>Guardar Cita</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Añadir Nota ── */}
      {isNoteModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-4 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-amber-500/15 text-amber-600 flex items-center justify-center font-bold text-xs">
                  <FileText className="h-4 w-4" />
                </div>
                <h3 className="text-base sm:text-lg font-bold text-foreground">
                  Añadir Nota Interna
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsNoteModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleNoteSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Prospecto asociado *
                </label>
                <select
                  required
                  value={selectedLeadId}
                  onChange={(e) => setSelectedLeadId(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                >
                  {recentLeads.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name} ({l.service || "Servicio"} - {l.stage})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Contenido de la nota *
                </label>
                <textarea
                  rows={4}
                  required
                  placeholder="Añade observaciones importantes, requerimientos especiales del cliente..."
                  value={actionNotes}
                  onChange={(e) => setActionNotes(e.target.value)}
                  className="w-full p-3 rounded-2xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsNoteModalOpen(false)}
                  className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmittingAction}
                  className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 hover:bg-primary/90 transition-all cursor-pointer shadow-xs disabled:opacity-50"
                >
                  {isSubmittingAction ? (
                    <RefreshCw className="h-4 w-4 animate-spin" />
                  ) : (
                    <FileText className="h-4 w-4" />
                  )}
                  <span>Guardar Nota</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Registrar Tarea Rápida ── */}
      {isTaskModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-4 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-purple-500/15 text-purple-600 flex items-center justify-center font-bold text-xs">
                  <CheckSquare className="h-4 w-4" />
                </div>
                <h3 className="text-base sm:text-lg font-bold text-foreground">
                  Registrar Tarea Rápida
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsTaskModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleTaskSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Descripción de la tarea *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ej. Revisar contrato de servicios IT..."
                  value={taskTitle}
                  onChange={(e) => setTaskTitle(e.target.value)}
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Asociar a Prospecto
                  </label>
                  <select
                    value={selectedLeadId}
                    onChange={(e) => setSelectedLeadId(e.target.value)}
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  >
                    <option value="">Sin asociar (General)</option>
                    {recentLeads.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Prioridad
                  </label>
                  <select
                    value={taskPriority}
                    onChange={(e) => setTaskPriority(e.target.value as any)}
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  >
                    <option value="ALTA">Alta</option>
                    <option value="MEDIA">Media</option>
                    <option value="BAJA">Baja</option>
                  </select>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsTaskModalOpen(false)}
                  className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 hover:bg-primary/90 transition-all cursor-pointer shadow-xs"
                >
                  <CheckSquare className="h-4 w-4" />
                  <span>Guardar Tarea</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Create Lead Modal ── */}
      {isCreateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-5 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3.5 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-accent text-accent-foreground flex items-center justify-center font-bold text-xs shadow-2xs">
                  ✦
                </div>
                <div>
                  <h3 className="text-base sm:text-lg font-bold text-foreground">
                    Registrar Nuevo Lead
                  </h3>
                  <p className="text-xs text-muted-foreground font-medium">
                    Añade un prospecto para seguimiento comercial
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsCreateModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleCreateLeadSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
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
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                />
                {newLeadErrors.name && (
                  <p className="text-[11px] text-rose-500 mt-1 flex items-center gap-1 font-semibold">
                    <AlertCircle className="h-3 w-3" />
                    <span>{newLeadErrors.name}</span>
                  </p>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Teléfono
                  </label>
                  <input
                    type="text"
                    placeholder="+51 987 654 321"
                    value={newLeadForm.phone || ""}
                    onChange={(e) =>
                      setNewLeadForm((prev) => ({ ...prev, phone: e.target.value }))
                    }
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Correo electrónico
                  </label>
                  <input
                    type="email"
                    placeholder="juan@empresa.com"
                    value={newLeadForm.email || ""}
                    onChange={(e) =>
                      setNewLeadForm((prev) => ({ ...prev, email: e.target.value }))
                    }
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Servicio / Plan
                  </label>
                  <input
                    type="text"
                    placeholder="Consultoría, Enterprise..."
                    value={newLeadForm.service || ""}
                    onChange={(e) =>
                      setNewLeadForm((prev) => ({ ...prev, service: e.target.value }))
                    }
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
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
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  >
                    <option value="NUEVO">NUEVO (Por calificar)</option>
                    <option value="EN_SEGUIMIENTO">EN SEGUIMIENTO (Activo)</option>
                    <option value="CERRADO">CERRADO (Concretado)</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
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
                  className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                />
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsCreateModalOpen(false)}
                  className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmittingLead}
                  className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 hover:bg-primary/90 transition-all disabled:opacity-50 cursor-pointer shadow-xs"
                >
                  {isSubmittingLead ? (
                    <>
                      <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                      <span>Guardando...</span>
                    </>
                  ) : (
                    <>
                      <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
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
