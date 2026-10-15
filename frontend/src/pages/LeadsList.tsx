import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  Search,
  Plus,
  Phone,
  Mail,
  Calendar,
  AlertCircle,
  CheckCircle2,
  RefreshCw,
  X,
  ChevronRight,
  Briefcase,
  User,
  Trash2,
  Sparkles,
} from "lucide-react";
import {
  getLeadsApi,
  createLeadApi,
  deleteLeadApi,
  Lead,
  LeadStage,
  CreateLeadPayload,
  LeadApiError,
} from "../services/leads";
import { GlassSelect, GlassSelectOption } from "../components/ui/GlassSelect";
import { GlassDatePicker } from "../components/ui/GlassDatePicker";

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

export default function LeadsList() {
  // Data states
  const [leads, setLeads] = useState<Lead[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [page, setPage] = useState<number>(0);

  // Search & filter states
  const [searchInput, setSearchInput] = useState<string>("");
  const [debouncedSearch, setDebouncedSearch] = useState<string>("");

  // Loading & error states
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<LeadApiError | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Modal & form states
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [formData, setFormData] = useState<CreateLeadPayload>({
    name: "",
    phone: "",
    email: "",
    stage: "NUEVO",
    nextFollowUp: "",
    service: "",
  });
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});
  const [formGeneralError, setFormGeneralError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  // Delete modal state
  const [leadToDelete, setLeadToDelete] = useState<Lead | null>(null);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);

  // Debounce search input by 350ms
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchInput.trim());
      setPage(0);
    }, 350);
    return () => clearTimeout(timer);
  }, [searchInput]);

  // Fetch leads callback
  const fetchLeads = useCallback(async (searchQuery: string, pageNumber: number) => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await getLeadsApi(searchQuery, pageNumber);
      setLeads(response.content || []);
      setTotalElements(response.totalElements ?? 0);
      setTotalPages(response.totalPages ?? 1);
    } catch (err: any) {
      setError(
        err.message
          ? (err as LeadApiError)
          : {
              status: 500,
              message: "Error al comunicarse con el servidor.",
            }
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Fetch leads on mount or when debouncedSearch/page change
  useEffect(() => {
    fetchLeads(debouncedSearch, page);
  }, [debouncedSearch, page, fetchLeads]);

  // Handle ESC key to close modals
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (leadToDelete) setLeadToDelete(null);
        else if (isModalOpen) setIsModalOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isModalOpen, leadToDelete]);

  // Clear success notification after 5 seconds
  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  const handleOpenModal = () => {
    setFormErrors({});
    setFormGeneralError(null);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setFormErrors({});
    setFormGeneralError(null);
  };

  const handleInputChange = (field: keyof CreateLeadPayload, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (formErrors[field]) {
      setFormErrors((prev) => {
        const updated = { ...prev };
        delete updated[field];
        return updated;
      });
    }
    if (formGeneralError) {
      setFormGeneralError(null);
    }
  };

  const handleSubmitLead = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormErrors({});
    setFormGeneralError(null);

    const trimmedName = formData.name.trim();
    const trimmedPhone = formData.phone?.trim() || "";
    const trimmedEmail = formData.email?.trim() || "";

    const localErrors: Record<string, string> = {};
    if (!trimmedName || trimmedName.length < 2) {
      localErrors.name = "El nombre debe tener al menos 2 caracteres.";
    }
    if (!trimmedPhone && !trimmedEmail) {
      setFormGeneralError("Debe proporcionar al menos un teléfono o un correo electrónico.");
    }

    if (Object.keys(localErrors).length > 0) {
      setFormErrors(localErrors);
      return;
    }

    if (!trimmedPhone && !trimmedEmail) {
      return;
    }

    setIsSubmitting(true);
    try {
      await createLeadApi({
        name: trimmedName,
        phone: trimmedPhone || null,
        email: trimmedEmail || null,
        stage: formData.stage,
        nextFollowUp: formData.nextFollowUp || null,
        service: formData.service?.trim() || null,
      });

      setIsModalOpen(false);
      setFormData({
        name: "",
        phone: "",
        email: "",
        stage: "NUEVO",
        nextFollowUp: "",
        service: "",
      });
      setSuccessMessage("¡Lead registrado exitosamente!");
      fetchLeads(debouncedSearch, 0);
      setPage(0);
    } catch (err: any) {
      const apiErr = err as LeadApiError;
      if (apiErr.fieldErrors && Object.keys(apiErr.fieldErrors).length > 0) {
        setFormErrors(apiErr.fieldErrors);
      }
      setFormGeneralError(apiErr.message || "Error al registrar el lead. Revisa los datos.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // Delete lead handler
  const handleConfirmDelete = async () => {
    if (!leadToDelete) return;
    setIsDeleting(true);
    try {
      await deleteLeadApi(leadToDelete.id);
      setSuccessMessage(`El lead "${leadToDelete.name}" fue descartado exitosamente.`);
      setLeadToDelete(null);
      fetchLeads(debouncedSearch, page);
    } catch (err: any) {
      setError({
        status: 500,
        message: err.message || "No se pudo descartar el lead.",
      });
      setLeadToDelete(null);
    } finally {
      setIsDeleting(false);
    }
  };

  // Helper for stage badge
  const renderStageBadge = (stage: LeadStage | string) => {
    switch (stage) {
      case "NUEVO":
        return (
          <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-sky-400/15 text-sky-200 border border-sky-400/30 shadow-[0_0_12px_rgba(56,189,248,0.2)] backdrop-blur-md">
            <span className="w-1.5 h-1.5 rounded-full bg-sky-400 animate-pulse" />
            Nuevo
          </span>
        );
      case "EN_SEGUIMIENTO":
        return (
          <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-amber-400/15 text-amber-200 border border-amber-400/30 shadow-[0_0_12px_rgba(251,191,36,0.2)] backdrop-blur-md">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
            En seguimiento
          </span>
        );
      case "CERRADO":
        return (
          <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-400/15 text-emerald-200 border border-emerald-400/30 shadow-[0_0_12px_rgba(52,211,153,0.2)] backdrop-blur-md">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            Cerrado
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-white/10 text-white/80 border border-white/20">
            {stage}
          </span>
        );
    }
  };

  return (
    <div className="w-full max-w-full space-y-6 animate-in fade-in duration-500">
      {/* ── Top Header & Actions ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white drop-shadow-sm">
              Leads
            </h1>
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-white/10 border border-white/15 text-white/80 backdrop-blur-md">
              <span className="w-1.5 h-1.5 rounded-full bg-[#00c988]" />
              {totalElements} {totalElements === 1 ? "contacto" : "contactos"}
            </span>
          </div>
          <p className="text-sm text-white/70 mt-1">
            Consulta, busca y gestiona oportunidades comerciales en tiempo real.
          </p>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          {/* Search bar with perfect icon alignment */}
          <div className="relative flex-1 sm:w-72">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-white/50 pointer-events-none" />
            <input
              type="text"
              id="lead-search-input"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Buscar por nombre..."
              aria-label="Buscar leads por nombre"
              className="w-full h-10 pl-10 pr-9 rounded-full bg-white/10 border border-white/20 text-sm text-white placeholder:text-white/40 focus:outline-none focus:ring-2 focus:ring-[#00c988]/40 backdrop-blur-md transition-all"
            />
            {searchInput && (
              <button
                type="button"
                onClick={() => setSearchInput("")}
                aria-label="Limpiar búsqueda"
                className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-white/50 hover:text-white rounded-full transition-colors"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>

          {/* New Lead Aurora Chip Button */}
          <button
            type="button"
            onClick={handleOpenModal}
            className="aurora-chip-btn shrink-0 h-10 px-5 text-sm font-medium"
          >
            <Plus className="h-4 w-4" />
            <span>Nuevo Lead</span>
          </button>
        </div>
      </div>

      {/* ── Success Banner ── */}
      {successMessage && (
        <div
          role="status"
          className="flex items-center justify-between p-4 rounded-2xl bg-emerald-500/15 border border-emerald-400/30 text-emerald-100 text-sm backdrop-blur-xl shadow-[0_8px_20px_rgba(16,185,129,0.15)] animate-in fade-in duration-300"
        >
          <div className="flex items-center gap-3">
            <CheckCircle2 className="h-5 w-5 text-emerald-400 shrink-0" />
            <span className="font-medium">{successMessage}</span>
          </div>
          <button
            type="button"
            onClick={() => setSuccessMessage(null)}
            className="text-emerald-300 hover:text-white transition-colors p-1"
            aria-label="Cerrar notificación"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* ── Error Banner ── */}
      {error && (
        <div
          role="alert"
          className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-2xl bg-red-500/15 border border-red-500/30 text-red-200 text-sm backdrop-blur-xl shadow-[0_8px_20px_rgba(239,68,68,0.15)] animate-in fade-in duration-300"
        >
          <div className="flex items-center gap-3">
            <AlertCircle className="h-5 w-5 text-red-400 shrink-0" />
            <div>
              <p className="font-semibold text-white">Error al sincronizar leads</p>
              <p className="text-red-200 text-xs sm:text-sm">{error.message}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => fetchLeads(debouncedSearch, page)}
            className="shrink-0 liquid-glass-btn-secondary px-3.5 py-1.5 rounded-xl text-xs font-medium flex items-center gap-1.5"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            Reintentar
          </button>
        </div>
      )}

      {/* ── Main Content Area ── */}
      <div className="w-full">
        {/* Loading State */}
        {isLoading ? (
          <div className="liquid-glass-card rounded-3xl p-12 sm:p-16 text-center">
            <div className="w-14 h-14 mx-auto mb-4 rounded-2xl bg-white/10 border border-white/20 flex items-center justify-center text-cyan-300 animate-spin">
              <RefreshCw className="h-6 w-6" />
            </div>
            <p className="text-white font-semibold text-lg">Cargando leads...</p>
            <p className="text-white/60 text-xs sm:text-sm mt-1">
              Obteniendo prospectos comerciales en tiempo real
            </p>
          </div>
        ) : leads.length === 0 ? (
          /* Empty State */
          <div className="leads-empty-state w-full min-h-[440px] p-10 sm:p-16 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
            {/* User Icon: enlarged and centered */}
            <div className="w-24 h-24 rounded-full bg-white/10 flex items-center justify-center text-white/90 border border-white/20 shadow-lg shadow-black/10 shrink-0">
              <User className="h-12 w-12 text-white/90" />
            </div>

            {debouncedSearch ? (
              <div className="flex flex-col items-center justify-center text-center max-w-lg mx-auto gap-4">
                <h3 className="text-xl font-semibold text-white">
                  No se encontraron resultados
                </h3>
                <p className="text-base text-white/70 text-center leading-relaxed">
                  No hay ningún lead que coincida con &ldquo;{debouncedSearch}&rdquo;.
                </p>
                <button
                  type="button"
                  onClick={() => setSearchInput("")}
                  className="aurora-chip-btn h-10 px-5 text-sm"
                >
                  <X className="h-4 w-4" />
                  <span>Limpiar búsqueda</span>
                </button>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center text-center max-w-xl mx-auto gap-6">
                <p className="text-lg sm:text-xl text-white/90 font-normal text-center leading-relaxed">
                  Registra tu primer lead para comenzar a gestionar tu pipeline
                  <br className="hidden sm:inline" /> comercial y hacer seguimiento a tus ventas.
                </p>
                <button
                  type="button"
                  onClick={handleOpenModal}
                  className="aurora-chip-btn h-12 px-8 text-base font-medium"
                >
                  <Plus className="h-5 w-5" />
                  <span>Crear mi primer lead</span>
                </button>
              </div>
            )}
          </div>
        ) : (
          /* Data Exists: Table (Desktop) & Cards (Mobile) */
          <div className="space-y-4">
            {/* 1. Desktop Liquid Glass Table */}
            <div className="hidden md:block liquid-glass-card rounded-2xl overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm text-white/90">
                  <thead className="bg-white/[0.08] text-white/60 text-xs font-semibold uppercase tracking-wider border-b border-white/15">
                    <tr>
                      <th scope="col" className="py-4 px-6">
                        Nombre / Servicio
                      </th>
                      <th scope="col" className="py-4 px-6">
                        Contacto
                      </th>
                      <th scope="col" className="py-4 px-6">
                        Etapa
                      </th>
                      <th scope="col" className="py-4 px-6">
                        Próximo Seguimiento
                      </th>
                      <th scope="col" className="py-4 px-6 text-right">
                        Acciones
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/[0.08]">
                    {leads.map((lead) => (
                      <tr
                        key={lead.id}
                        className="hover:bg-white/[0.06] transition-colors group"
                      >
                        {/* Name & Service with generous spacing */}
                        <td className="py-5 px-6">
                          <div className="font-semibold text-white text-sm group-hover:text-cyan-300 transition-colors">
                            {lead.name}
                          </div>
                          {lead.service ? (
                            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 mt-1 rounded-md bg-white/[0.06] text-white/70 text-xs border border-white/10">
                              <Briefcase className="h-3 w-3 text-cyan-300/70" />
                              <span className="truncate max-w-[220px]">{lead.service}</span>
                            </div>
                          ) : (
                            <span className="text-xs text-white/40 italic block mt-0.5">
                              Sin servicio especificado
                            </span>
                          )}
                        </td>

                        {/* Contact (Phone / Email) */}
                        <td className="py-5 px-6">
                          <div className="space-y-1">
                            {lead.phone && (
                              <div className="flex items-center gap-2 text-xs text-white/80">
                                <Phone className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
                                <span>{lead.phone}</span>
                              </div>
                            )}
                            {lead.email && (
                              <div className="flex items-center gap-2 text-xs text-white/80">
                                <Mail className="h-3.5 w-3.5 text-sky-400 shrink-0" />
                                <span className="truncate max-w-[200px]" title={lead.email}>
                                  {lead.email}
                                </span>
                              </div>
                            )}
                            {!lead.phone && !lead.email && (
                              <span className="text-xs text-white/40 italic">
                                Sin datos de contacto
                              </span>
                            )}
                          </div>
                        </td>

                        {/* Stage with unclipped pill */}
                        <td className="py-5 px-6 whitespace-nowrap">
                          {renderStageBadge(lead.stage)}
                        </td>

                        {/* Next Follow Up */}
                        <td className="py-5 px-6 whitespace-nowrap">
                          {lead.nextFollowUp ? (
                            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full liquid-glass-pill text-xs text-white/90">
                              <Calendar className="h-3.5 w-3.5 text-cyan-300" />
                              <span>{lead.nextFollowUp}</span>
                            </div>
                          ) : (
                            <span className="text-xs text-white/40 italic">
                              Sin fecha programada
                            </span>
                          )}
                        </td>

                        {/* Actions (Ver Detalle + Descartar / Eliminar) */}
                        <td className="py-5 px-6 text-right whitespace-nowrap">
                          <div className="inline-flex items-center gap-2">
                            <Link
                              to={`/app/leads/${lead.id}`}
                              className="liquid-glass-btn-secondary px-3 py-1.5 rounded-xl text-xs font-medium inline-flex items-center gap-1.5 active:scale-95 cursor-pointer"
                              title="Ver detalle del lead"
                            >
                              <span>Ver detalle</span>
                              <ChevronRight className="h-3.5 w-3.5 text-white/60" />
                            </Link>

                            <button
                              type="button"
                              onClick={() => setLeadToDelete(lead)}
                              className="liquid-glass-btn-danger px-2.5 py-1.5 rounded-xl text-xs font-medium inline-flex items-center gap-1.5 active:scale-95 cursor-pointer"
                              title="Descartar este lead"
                              aria-label={`Descartar lead ${lead.name}`}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              <span>Descartar</span>
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 2. Mobile Liquid Glass Cards */}
            <div className="block md:hidden space-y-3">
              {leads.map((lead) => (
                <div
                  key={lead.id}
                  className="liquid-glass-card rounded-2xl p-4.5 space-y-3"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <h2 className="font-semibold text-white text-base truncate">
                        {lead.name}
                      </h2>
                      {lead.service && (
                        <p className="text-xs text-white/65 flex items-center gap-1.5 mt-0.5 truncate">
                          <Briefcase className="h-3 w-3 text-cyan-300 shrink-0" />
                          <span>{lead.service}</span>
                        </p>
                      )}
                    </div>
                    <div className="shrink-0">{renderStageBadge(lead.stage)}</div>
                  </div>

                  {/* Contact Info */}
                  <div className="space-y-1.5 pt-2 border-t border-white/10 text-xs text-white/80">
                    {lead.phone && (
                      <div className="flex items-center gap-2">
                        <Phone className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
                        <a href={`tel:${lead.phone}`} className="hover:underline text-white/90">
                          {lead.phone}
                        </a>
                      </div>
                    )}
                    {lead.email && (
                      <div className="flex items-center gap-2">
                        <Mail className="h-3.5 w-3.5 text-sky-400 shrink-0" />
                        <a href={`mailto:${lead.email}`} className="hover:underline text-white/90 truncate">
                          {lead.email}
                        </a>
                      </div>
                    )}
                    {lead.nextFollowUp && (
                      <div className="flex items-center gap-2 text-white/70">
                        <Calendar className="h-3.5 w-3.5 text-cyan-300 shrink-0" />
                        <span>Seguimiento: {lead.nextFollowUp}</span>
                      </div>
                    )}
                  </div>

                  {/* Mobile Actions: Ver Detalle + Descartar */}
                  <div className="pt-2 flex items-center gap-2">
                    <Link
                      to={`/app/leads/${lead.id}`}
                      className="flex-1 liquid-glass-btn-secondary py-2 px-3 rounded-xl text-xs font-medium text-center flex items-center justify-center gap-1.5"
                    >
                      <span>Ver detalle</span>
                      <ChevronRight className="h-3.5 w-3.5" />
                    </Link>

                    <button
                      type="button"
                      onClick={() => setLeadToDelete(lead)}
                      className="liquid-glass-btn-danger py-2 px-3 rounded-xl text-xs font-medium flex items-center justify-center gap-1.5 cursor-pointer"
                      aria-label={`Descartar lead ${lead.name}`}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      <span>Descartar</span>
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2 text-xs text-white/70">
                <span>
                  Página {page + 1} de {totalPages} ({totalElements} leads en total)
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={page === 0 || isLoading}
                    onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                    className="liquid-glass-btn-secondary h-8 px-3 rounded-xl text-xs disabled:opacity-40 cursor-pointer"
                  >
                    Anterior
                  </button>
                  <button
                    type="button"
                    disabled={page >= totalPages - 1 || isLoading}
                    onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
                    className="liquid-glass-btn-secondary h-8 px-3 rounded-xl text-xs disabled:opacity-40 cursor-pointer"
                  >
                    Siguiente
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Liquid Glass Modal: Registrar Nuevo Lead ── */}
      {isModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-lead-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 bg-black/50 backdrop-blur-md animate-in fade-in duration-300"
          onClick={handleCloseModal}
        >
          <div
            className="liquid-glass-modal lead-modal-dialog relative overflow-hidden shadow-2xl text-white max-h-[92vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="lead-modal-header relative z-10">
              <div>
                <div className="lead-modal-badge shadow-sm">
                  <Sparkles className="h-3.5 w-3.5 text-white/90" />
                  <span>Nuevo Prospecto</span>
                </div>
                <h2 id="new-lead-title" className="lead-modal-title">
                  Registrar Lead
                </h2>
                <p className="lead-modal-subtitle">
                  Ingresa los datos para incorporarlo a tu embudo comercial.
                </p>
              </div>

              <button
                type="button"
                onClick={handleCloseModal}
                disabled={isSubmitting}
                aria-label="Cerrar modal"
                className="lead-modal-close"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* General Server Error Banner */}
            {formGeneralError && (
              <div
                role="alert"
                className="p-4 rounded-2xl bg-red-500/20 border border-red-500/40 text-red-100 text-sm flex items-start gap-3 shadow-lg relative z-10"
              >
                <AlertCircle className="h-5 w-5 text-red-400 shrink-0 mt-0.5" />
                <div className="flex-1 font-medium leading-relaxed">
                  {formGeneralError}
                </div>
              </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmitLead} className="relative z-10" noValidate>
              {/* Field 1: Nombre completo */}
              <div className="lead-field-group">
                <label htmlFor="lead-name">
                  Nombre completo <span className="text-white/60 font-normal">*</span>
                </label>
                <input
                  id="lead-name"
                  type="text"
                  required
                  autoFocus
                  placeholder="Ej: Laura Mendoza"
                  value={formData.name}
                  onChange={(e) => handleInputChange("name", e.target.value)}
                  disabled={isSubmitting}
                  className={`liquid-glass-input ${
                    formErrors.name ? "border-red-400 ring-2 ring-red-400/30" : ""
                  }`}
                  aria-invalid={!!formErrors.name}
                  aria-describedby={formErrors.name ? "lead-name-error" : undefined}
                />
                {formErrors.name && (
                  <p id="lead-name-error" role="alert" className="lead-error-text">
                    {formErrors.name}
                  </p>
                )}
              </div>

              {/* Field 2: Etapa comercial y Próximo seguimiento (elevado para evitar scroll) */}
              <div className="lead-field-grid">
                <div className="lead-field-group">
                  <label htmlFor="lead-stage">
                    Etapa comercial <span className="text-white/60 font-normal">*</span>
                  </label>
                  <GlassSelect
                    id="lead-stage"
                    value={formData.stage}
                    onChange={(val) =>
                      handleInputChange("stage", val as LeadStage)
                    }
                    options={STAGE_OPTIONS}
                    disabled={isSubmitting}
                  />
                </div>

                <div className="lead-field-group">
                  <label htmlFor="lead-followup">
                    Próximo seguimiento
                  </label>
                  <GlassDatePicker
                    id="lead-followup"
                    value={formData.nextFollowUp || ""}
                    onChange={(val) => handleInputChange("nextFollowUp", val)}
                    disabled={isSubmitting || formData.stage === "CERRADO"}
                    placeholder="dd / mm / aaaa"
                  />
                  {formData.stage === "CERRADO" && (
                    <p className="lead-helper-text">
                      Un lead cerrado no requiere seguimiento.
                    </p>
                  )}
                </div>
              </div>

              {/* Field 3: Teléfono y Correo con helper text integrado */}
              <div className="lead-field-group">
                <div className="lead-field-grid">
                  <div className="lead-field-group">
                    <label htmlFor="lead-phone" className="flex items-center justify-between">
                      <span>Teléfono</span>
                      <span className="text-[11px] text-white/40 font-normal">o correo</span>
                    </label>
                    <input
                      id="lead-phone"
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
                    <label htmlFor="lead-email" className="flex items-center justify-between">
                      <span>Correo electrónico</span>
                      <span className="text-[11px] text-white/40 font-normal">o teléfono</span>
                    </label>
                    <input
                      id="lead-email"
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
              <div className="lead-field-group">
                <label htmlFor="lead-service">
                  Servicio de interés <span className="text-white/40 font-normal">(Opcional)</span>
                </label>
                <input
                  id="lead-service"
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
                  onClick={handleCloseModal}
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
                      <span>Guardando...</span>
                    </>
                  ) : (
                    <>
                      <Plus className="h-4 w-4" />
                      <span>Guardar Lead</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Delete Confirmation Liquid Glass Modal ── */}
      {leadToDelete && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-md animate-in fade-in duration-200"
          onClick={() => setLeadToDelete(null)}
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
                <span className="text-white font-semibold">&ldquo;{leadToDelete.name}&rdquo;</span>{" "}
                de tu base de datos comercial. Esta acción no se puede deshacer.
              </p>
            </div>

            <div className="delete-actions">
              <button
                type="button"
                disabled={isDeleting}
                onClick={() => setLeadToDelete(null)}
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
