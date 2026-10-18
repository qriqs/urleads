import React, { useState, useEffect, useMemo, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  Plus,
  CheckSquare,
  Clock,
  AlertCircle,
  Trash2,
  Calendar,
  User,
  Check,
  Search,
  X,
  Phone,
  Mail,
  FileText,
  Filter,
} from "lucide-react";
import { sileo } from "sileo";
import { getLeadsApi, Lead } from "../services/leads";

export interface CrmTask {
  id: string;
  title: string;
  leadId?: number;
  leadName?: string;
  dueDate: string;
  priority: "ALTA" | "MEDIA" | "BAJA";
  type: "LLAMADA" | "EMAIL" | "REUNION" | "PROPUESTA" | "OTRO";
  completed: boolean;
  createdAt: string;
}

const STORAGE_KEY = "urleads_commercial_tasks_v1";

const DEFAULT_TASKS: CrmTask[] = [
  {
    id: "task-1",
    title: "Llamar a Carlos Torres para confirmar presupuesto de Consultoría IT",
    leadName: "Carlos Torres",
    dueDate: new Date().toISOString().split("T")[0],
    priority: "ALTA",
    type: "LLAMADA",
    completed: false,
    createdAt: new Date().toISOString(),
  },
  {
    id: "task-2",
    title: "Enviar propuesta comercial ajustada con descuento especial",
    leadName: "Tech Solutions S.A.",
    dueDate: new Date(Date.now() + 86400000).toISOString().split("T")[0],
    priority: "ALTA",
    type: "PROPUESTA",
    completed: false,
    createdAt: new Date().toISOString(),
  },
  {
    id: "task-3",
    title: "Reunión de demostración del panel comercial UrLeads",
    leadName: "María López",
    dueDate: new Date(Date.now() + 172800000).toISOString().split("T")[0],
    priority: "MEDIA",
    type: "REUNION",
    completed: false,
    createdAt: new Date().toISOString(),
  },
  {
    id: "task-4",
    title: "Seguimiento por WhatsApp sobre dudas de facturación",
    leadName: "Distribuidora Sur",
    dueDate: new Date(Date.now() - 86400000).toISOString().split("T")[0],
    priority: "MEDIA",
    type: "EMAIL",
    completed: true,
    createdAt: new Date().toISOString(),
  },
];

