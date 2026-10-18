import React, { useState, useEffect, useCallback } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  Phone,
  Mail,
  Calendar,
  Briefcase,
  User,
  Trash2,
  Pencil,
  Check,
  RefreshCw,
  AlertCircle,
  MessageSquare,
  Lock,
  Clock,
  Sparkles,
  X,
  ExternalLink,
} from "lucide-react";
import { sileo } from "sileo";
import {
  getLeadByIdApi,
  updateLeadApi,
  deleteLeadApi,
  Lead,
  LeadStage,
  UpdateLeadPayload,
  LeadApiError,
} from "../services/leads";
import {
  getNotesByLeadIdApi,
  createNoteApi,
  LeadNoteResponse,
} from "../services/notes";
import { GlassSelect, GlassSelectOption } from "../components/ui/GlassSelect";
import { GlassDatePicker } from "../components/ui/GlassDatePicker";
import { useAuth } from "../context/AuthContext";

export interface LeadNoteItem {
  id: number;
  content: string;
  author: string;
  createdAt: string;
}

const STAGE_OPTIONS: GlassSelectOption[] = [
  {
    value: "NUEVO",
    label: "NUEVO",
    description: "Prospecto recién registrado",
    colorDot: "bg-sky-400",
  },
  {
    value: "EN_SEGUIMIENTO",
    label: "EN SEGUIMIENTO",
    description: "Contacto activo y negociación",
    colorDot: "bg-amber-400",
  },
  {
    value: "CERRADO",
    label: "CERRADO",
    description: "Trato comercial concretado",
    colorDot: "bg-emerald-400",
  },
];

/**
 * Formats a LocalDate string (YYYY-MM-DD) into Peruvian Spanish format without time.
 */
function formatFollowUpDate(dateStr: string | null | undefined): string {
  if (!dateStr) return "Sin fecha programada";
  try {
    const [year, month, day] = dateStr.split("-").map(Number);
    if (!year || !month || !day) return dateStr;
    const date = new Date(year, month - 1, day);
    return date.toLocaleDateString("es-PE", {
      day: "numeric",
      month: "long",
      year: "numeric",
    });
  } catch {
    return dateStr;
  }
}

/**
 * Formats an ISO OffsetDateTime into Peruvian Spanish format with date and time.
 */
function formatDateTime(isoStr: string | null | undefined): string {
  if (!isoStr) return "No registrada";
  try {
    const date = new Date(isoStr);
    return date.toLocaleDateString("es-PE", {
      day: "numeric",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return isoStr;
  }
}

export default function LeadDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  // Lead data states
  const [lead, setLead] = useState<Lead | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<LeadApiError | null>(null);

  // Edit modal states
  const [isEditModalOpen, setIsEditModalOpen] = useState<boolean>(false);
  const [formData, setFormData] = useState<UpdateLeadPayload>({
    name: "",
    phone: "",
    email: "",
    stage: "NUEVO",
    nextFollowUp: "",
    service: "",
  });
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isStageSelectOpen, setIsStageSelectOpen] = useState<boolean>(false);
  const [isDatePickerOpen, setIsDatePickerOpen] = useState<boolean>(false);

  // Delete modal state
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState<boolean>(false);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);

  // User and internal notes state
  const { user } = useAuth();
  const [noteContent, setNoteContent] = useState<string>("");
  const [notes, setNotes] = useState<LeadNoteResponse[]>([]);
  const [isLoadingNotes, setIsLoadingNotes] = useState<boolean>(true);
  const [notesError, setNotesError] = useState<string | null>(null);
  const [noteSubmitError, setNoteSubmitError] = useState<string | null>(null);
  const [isSubmittingNote, setIsSubmittingNote] = useState<boolean>(false);

  // Fetch real internal notes from backend API
  const fetchNotes = useCallback(async () => {
    if (!id) return;
    setIsLoadingNotes(true);
    setNotesError(null);
    try {
      const data = await getNotesByLeadIdApi(id);
      setNotes(data);
    } catch (err: any) {
      setNotesError(err.message || "Error al cargar las notas del lead.");
    } finally {
      setIsLoadingNotes(false);
    }
  }, [id]);

  useEffect(() => {
    fetchNotes();
  }, [fetchNotes]);

  const handleAddNote = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = noteContent.trim();
    if (!trimmed) {
      sileo.warning({
        title: "Nota vacía",
        description: "Escribe un comentario antes de registrar la actualización.",
      });
      return;
    }

    if (trimmed.length > 2000) {
      sileo.warning({
        title: "Límite excedido",
        description: "El contenido de la nota no puede superar los 2.000 caracteres.",
      });
      return;
    }

    if (!id) return;

    setIsSubmittingNote(true);
    setNoteSubmitError(null);

    try {
      const createdNote = await createNoteApi(id, trimmed);
      setNotes((prev) => [...prev, createdNote]);
      setNoteContent("");
      setNoteSubmitError(null);
      sileo.success({
        title: "Nota registrada",
        description: "La actualización interna fue agregada a la bitácora.",
      });
    } catch (err: any) {
      // Preserve user typed text on failure for retry
      const errMsg = err.message || "No se pudo registrar la nota interna en el servidor.";
      setNoteSubmitError(errMsg);
      sileo.error({
        title: "Error al guardar nota",
        description: errMsg,
      });
    } finally {
      setIsSubmittingNote(false);
    }
  };


  // Fetch lead data from real backend endpoint
  const fetchLead = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await getLeadByIdApi(id);
      setLead(data);
    } catch (err: any) {
      const apiErr: LeadApiError = err.message
        ? (err as LeadApiError)
        : {
            status: 500,
            message: "Error al comunicarse con el servidor.",
          };
      setError(apiErr);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchLead();
  }, [fetchLead]);

  // Handle ESC key to close open modals
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (isDeleteModalOpen) setIsDeleteModalOpen(false);
        else if (isEditModalOpen) handleCloseEditModal();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isDeleteModalOpen, isEditModalOpen]);

  // Stage badge renderer
  const renderStageBadge = (stage: LeadStage | string) => {
    switch (stage) {
      case "NUEVO":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-sky-500/10 text-sky-700 border border-sky-500/20">
            <span className="w-1.5 h-1.5 rounded-full bg-sky-500 animate-pulse" />
            Nuevo
          </span>
        );
      case "EN_SEGUIMIENTO":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-500/10 text-amber-700 border border-amber-500/20">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
            En seguimiento
          </span>
        );
      case "CERRADO":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            Cerrado
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-secondary text-secondary-foreground border border-border">
            {stage}
          </span>
        );
    }
  };

  // Open Edit Modal handler
  const handleOpenEditModal = () => {
    if (!lead) return;
    setFormData({
      name: lead.name,
      phone: lead.phone || "",
      email: lead.email || "",
      stage: lead.stage,
      nextFollowUp: lead.nextFollowUp || "",
      service: lead.service || "",
    });
    setFormErrors({});
    setIsStageSelectOpen(false);
    setIsDatePickerOpen(false);
    setIsEditModalOpen(true);
  };

  const handleCloseEditModal = () => {
    setIsEditModalOpen(false);
    setIsStageSelectOpen(false);
    setIsDatePickerOpen(false);
    setFormErrors({});
  };

  const handleInputChange = (field: keyof UpdateLeadPayload, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (formErrors[field]) {
      setFormErrors((prev) => {
        const updated = { ...prev };
        delete updated[field];
        return updated;
      });
    }
  };

  // Submit Edit form
  const handleSubmitEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!lead) return;
    setFormErrors({});

    const trimmedName = formData.name.trim();
    const trimmedPhone = formData.phone?.trim() || "";
    const trimmedEmail = formData.email?.trim() || "";

    const localErrors: Record<string, string> = {};
    if (!trimmedName || trimmedName.length < 2) {
      localErrors.name = "El nombre debe tener al menos 2 caracteres.";
    }
    if (!trimmedPhone && !trimmedEmail) {
      localErrors.phone = "Ingresa al menos un teléfono o correo.";
      localErrors.email = "Ingresa al menos un teléfono o correo.";
    }

    if (Object.keys(localErrors).length > 0) {
      setFormErrors(localErrors);
      sileo.warning({
        title: "Campos requeridos",
        description: "Revisa los campos indicados antes de continuar.",
      });
      return;
    }

    setIsSubmitting(true);
    try {
      const payload: UpdateLeadPayload = {
        name: trimmedName,
        phone: trimmedPhone || null,
        email: trimmedEmail || null,
        stage: formData.stage,
        nextFollowUp:
          formData.stage === "CERRADO" ? null : formData.nextFollowUp || null,
        service: formData.service?.trim() || null,
      };

      const updated = await updateLeadApi(lead.id, payload);
      setLead(updated);
      setIsEditModalOpen(false);
      sileo.success({
        title: "¡Lead actualizado exitosamente!",
        description: `Los cambios para "${trimmedName}" han sido guardados.`,
      });
    } catch (err: any) {
      const apiErr = err as LeadApiError;
      if (apiErr.fieldErrors && Object.keys(apiErr.fieldErrors).length > 0) {
        setFormErrors(apiErr.fieldErrors);
      }
      sileo.error({
        title: "Error al actualizar lead",
        description: apiErr.message || "No se pudieron guardar los cambios.",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Confirm delete lead
  const handleConfirmDelete = async () => {
    if (!lead || isDeleting) return;
    const deletedName = lead.name;
    setIsDeleting(true);
    try {
      await deleteLeadApi(lead.id);
      sileo.success({
        title: "Lead descartado exitosamente",
        description: `El lead "${deletedName}" fue eliminado del pipeline.`,
      });
      navigate("/app/leads", { replace: true });
    } catch (err: any) {
      const errorMsg = err.message || "No se pudo descartar el lead.";
      sileo.error({
        title: "Error al descartar lead",
        description: errorMsg,
      });
      setIsDeleteModalOpen(false);
    } finally {
      setIsDeleting(false);
    }
  };

  // Loading view
  if (isLoading) {
    return (
      <div className="w-full space-y-6 animate-in fade-in duration-300">
        <div className="flex items-center gap-3">
          <Link
            to="/app/leads"
            className="liquid-glass-btn-secondary h-10 px-3.5 rounded-xl text-xs font-medium inline-flex items-center gap-2 text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Volver a Leads</span>
          </Link>
        </div>
        <div className="liquid-glass-card rounded-3xl p-16 text-center">
          <div className="w-14 h-14 mx-auto mb-4 rounded-2xl bg-secondary border border-border flex items-center justify-center text-primary animate-spin">
            <RefreshCw className="h-6 w-6" />
          </div>
          <p className="text-foreground font-semibold text-lg">Cargando detalles del lead...</p>
          <p className="text-muted-foreground text-sm mt-1">
            Consultando la información comercial en tiempo real
          </p>
        </div>
      </div>
    );
  }

  // Error view
  if (error || !lead) {
    const isNotFound = error?.status === 404;
    return (
      <div className="w-full space-y-6 animate-in fade-in duration-300">
        <div className="flex items-center gap-3">
          <Link
            to="/app/leads"
            className="liquid-glass-btn-secondary h-10 px-3.5 rounded-xl text-xs font-medium inline-flex items-center gap-2 text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>Volver a Leads</span>
          </Link>
        </div>
        <div className="liquid-glass-card rounded-3xl p-12 sm:p-16 text-center flex flex-col items-center justify-center gap-4">
          <div className="w-20 h-20 rounded-full bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-500">
            <AlertCircle className="h-10 w-10" />
          </div>
          <h2 className="text-2xl font-bold text-foreground">
            {isNotFound ? "Lead no encontrado" : "Error al sincronizar lead"}
          </h2>
          <p className="text-base text-muted-foreground max-w-md mx-auto leading-relaxed">
            {isNotFound
              ? `El lead con identificador #${id} no existe o fue eliminado previamente.`
              : error?.message || "Ocurrió un error al cargar la información del prospecto."}
          </p>
          <div className="flex items-center gap-3 pt-2">
            <Link
              to="/app/leads"
              className="aurora-chip-btn h-10 px-6 text-sm font-medium inline-flex items-center gap-2"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Ir a la lista de leads</span>
            </Link>
            {!isNotFound && (
              <button
                type="button"
                onClick={fetchLead}
                className="liquid-glass-btn-secondary h-10 px-5 rounded-xl text-sm font-medium inline-flex items-center gap-2 cursor-pointer text-foreground"
              >
                <RefreshCw className="h-4 w-4" />
                <span>Reintentar</span>
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full max-w-full space-y-6 animate-in fade-in duration-500">
      {/* ── Top Bar Navigation & Actions ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <Link
            to="/app/leads"
            aria-label="Volver al listado de leads"
            className="liquid-glass-btn-secondary h-10 px-3.5 rounded-xl text-xs font-medium inline-flex items-center gap-2 active:scale-95 transition-all text-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4 text-muted-foreground" />
            <span>Volver a Leads</span>
          </Link>
          <span className="text-muted-foreground/40 text-sm hidden sm:inline">/</span>
          <span className="text-muted-foreground text-xs sm:text-sm truncate max-w-[200px] hidden sm:inline">
            {lead.name}
          </span>
        </div>

        {/* Action Buttons: Editar & Descartar */}
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleOpenEditModal}
            className="liquid-glass-btn-secondary h-10 px-4 rounded-xl text-xs font-medium inline-flex items-center gap-2 active:scale-95 cursor-pointer text-foreground hover:text-foreground"
            title="Editar información del lead"
          >
            <Pencil className="h-3.5 w-3.5 text-muted-foreground" />
            <span>Editar Lead</span>
          </button>

          <button
            type="button"
            onClick={() => setIsDeleteModalOpen(true)}
            className="liquid-glass-btn-danger h-10 px-4 rounded-xl text-xs font-medium inline-flex items-center gap-2 active:scale-95 cursor-pointer"
            title="Descartar este lead"
          >
            <Trash2 className="h-3.5 w-3.5" />
            <span>Descartar</span>
          </button>
        </div>
      </div>

      {/* ── Header Summary Card ── */}
      <div className="liquid-glass-card rounded-2xl p-6 sm:p-8 space-y-6">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-border">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
                {lead.name}
              </h1>
              {renderStageBadge(lead.stage)}
            </div>
            {lead.service ? (
              <p className="text-sm text-muted-foreground flex items-center gap-2">
                <Briefcase className="h-4 w-4 text-muted-foreground shrink-0" />
                <span>Interés en: <strong className="font-semibold text-foreground">{lead.service}</strong></span>
              </p>
            ) : (
              <p className="text-sm text-muted-foreground/60 italic">
                Sin servicio específico asignado
              </p>
            )}
          </div>

          {/* Quick Communication Actions */}
          <div className="flex flex-wrap items-center gap-2.5">
            {lead.phone && (
              <a
                href={`tel:${lead.phone}`}
                className="aurora-chip-btn h-10 px-4 text-xs font-medium inline-flex items-center gap-2"
                title={`Llamar a ${lead.phone}`}
              >
                <Phone className="h-3.5 w-3.5" />
                <span>Llamar ({lead.phone})</span>
              </a>
            )}
            {lead.email && (
              <a
                href={`mailto:${lead.email}`}
                className="liquid-glass-btn-secondary h-10 px-4 rounded-xl text-xs font-medium inline-flex items-center gap-2 text-foreground"
                title={`Enviar correo a ${lead.email}`}
              >
                <Mail className="h-3.5 w-3.5 text-sky-600" />
                <span>Enviar correo</span>
              </a>
            )}
          </div>
        </div>

        {/* Summary Highlights Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 pt-1">
          {/* Item 1: Etapa Comercial */}
          <div className="p-4 rounded-xl bg-secondary/35 border border-border space-y-1">
            <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider block">
              Etapa Comercial
            </span>
            <div className="text-sm font-semibold text-foreground flex items-center gap-2 pt-0.5">
              <span className="w-2 h-2 rounded-full bg-primary" />
              <span>{lead.stage.replace("_", " ")}</span>
            </div>
          </div>

          {/* Item 2: Próximo Seguimiento (sin inventar hora) */}
          <div className="p-4 rounded-xl bg-secondary/35 border border-border space-y-1">
            <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider block">
              Próximo Seguimiento
            </span>
            <div className="text-sm font-semibold text-foreground flex items-center gap-2 pt-0.5">
              <Calendar className="h-4 w-4 text-muted-foreground shrink-0" />
              <span>{formatFollowUpDate(lead.nextFollowUp)}</span>
            </div>
          </div>

          {/* Item 3: Teléfono de contacto */}
          <div className="p-4 rounded-xl bg-secondary/35 border border-border space-y-1">
            <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider block">
              Teléfono Directo
            </span>
            <div className="text-sm font-semibold text-foreground flex items-center gap-2 pt-0.5 truncate">
              <Phone className="h-4 w-4 text-emerald-600 shrink-0" />
              {lead.phone ? (
                <a href={`tel:${lead.phone}`} className="hover:underline truncate">
                  {lead.phone}
                </a>
              ) : (
                <span className="text-muted-foreground/60 italic font-normal">No registrado</span>
              )}
            </div>
          </div>

          {/* Item 4: Correo Electrónico */}
          <div className="p-4 rounded-xl bg-secondary/35 border border-border space-y-1">
            <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider block">
              Correo Electrónico
            </span>
            <div className="text-sm font-semibold text-foreground flex items-center gap-2 pt-0.5 truncate">
              <Mail className="h-4 w-4 text-sky-600 shrink-0" />
              {lead.email ? (
                <a href={`mailto:${lead.email}`} className="hover:underline truncate" title={lead.email}>
                  {lead.email}
                </a>
              ) : (
                <span className="text-muted-foreground/60 italic font-normal">No registrado</span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ── Two Columns: Bitácora & Internal Notes + Complete Lead Profile ── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column (2 cols): Bitácora e Historial */}
        <div className="lg:col-span-2 space-y-6">
          <div className="liquid-glass-card rounded-2xl p-6 sm:p-7 space-y-6">
            {/* Bitácora Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-border">
              <div className="space-y-1">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-primary/10 border border-primary/20 flex items-center justify-center text-primary">
                    <MessageSquare className="h-4 w-4" />
                  </div>
                  <h2 className="text-lg font-semibold text-foreground">
                    Historial y Notas Internas
                  </h2>
                </div>
                <p className="text-xs text-muted-foreground">
                  Bitácora cronológica de acuerdos, conversaciones y avances comerciales.
                </p>
              </div>

              <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-secondary text-muted-foreground border border-border shrink-0 self-start sm:self-auto">
                <Lock className="h-3 w-3 text-muted-foreground" />
                <span>Solo uso interno</span>
              </div>
            </div>

            {/* Note Editor Area */}
            <div className="space-y-3 p-4 sm:p-5 rounded-2xl bg-secondary/25 border border-border">
              <div className="flex items-center justify-between">
                <label htmlFor="lead-note-input" className="text-xs font-semibold text-foreground uppercase tracking-wider block">
                  Registrar actualización interna
                </label>
                <span
                  className={`text-[11px] font-mono transition-colors ${
                    noteContent.length > 2000
                      ? "text-rose-500 font-bold"
                      : noteContent.length > 1800
                      ? "text-amber-500 font-medium"
                      : "text-muted-foreground"
                  }`}
                  aria-live="polite"
                >
                  {noteContent.length} / 2.000
                </span>
              </div>

              <textarea
                id="lead-note-input"
                rows={3}
                value={noteContent}
                maxLength={2100}
                onChange={(e) => {
                  setNoteContent(e.target.value);
                  if (noteSubmitError) setNoteSubmitError(null);
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    handleAddNote();
                  }
                }}
                placeholder="Escribe una actualización sobre qué conversaron, qué pidió el contacto o próximos acuerdos..."
                disabled={isSubmittingNote}
                className={`w-full p-3.5 rounded-xl bg-white border text-sm text-foreground placeholder:text-muted-foreground resize-none cursor-text focus:outline-none transition-all duration-200 ${
                  noteContent.length > 2000
                    ? "border-rose-400 focus:border-rose-500 focus:ring-2 focus:ring-rose-400/20"
                    : "border-border focus:border-primary focus:ring-2 focus:ring-primary/20"
                }`}
              />

              {noteContent.length > 2000 && (
                <p className="text-xs text-rose-500 flex items-center gap-1.5 font-medium">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  <span>El contenido de la nota no puede superar los 2.000 caracteres.</span>
                </p>
              )}

              {/* Inline Submission Error with Retry */}
              {noteSubmitError && (
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-xs text-rose-600">
                  <div className="flex items-center gap-2">
                    <AlertCircle className="h-4 w-4 shrink-0 text-rose-500" />
                    <span>{noteSubmitError}</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleAddNote()}
                    disabled={isSubmittingNote}
                    className="liquid-glass-btn-secondary px-3 py-1 rounded-lg text-xs font-medium cursor-pointer text-foreground hover:text-rose-600 shrink-0 self-end sm:self-auto"
                  >
                    Reintentar
                  </button>
                </div>
              )}

              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1">
                <span className="text-[11px] text-muted-foreground">
                  Tip: Presiona <kbd className="px-1.5 py-0.5 rounded bg-secondary border border-border text-foreground text-[10px] font-mono">Ctrl + Enter</kbd> o haz clic para guardar.
                </span>
                <button
                  type="button"
                  onClick={() => handleAddNote()}
                  disabled={
                    isSubmittingNote ||
                    !noteContent.trim() ||
                    noteContent.trim().length > 2000
                  }
                  className="aurora-chip-btn h-9 px-5 text-xs font-medium self-end sm:self-auto disabled:opacity-40 disabled:cursor-not-allowed transition-all flex items-center gap-1.5 cursor-pointer"
                >
                  {isSubmittingNote ? (
                    <>
                      <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                      <span>Guardando...</span>
                    </>
                  ) : (
                    <span>Agregar nota</span>
                  )}
                </button>
              </div>
            </div>

            {/* Timeline Notes Structure (Empty State & Visual Flow) */}
            <div className="space-y-4 pt-2">
              <div className="flex items-center justify-between text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                <span>Línea de tiempo</span>
                <span>{notes.length} {notes.length === 1 ? "nota" : "notas"}</span>
              </div>

              {isLoadingNotes ? (
                <div className="p-8 rounded-2xl bg-secondary/20 border border-border text-center flex flex-col items-center justify-center gap-3">
                  <RefreshCw className="h-6 w-6 text-primary animate-spin" />
                  <p className="text-xs text-muted-foreground">Cargando notas internas...</p>
                </div>
              ) : notesError ? (
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-xs text-rose-600">
                  <div className="flex items-center gap-2">
                    <AlertCircle className="h-4 w-4 shrink-0 text-rose-500" />
                    <span>{notesError}</span>
                  </div>
                  <button
                    type="button"
                    onClick={fetchNotes}
                    className="liquid-glass-btn-secondary px-3 py-1.5 rounded-lg text-xs font-medium cursor-pointer text-foreground hover:text-rose-600 shrink-0 self-end sm:self-auto"
                  >
                    Reintentar
                  </button>
                </div>
              ) : notes.length === 0 ? (
                /* Empty state for Notes */
                <div className="p-8 sm:p-10 rounded-2xl bg-secondary/20 border border-border text-center flex flex-col items-center justify-center gap-3">
                  <div className="w-14 h-14 rounded-2xl bg-secondary border border-border flex items-center justify-center text-muted-foreground">
                    <Clock className="h-6 w-6 text-muted-foreground" />
                  </div>
                  <h3 className="text-base font-semibold text-foreground">
                    Aún no hay notas registradas
                  </h3>
                  <p className="text-xs sm:text-sm text-muted-foreground max-w-md mx-auto leading-relaxed">
                    Las notas internas permiten guardar un historial confidencial de llamadas,
                    reuniones y acuerdos sin enviar notificaciones al cliente.
                  </p>
                </div>
              ) : (
                <div className="space-y-3 relative before:absolute before:inset-0 before:left-3 before:w-0.5 before:bg-border">
                  {notes.map((n) => (
                    <div key={n.id} className="relative pl-7 group transition-all">
                      <div className="absolute left-1.5 top-3.5 w-3 h-3 rounded-full bg-emerald-500 border-2 border-white shadow-xs" />
                      <div className="p-4 rounded-xl bg-secondary/35 hover:bg-secondary/50 border border-border transition-colors space-y-2">
                        <div className="flex items-center justify-between gap-2 text-xs">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-foreground">
                              {user?.username || "Usuario"}
                            </span>
                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-700 border border-emerald-500/20 font-medium">
                              Interno
                            </span>
                          </div>
                          <span className="text-[11px] text-muted-foreground">
                            {formatDateTime(n.createdAt)}
                          </span>
                        </div>
                        <p className="text-xs sm:text-sm text-foreground whitespace-pre-wrap break-words leading-relaxed">
                          {n.content}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

          </div>
        </div>

        {/* Right Column (1 col): Ficha Técnica de Datos */}
        <div className="space-y-6">
          <div className="liquid-glass-card rounded-2xl p-6 sm:p-7 space-y-6">
            <div className="flex items-center gap-2.5 pb-4 border-b border-border">
              <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-600">
                <User className="h-4 w-4" />
              </div>
              <h2 className="text-lg font-semibold text-foreground">
                Ficha del Prospecto
              </h2>
            </div>

            <div className="space-y-4 text-xs">
              {/* Field: Nombre */}
              <div className="space-y-1">
                <span className="text-muted-foreground font-medium">Nombre Completo</span>
                <p className="text-sm font-semibold text-foreground">{lead.name}</p>
              </div>

              {/* Field: Etapa */}
              <div className="space-y-1 pt-2 border-t border-border">
                <span className="text-muted-foreground font-medium">Etapa Comercial Actual</span>
                <div className="pt-1">{renderStageBadge(lead.stage)}</div>
              </div>

              {/* Field: Próximo Seguimiento */}
              <div className="space-y-1 pt-2 border-t border-border">
                <span className="text-muted-foreground font-medium">Fecha de Seguimiento</span>
                <p className="text-sm font-semibold text-foreground">
                  {formatFollowUpDate(lead.nextFollowUp)}
                </p>
              </div>

              {/* Field: Servicio de Interés */}
              <div className="space-y-1 pt-2 border-t border-border">
                <span className="text-muted-foreground font-medium">Servicio Requerido</span>
                <p className="text-sm text-foreground">
                  {lead.service || <span className="text-muted-foreground/60 italic">Sin especificar</span>}
                </p>
              </div>

              {/* Field: Registro en el sistema */}
              <div className="space-y-1 pt-2 border-t border-border">
                <span className="text-muted-foreground font-medium">Fecha de Registro</span>
                <p className="text-xs text-muted-foreground">
                  {formatDateTime(lead.createdAt)}
                </p>
              </div>

              {/* Field: Última actualización */}
              <div className="space-y-1 pt-2 border-t border-border">
                <span className="text-muted-foreground font-medium">Última Modificación</span>
                <p className="text-xs text-muted-foreground">
                  {formatDateTime(lead.updatedAt)}
                </p>
              </div>
            </div>

            {/* Quick Action in Card */}
            <div className="pt-4 border-t border-border flex flex-col gap-2">
              <button
                type="button"
                onClick={handleOpenEditModal}
                className="w-full liquid-glass-btn-secondary h-10 rounded-xl text-xs font-medium flex items-center justify-center gap-2 cursor-pointer text-foreground hover:text-foreground"
              >
                <Pencil className="h-3.5 w-3.5 text-muted-foreground" />
                <span>Modificar datos</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Liquid Glass Modal: Editar Lead ── */}
      {isEditModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="detail-edit-lead-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/50 backdrop-blur-md animate-in fade-in duration-300"
          onClick={handleCloseEditModal}
        >
          <div
            className="liquid-glass-modal lead-modal-dialog relative overflow-hidden shadow-2xl text-white max-h-[92vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="lead-modal-header relative z-10">
              <div>
                <div className="lead-modal-badge shadow-sm">
                  <Pencil className="h-3.5 w-3.5 text-cyan-300" />
                  <span>Editar Prospecto</span>
                </div>
                <h2 id="detail-edit-lead-title" className="lead-modal-title">
                  Editar Lead
                </h2>
                <p className="lead-modal-subtitle">
                  Actualiza la información comercial y seguimiento de &ldquo;{lead.name}&rdquo;.
                </p>
              </div>

              <button
                type="button"
                onClick={handleCloseEditModal}
                disabled={isSubmitting}
                aria-label="Cerrar modal"
                className="lead-modal-close"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmitEdit} className="relative z-10" noValidate>
              {/* Field 1: Nombre completo */}
              <div className="lead-field-group">
                <label htmlFor="detail-lead-name">
                  Nombre completo <span className="text-white/60 font-normal">*</span>
                </label>
                <input
                  id="detail-lead-name"
                  type="text"
                  required
                  placeholder="Ej: Laura Mendoza"
                  value={formData.name}
                  onChange={(e) => handleInputChange("name", e.target.value)}
                  disabled={isSubmitting}
                  className={`liquid-glass-input ${
                    formErrors.name ? "border-red-400 ring-2 ring-red-400/30" : ""
                  }`}
                  aria-invalid={!!formErrors.name}
                />
                {formErrors.name && (
                  <p role="alert" className="lead-error-text">
                    {formErrors.name}
                  </p>
                )}
              </div>

              {/* Field 2: Etapa comercial y Próximo seguimiento */}
              <div className="lead-field-grid">
                <div className="lead-field-group">
                  <label htmlFor="detail-lead-stage">
                    Etapa comercial <span className="text-white/60 font-normal">*</span>
                  </label>
                  <GlassSelect
                    id="detail-lead-stage"
                    value={formData.stage}
                    onChange={(val) =>
                      handleInputChange("stage", val as LeadStage)
                    }
                    options={STAGE_OPTIONS}
                    disabled={isSubmitting}
                    onOpenChange={setIsStageSelectOpen}
                  />
                </div>

                <div className="lead-field-group">
                  <label htmlFor="detail-lead-followup">
                    Próximo seguimiento
                  </label>
                  <GlassDatePicker
                    id="detail-lead-followup"
                    value={formData.nextFollowUp || ""}
                    onChange={(val) => handleInputChange("nextFollowUp", val)}
                    disabled={isSubmitting || formData.stage === "CERRADO"}
                    placeholder="dd / mm / aaaa"
                    onOpenChange={setIsDatePickerOpen}
                  />
                  {formData.stage === "CERRADO" && (
                    <p className="lead-helper-text">
                      Un lead cerrado no requiere seguimiento.
                    </p>
                  )}
                </div>
              </div>

              {/* Field 3: Teléfono y Correo */}
              <div
                className={`lead-field-group form-field-dimmable ${
                  isStageSelectOpen || isDatePickerOpen ? "is-dimmed" : ""
                }`}
              >
                <div className="lead-field-grid">
                  <div className="lead-field-group">
                    <label htmlFor="detail-lead-phone">Teléfono</label>
                    <input
                      id="detail-lead-phone"
                      type="tel"
                      placeholder="Ej: 998877665"
                      value={formData.phone || ""}
                      onChange={(e) => handleInputChange("phone", e.target.value)}
                      disabled={isSubmitting}
                      className={`liquid-glass-input ${
                        formErrors.phone ? "border-red-400 ring-2 ring-red-400/30" : ""
                      }`}
                    />
                    {formErrors.phone && (
                      <p role="alert" className="lead-error-text">
                        {formErrors.phone}
                      </p>
                    )}
                  </div>

                  <div className="lead-field-group">
                    <label htmlFor="detail-lead-email">Correo electrónico</label>
                    <input
                      id="detail-lead-email"
                      type="email"
                      placeholder="laura@empresa.com"
                      value={formData.email || ""}
                      onChange={(e) => handleInputChange("email", e.target.value)}
                      disabled={isSubmitting}
                      className={`liquid-glass-input ${
                        formErrors.email ? "border-red-400 ring-2 ring-red-400/30" : ""
                      }`}
                    />
                    {formErrors.email && (
                      <p role="alert" className="lead-error-text">
                        {formErrors.email}
                      </p>
                    )}
                  </div>
                </div>
                <p className="lead-helper-text">
                  * Debe ingresar al menos un teléfono o un correo electrónico de contacto.
                </p>
              </div>

              {/* Field 4: Servicio de interés */}
              <div
                className={`lead-field-group form-field-dimmable ${
                  isDatePickerOpen ? "is-dimmed" : ""
                }`}
              >
                <label htmlFor="detail-lead-service">
                  Servicio de interés <span className="text-white/40 font-normal">(Opcional)</span>
                </label>
                <input
                  id="detail-lead-service"
                  type="text"
                  placeholder="Ej: Consultoría Cloud, Auditoría CRM"
                  value={formData.service || ""}
                  onChange={(e) => handleInputChange("service", e.target.value)}
                  disabled={isSubmitting}
                  className="liquid-glass-input"
                />
              </div>

              {/* Modal Actions */}
              <div className="lead-modal-actions">
                <button
                  type="button"
                  onClick={handleCloseEditModal}
                  disabled={isSubmitting}
                  className="lead-btn-cancel"
                >
                  Cancelar
                </button>

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="aurora-chip-btn lead-btn-submit"
                >
                  {isSubmitting ? (
                    <>
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      <span>Actualizando...</span>
                    </>
                  ) : (
                    <>
                      <Check className="h-4 w-4" />
                      <span>Guardar Cambios</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Delete Confirmation Liquid Glass Modal ── */}
      {isDeleteModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-md animate-in fade-in duration-200"
          onClick={() => setIsDeleteModalOpen(false)}
        >
          <div
            className="liquid-glass-modal lead-delete-dialog relative overflow-hidden text-white"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="delete-icon-wrapper">
              <Trash2 className="h-7 w-7" />
            </div>

            <div>
              <h3 className="delete-title">¿Descartar este lead?</h3>
              <p className="delete-desc">
                Estás a punto de eliminar a{" "}
                <span className="text-white font-semibold">&ldquo;{lead.name}&rdquo;</span>{" "}
                de tu base de datos comercial. Esta acción no se puede deshacer.
              </p>
            </div>

            <div className="delete-actions">
              <button
                type="button"
                disabled={isDeleting}
                onClick={() => setIsDeleteModalOpen(false)}
                className="delete-btn-cancel"
              >
                Cancelar
              </button>

              <button
                type="button"
                disabled={isDeleting}
                onClick={handleConfirmDelete}
                className="delete-btn-confirm"
              >
                {isDeleting ? (
                  <>
                    <RefreshCw className="h-4 w-4 animate-spin" />
                    <span>Descartando...</span>
                  </>
                ) : (
                  <>
                    <Trash2 className="h-4 w-4" />
                    <span>Sí, descartar</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