export default function TasksPage() {
  const [tasks, setTasks] = useState<CrmTask[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      return saved ? JSON.parse(saved) : DEFAULT_TASKS;
    } catch {
      return DEFAULT_TASKS;
    }
  });

  const [leadsList, setLeadsList] = useState<Lead[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [filterStatus, setFilterStatus] = useState<"ALL" | "PENDING" | "COMPLETED">("ALL");
  const [filterPriority, setFilterPriority] = useState<string>("ALL");
  const [isModalOpen, setIsModalOpen] = useState(false);

  // Form state
  const [formData, setFormData] = useState({
    title: "",
    leadId: "",
    dueDate: new Date().toISOString().split("T")[0],
    priority: "MEDIA" as "ALTA" | "MEDIA" | "BAJA",
    type: "LLAMADA" as "LLAMADA" | "EMAIL" | "REUNION" | "PROPUESTA" | "OTRO",
  });
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  // Sync to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
    } catch {
      // Ignore storage errors
    }
  }, [tasks]);

  // Load leads for dropdown
  const loadLeads = useCallback(async () => {
    try {
      const res = await getLeadsApi({ page: 0 });
      if (res && res.content) {
        setLeadsList(res.content);
      }
    } catch {
      // Ignored
    }
  }, []);

  useEffect(() => {
    loadLeads();
  }, [loadLeads]);

  // Filtered tasks
  const filteredTasks = useMemo(() => {
    return tasks.filter((task) => {
      if (filterStatus === "PENDING" && task.completed) return false;
      if (filterStatus === "COMPLETED" && !task.completed) return false;
      if (filterPriority !== "ALL" && task.priority !== filterPriority) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesTitle = task.title.toLowerCase().includes(q);
        const matchesLead = task.leadName?.toLowerCase().includes(q);
        if (!matchesTitle && !matchesLead) return false;
      }
      return true;
    });
  }, [tasks, filterStatus, filterPriority, searchQuery]);

  // Summary counts
  const pendingCount = useMemo(() => tasks.filter((t) => !t.completed).length, [tasks]);
  const completedCount = useMemo(() => tasks.filter((t) => t.completed).length, [tasks]);
  const highPriorityCount = useMemo(
    () => tasks.filter((t) => !t.completed && t.priority === "ALTA").length,
    [tasks]
  );

  // Toggle complete
  const handleToggleTask = (taskId: string) => {
    setTasks((prev) =>
      prev.map((t) => {
        if (t.id === taskId) {
          const updated = !t.completed;
          if (updated) {
            sileo.success({
              title: "¡Tarea completada!",
              description: `"${t.title.slice(0, 40)}..." marcada como resuelta.`,
            });
          }
          return { ...t, completed: updated };
        }
        return t;
      })
    );
  };

  // Delete task
  const handleDeleteTask = (taskId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setTasks((prev) => prev.filter((t) => t.id !== taskId));
    sileo.info({
      title: "Tarea eliminada",
      description: "La tarea ha sido retirada de tu lista.",
    });
  };

  // Create Task Submit
  const handleCreateTask = (e: React.FormEvent) => {
    e.preventDefault();
    const errors: Record<string, string> = {};
    if (!formData.title.trim()) {
      errors.title = "El título o descripción de la tarea es requerido.";
    }
    if (!formData.dueDate) {
      errors.dueDate = "Selecciona una fecha límite.";
    }

    if (Object.keys(errors).length > 0) {
      setFormErrors(errors);
      return;
    }

    const selectedLead = leadsList.find((l) => String(l.id) === formData.leadId);

    const newTask: CrmTask = {
      id: `task-${Date.now()}`,
      title: formData.title.trim(),
      leadId: selectedLead ? selectedLead.id : undefined,
      leadName: selectedLead ? selectedLead.name : undefined,
      dueDate: formData.dueDate,
      priority: formData.priority,
      type: formData.type,
      completed: false,
      createdAt: new Date().toISOString(),
    };

    setTasks((prev) => [newTask, ...prev]);
    setIsModalOpen(false);
    setFormData({
      title: "",
      leadId: "",
      dueDate: new Date().toISOString().split("T")[0],
      priority: "MEDIA",
      type: "LLAMADA",
    });
    setFormErrors({});

    sileo.success({
      title: "Tarea creada",
      description: "Tu nueva tarea comercial está activa.",
    });
  };

  const getPriorityBadge = (priority: string) => {
    switch (priority) {
      case "ALTA":
        return "bg-rose-500/10 text-rose-700 border-rose-500/20";
      case "MEDIA":
        return "bg-amber-500/10 text-amber-700 border-amber-500/20";
      case "BAJA":
        return "bg-sky-500/10 text-sky-700 border-sky-500/20";
      default:
        return "bg-secondary text-muted-foreground";
    }
  };

  const getTypeIcon = (type: string) => {
    switch (type) {
      case "LLAMADA":
        return <Phone className="h-3.5 w-3.5 text-sky-500" />;
      case "EMAIL":
        return <Mail className="h-3.5 w-3.5 text-indigo-500" />;
      case "REUNION":
        return <Calendar className="h-3.5 w-3.5 text-emerald-500" />;
      case "PROPUESTA":
        return <FileText className="h-3.5 w-3.5 text-purple-500" />;
      default:
        return <CheckSquare className="h-3.5 w-3.5 text-muted-foreground" />;
    }
  };

  return (
    <div className="w-full max-w-full space-y-6 animate-in fade-in duration-300 pb-16 md:pb-4">
      {/* ── Header ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
              Tareas Comerciales
            </h1>
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-secondary border border-border text-foreground">
              <span className="w-2 h-2 rounded-full bg-accent" />
              {pendingCount} pendientes
            </span>
          </div>
          <p className="text-sm text-muted-foreground mt-1 font-medium">
            Planifica llamadas, cotizaciones y recordatorios comerciales
          </p>
        </div>

        <button
          type="button"
          onClick={() => setIsModalOpen(true)}
          className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 shadow-xs hover:bg-primary/90 transition-all cursor-pointer self-start sm:self-auto active:scale-95"
        >
          <Plus className="h-4 w-4 stroke-[2.5]" />
          <span>Nueva Tarea</span>
        </button>
      </div>

      {/* ── KPI Bubbles ── */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-4 sm:p-5 rounded-2xl bg-secondary/30 border border-border/80 flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Pendientes
            </p>
            <p className="text-2xl sm:text-3xl font-bold text-foreground mt-1">
              {pendingCount}
            </p>
          </div>
          <div className="h-10 w-10 rounded-full bg-amber-500/10 text-amber-600 flex items-center justify-center font-bold">
            <Clock className="h-5 w-5" />
          </div>
        </div>

        <div className="p-4 sm:p-5 rounded-2xl bg-secondary/30 border border-border/80 flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Prioridad Alta
            </p>
            <p className="text-2xl sm:text-3xl font-bold text-rose-600 mt-1">
              {highPriorityCount}
            </p>
          </div>
          <div className="h-10 w-10 rounded-full bg-rose-500/10 text-rose-600 flex items-center justify-center font-bold">
            <AlertCircle className="h-5 w-5" />
          </div>
        </div>

        <div className="p-4 sm:p-5 rounded-2xl bg-secondary/30 border border-border/80 flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Completadas
            </p>
            <p className="text-2xl sm:text-3xl font-bold text-emerald-600 mt-1">
              {completedCount}
            </p>
          </div>
          <div className="h-10 w-10 rounded-full bg-emerald-500/10 text-emerald-600 flex items-center justify-center font-bold">
            <Check className="h-5 w-5 stroke-[2.5]" />
          </div>
        </div>
      </div>

      {/* ── Filter & Search Bar ── */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 p-3 rounded-2xl bg-secondary/35 border border-border">
        {/* Search */}
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Buscar por tarea o prospecto..."
            className="w-full h-10 pl-10 pr-9 rounded-full bg-background border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium placeholder:text-muted-foreground"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-muted-foreground hover:text-foreground rounded-full"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        {/* Status Filters */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
          <button
            type="button"
            onClick={() => setFilterStatus("ALL")}
            className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
              filterStatus === "ALL"
                ? "bg-foreground text-background shadow-xs"
                : "bg-background border border-border text-muted-foreground hover:text-foreground"
            }`}
          >
            Todas ({tasks.length})
          </button>
          <button
            type="button"
            onClick={() => setFilterStatus("PENDING")}
            className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
              filterStatus === "PENDING"
                ? "bg-foreground text-background shadow-xs"
                : "bg-background border border-border text-muted-foreground hover:text-foreground"
            }`}
          >
            Pendientes ({pendingCount})
          </button>
          <button
            type="button"
            onClick={() => setFilterStatus("COMPLETED")}
            className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
              filterStatus === "COMPLETED"
                ? "bg-foreground text-background shadow-xs"
                : "bg-background border border-border text-muted-foreground hover:text-foreground"
            }`}
          >
            Completadas ({completedCount})
          </button>
        </div>
      </div>

      {/* ── Tasks List ── */}
      <div className="space-y-3">
        {filteredTasks.length === 0 ? (
          <div className="p-12 text-center rounded-3xl border border-dashed border-border bg-secondary/20">
            <CheckSquare className="h-10 w-10 text-muted-foreground/60 mx-auto mb-3" />
            <h3 className="text-base font-semibold text-foreground">
              No hay tareas en esta vista
            </h3>
            <p className="text-xs sm:text-sm text-muted-foreground mt-1 max-w-sm mx-auto">
              Todo está al día o no coincide con los filtros seleccionados.
            </p>
          </div>
        ) : (
          filteredTasks.map((task) => (
            <div
              key={task.id}
              onClick={() => handleToggleTask(task.id)}
              className={`p-4 sm:p-5 rounded-2xl border transition-all cursor-pointer flex items-start gap-4 group ${
                task.completed
                  ? "bg-secondary/20 border-border/60 opacity-60"
                  : "bg-background border-border hover:border-accent/40 shadow-2xs hover:shadow-xs"
              }`}
            >
              {/* Checkbox Bubble */}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleToggleTask(task.id);
                }}
                className={`mt-0.5 h-6 w-6 rounded-full border flex items-center justify-center transition-all shrink-0 cursor-pointer ${
                  task.completed
                    ? "bg-emerald-500 border-emerald-500 text-white"
                    : "border-border bg-secondary/50 group-hover:border-accent"
                }`}
              >
                {task.completed && <Check className="h-3.5 w-3.5 stroke-[3]" />}
              </button>

              {/* Task Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2.5 flex-wrap">
                  <span
                    className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${getPriorityBadge(
                      task.priority
                    )}`}
                  >
                    {task.priority}
                  </span>
                  <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-medium bg-secondary text-muted-foreground border border-border">
                    {getTypeIcon(task.type)}
                    <span className="capitalize">{task.type.toLowerCase()}</span>
                  </span>
                  {task.leadName && (
                    <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-accent">
                      <User className="h-3 w-3" />
                      {task.leadName}
                    </span>
                  )}
                </div>

                <p
                  className={`mt-2 text-sm sm:text-base font-medium leading-snug ${
                    task.completed
                      ? "line-through text-muted-foreground"
                      : "text-foreground"
                  }`}
                >
                  {task.title}
                </p>

                <div className="flex items-center gap-4 mt-2.5 text-xs text-muted-foreground font-medium">
                  <div className="flex items-center gap-1.5">
                    <Calendar className="h-3.5 w-3.5" />
                    <span>Límite: {task.dueDate}</span>
                  </div>
                </div>
              </div>

              {/* Delete Action */}
              <button
                type="button"
                onClick={(e) => handleDeleteTask(task.id, e)}
                title="Eliminar tarea"
                className="p-2 rounded-full text-muted-foreground hover:text-rose-500 hover:bg-rose-500/10 transition-colors opacity-0 group-hover:opacity-100 cursor-pointer shrink-0"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))
        )}
      </div>

      {/* ── Modal Nueva Tarea ── */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div
            className="w-full max-w-lg bg-background border border-border rounded-3xl shadow-xl overflow-hidden p-6 sm:p-7 space-y-5"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3.5 border-b border-border">
              <div className="flex items-center gap-2.5">
                <div className="h-8 w-8 rounded-full bg-accent text-accent-foreground flex items-center justify-center font-bold text-xs shadow-2xs">
                  <CheckSquare className="h-4 w-4" />
                </div>
                <div>
                  <h3 className="text-base sm:text-lg font-bold text-foreground">
                    Nueva Tarea Comercial
                  </h3>
                  <p className="text-xs text-muted-foreground font-medium">
                    Asigna seguimiento o compromisos con prospectos
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                className="h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleCreateTask} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                  Descripción de la tarea *
                </label>
                <textarea
                  rows={3}
                  required
                  placeholder="Ej. Enviar cotización actualizada de servicios IT..."
                  value={formData.title}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, title: e.target.value }))
                  }
                  className="w-full p-3 rounded-2xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium resize-none"
                />
                {formErrors.title && (
                  <p className="text-[11px] text-rose-500 mt-1 flex items-center gap-1 font-semibold">
                    <AlertCircle className="h-3 w-3" />
                    <span>{formErrors.title}</span>
                  </p>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Asociar a Lead (Opcional)
                  </label>
                  <select
                    value={formData.leadId}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, leadId: e.target.value }))
                    }
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  >
                    <option value="">Sin asociar (General)</option>
                    {leadsList.map((lead) => (
                      <option key={lead.id} value={lead.id}>
                        {lead.name} ({lead.stage})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Fecha límite *
                  </label>
                  <input
                    type="date"
                    required
                    value={formData.dueDate}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, dueDate: e.target.value }))
                    }
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Tipo de actividad
                  </label>
                  <select
                    value={formData.type}
                    onChange={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        type: e.target.value as any,
                      }))
                    }
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  >
                    <option value="LLAMADA">Llamada telefónica</option>
                    <option value="EMAIL">Correo electrónico</option>
                    <option value="REUNION">Reunión / Cita</option>
                    <option value="PROPUESTA">Cotización / Propuesta</option>
                    <option value="OTRO">Otro compromiso</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5 uppercase tracking-wider">
                    Prioridad
                  </label>
                  <select
                    value={formData.priority}
                    onChange={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        priority: e.target.value as any,
                      }))
                    }
                    className="w-full h-10 px-3.5 rounded-xl bg-secondary/50 border border-border text-xs sm:text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-accent/40 font-medium"
                  >
                    <option value="ALTA">Alta (Urgente)</option>
                    <option value="MEDIA">Media (Normal)</option>
                    <option value="BAJA">Baja</option>
                  </select>
                </div>
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-3.5 border-t border-border">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="h-10 px-5 rounded-full bg-secondary text-foreground font-semibold text-xs sm:text-sm hover:bg-secondary/80 transition-colors cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="h-10 px-5 rounded-full bg-primary text-primary-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 hover:bg-primary/90 transition-all cursor-pointer shadow-xs active:scale-95"
                >
                  <Plus className="h-4 w-4 stroke-[2.5]" />
                  <span>Guardar Tarea</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
