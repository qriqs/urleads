import React, { useState, useEffect, useCallback } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  Search,
  Plus,
  Phone,
  Mail,
  Calendar,
  AlertCircle,
  RefreshCw,
  X,
  ChevronRight,
  Briefcase,
  User,
  Trash2,
  Sparkles,
  Pencil,
  Filter,
  FilterX,
  Check,
} from "lucide-react";
import { sileo } from "sileo";
import {
  getLeadsApi,
  createLeadApi,
  updateLeadApi,
  deleteLeadApi,
  Lead,
  LeadStage,
  CreateLeadPayload,
  UpdateLeadPayload,
  FollowUpFilter,
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

const FILTER_STAGE_OPTIONS: GlassSelectOption[] = [
  {
    value: "",
    label: "Todas las etapas",
    description: "Ver todos los estados",
  },
  {
    value: "NUEVO",
    label: "Nuevo",
    description: "Prospectos recién registrados",
    colorDot: "bg-sky-400",
  },
  {
    value: "EN_SEGUIMIENTO",
    label: "En seguimiento",
    description: "Contacto activo y negociación",
    colorDot: "bg-amber-400",
  },
  {
    value: "CERRADO",
    label: "Cerrado",
    description: "Tratos comerciales concretados",
    colorDot: "bg-emerald-400",
  },
];

const FILTER_FOLLOWUP_OPTIONS: GlassSelectOption[] = [
  {
    value: "all",
    label: "Todos los seguimientos",
    description: "Cualquier fecha programada",
  },
  {
    value: "today",
    label: "Para hoy",
    description: "Seguimiento programado hoy",
    colorDot: "bg-cyan-400",
  },
  {
    value: "overdue",
    label: "Atrasados",
    description: "Seguimiento con fecha vencida",
    colorDot: "bg-rose-400",
  },
];

export default function LeadsList() {
  // Data states
  const [leads, setLeads] = useState<Lead[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [page, setPage] = useState<number>(0);

  const [searchParams, setSearchParams] = useSearchParams();
  const initialStage = (searchParams.get("stage") as LeadStage) || "";
  const initialFollowUp = (searchParams.get("followUp") as FollowUpFilter) || "all";
  const initialSearch = searchParams.get("search") || "";

  // Search & filter states
  const [searchInput, setSearchInput] = useState<string>(initialSearch);
  const [debouncedSearch, setDebouncedSearch] = useState<string>(initialSearch);
  const [selectedStage, setSelectedStage] = useState<LeadStage | "">(initialStage);
  const [selectedFollowUp, setSelectedFollowUp] = useState<FollowUpFilter>(initialFollowUp);

  // Sync state when URL params change (e.g. navigation from dashboard)
  useEffect(() => {
    const stageParam = (searchParams.get("stage") as LeadStage) || "";
    const followUpParam = (searchParams.get("followUp") as FollowUpFilter) || "all";
    const searchParam = searchParams.get("search") || "";
    setSelectedStage(stageParam);
    setSelectedFollowUp(followUpParam);
    setSearchInput(searchParam);
    setDebouncedSearch(searchParam);
  }, [searchParams]);


  // Loading & error states
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<LeadApiError | null>(null);

  // Modal & form states
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [editingLead, setEditingLead] = useState<Lead | null>(null);
  const [formData, setFormData] = useState<CreateLeadPayload>({
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
  const fetchLeads = useCallback(
    async (
      searchQuery: string,
      pageNumber: number,
      stageFilter: LeadStage | "",
      followUpFilter: FollowUpFilter
    ) => {
      setIsLoading(true);
      setError(null);
      try {
        const response = await getLeadsApi({
          search: searchQuery,
          page: pageNumber,
          stage: stageFilter || undefined,
          followUp: followUpFilter !== "all" ? followUpFilter : undefined,
        });
        setLeads(response.content || []);
        setTotalElements(response.totalElements ?? 0);
        setTotalPages(response.totalPages ?? 1);
      } catch (err: any) {
        const errorObj: LeadApiError = err.message
          ? (err as LeadApiError)
          : {
              status: 500,
              message: "Error al comunicarse con el servidor.",
            };
        setError(errorObj);
        sileo.error({
          title: "Error de sincronización",
          description: errorObj.message,
        });
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  // Fetch leads on mount or when search/page/filters change
  useEffect(() => {
    fetchLeads(debouncedSearch, page, selectedStage, selectedFollowUp);
  }, [debouncedSearch, page, selectedStage, selectedFollowUp, fetchLeads]);

  // Handle ESC key to close modals
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (leadToDelete) setLeadToDelete(null);
        else if (isModalOpen) handleCloseModal();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isModalOpen, leadToDelete]);

  const hasActiveFilters = Boolean(
    debouncedSearch || selectedStage || selectedFollowUp !== "all"
  );

  const handleClearFilters = () => {
    setSearchInput("");
    setDebouncedSearch("");
    setSelectedStage("");
    setSelectedFollowUp("all");
    setPage(0);
  };

  const handleOpenCreateModal = () => {
    setEditingLead(null);
    setFormData({
      name: "",
      phone: "",
      email: "",
      stage: "NUEVO",
      nextFollowUp: "",
      service: "",
    });
    setFormErrors({});
    setIsStageSelectOpen(false);
    setIsDatePickerOpen(false);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (lead: Lead) => {
    setEditingLead(lead);
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
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingLead(null);
    setIsStageSelectOpen(false);
    setIsDatePickerOpen(false);
    setFormErrors({});
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
  };

  const handleSubmitLead = async (e: React.FormEvent) => {
    e.preventDefault();
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
      if (localErrors.name && (!trimmedPhone && !trimmedEmail)) {
        sileo.warning({
          title: "Campos requeridos",
          description: "Completa el nombre y proporciona al menos un teléfono o correo de contacto.",
        });
      } else if (localErrors.name) {
        sileo.warning({
          title: "Nombre requerido",
          description: "El nombre debe tener al menos 2 caracteres.",
        });
      } else {
        sileo.warning({
          title: "Contacto requerido",
          description: "Debe ingresar al menos un teléfono o un correo electrónico.",
        });
      }
      return;
    }

    setIsSubmitting(true);
    try {
      const payload: UpdateLeadPayload = {
        name: trimmedName,
        phone: trimmedPhone || null,
        email: trimmedEmail || null,
        stage: formData.stage,
        nextFollowUp: formData.stage === "CERRADO" ? null : (formData.nextFollowUp || null),
        service: formData.service?.trim() || null,
      };

      if (editingLead) {
        await updateLeadApi(editingLead.id, payload);
        setIsModalOpen(false);
        setEditingLead(null);
        sileo.success({
          title: "¡Lead actualizado exitosamente!",
          description: `Los cambios para "${trimmedName}" han sido guardados.`,
        });
        fetchLeads(debouncedSearch, page, selectedStage, selectedFollowUp);
      } else {
        await createLeadApi(payload);
        setIsModalOpen(false);
        setFormData({
          name: "",
          phone: "",
          email: "",
          stage: "NUEVO",
          nextFollowUp: "",
          service: "",
        });
        sileo.success({
          title: "¡Lead registrado exitosamente!",
          description: `${trimmedName} ahora forma parte de tu pipeline comercial.`,
        });
        setPage(0);
        fetchLeads(debouncedSearch, 0, selectedStage, selectedFollowUp);
      }
    } catch (err: any) {
      const apiErr = err as LeadApiError;
      if (apiErr.fieldErrors && Object.keys(apiErr.fieldErrors).length > 0) {
        setFormErrors(apiErr.fieldErrors);
      }
      const actionText = editingLead ? "actualizar" : "registrar";
      const errorMsg = apiErr.message || `Error al ${actionText} el lead. Revisa los datos.`;
      sileo.error({
        title: `Error al ${actionText} lead`,
        description: errorMsg,
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Delete lead handler
  const handleConfirmDelete = async () => {
    if (!leadToDelete || isDeleting) return;
    const deletedName = leadToDelete.name;
    setIsDeleting(true);
    try {
      await deleteLeadApi(leadToDelete.id);
      sileo.success({
        title: "Lead descartado exitosamente",
        description: `El lead "${deletedName}" fue eliminado del pipeline.`,
      });
      setLeadToDelete(null);

      const targetPage = leads.length === 1 && page > 0 ? page - 1 : page;
      if (targetPage !== page) {
        setPage(targetPage);
      } else {
        fetchLeads(debouncedSearch, targetPage, selectedStage, selectedFollowUp);
      }
    } catch (err: any) {
      const errorMsg = err.message || "No se pudo descartar el lead.";
      setError({
        status: 500,
        message: errorMsg,
      });
      sileo.error({
        title: "Error al descartar lead",
        description: errorMsg,
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
              className="lead-search-input"
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
            onClick={handleOpenCreateModal}
            className="aurora-chip-btn lead-btn-new shrink-0 h-10 text-sm font-medium"
          >
            <Plus className="h-4 w-4" />
            <span>Nuevo Lead</span>
          </button>
        </div>
      </div>

      {/* ── Filter Bar ── */}
      <div className="relative z-20 flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3 sm:px-4 sm:py-3 rounded-2xl bg-white/[0.04] border border-white/10 backdrop-blur-md">
        <div className="flex flex-wrap items-center gap-2.5 w-full sm:w-auto">
          <div className="flex items-center gap-2 text-xs font-semibold text-white/70 uppercase tracking-wider shrink-0 mr-1">
            <Filter className="h-3.5 w-3.5 text-cyan-300" />
            <span>Filtros:</span>
          </div>

          {/* Stage Select Filter */}
          <div className="w-full xs:w-44 sm:w-48">
            <GlassSelect
              id="filter-stage"
              size="sm"
              value={selectedStage}
              onChange={(val) => {
                setSelectedStage(val as LeadStage | "");
                setPage(0);
              }}
              options={FILTER_STAGE_OPTIONS}
              placeholder="Etapa: Todas"
            />
          </div>

          {/* Follow-up Select Filter */}
          <div className="w-full xs:w-48 sm:w-52">
            <GlassSelect
              id="filter-followup"
              size="sm"
              value={selectedFollowUp}
              onChange={(val) => {
                setSelectedFollowUp(val as FollowUpFilter);
                setPage(0);
              }}
              options={FILTER_FOLLOWUP_OPTIONS}
              placeholder="Seguimiento: Todos"
            />
          </div>

          {/* Clear filters button if any filter is active */}
          {hasActiveFilters && (
            <button
              type="button"
              onClick={handleClearFilters}
              className="inline-flex items-center gap-1.5 h-10 px-3.5 rounded-xl text-xs font-medium bg-white/10 hover:bg-white/15 text-white/80 hover:text-white border border-white/15 transition-all cursor-pointer active:scale-95 shrink-0"
              title="Restablecer todos los filtros"
            >
              <FilterX className="h-3.5 w-3.5 text-cyan-300" />
              <span>Limpiar filtros</span>
            </button>
          )}
        </div>

        {/* Active Filters Summary */}
        <div className="flex items-center gap-2 text-xs text-white/60">
          {hasActiveFilters ? (
            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-cyan-400/10 text-cyan-200 border border-cyan-400/25">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse" />
              Filtros activos
            </span>
          ) : (
            <span className="text-white/40 hidden md:inline">Mostrando todos los registros</span>
          )}
        </div>
      </div>

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
        ) : error && leads.length === 0 ? (
          /* Connection Error State */
          <div className="leads-empty-state w-full min-h-[440px] p-10 sm:p-16 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md flex flex-col items-center justify-center text-center gap-4">
            <div className="w-20 h-20 rounded-full bg-red-500/15 border border-red-500/30 flex items-center justify-center text-red-400 shrink-0">
              <AlertCircle className="h-10 w-10" />
            </div>
            <h3 className="text-xl font-semibold text-white">
              No se pudieron sincronizar los leads
            </h3>
            <p className="text-base text-white/70 max-w-md mx-auto leading-relaxed">
              {error.message || "Ocurrió un error al consultar el servidor."}
            </p>
            <button
              type="button"
              onClick={() => fetchLeads(debouncedSearch, page, selectedStage, selectedFollowUp)}
              className="aurora-chip-btn h-10 px-6 text-sm font-medium"
            >
              <RefreshCw className="h-4 w-4" />
              <span>Reintentar conexión</span>
            </button>
          </div>
        ) : leads.length === 0 ? (
          /* Empty State */
          <div className="leads-empty-state w-full min-h-[440px] p-10 sm:p-16 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
            {hasActiveFilters ? (
              <div className="flex flex-col items-center justify-center text-center max-w-lg mx-auto gap-4">
                <div className="w-20 h-20 rounded-full bg-white/10 flex items-center justify-center text-white/90 border border-white/20 shadow-lg shadow-black/10 shrink-0">
                  <FilterX className="h-10 w-10 text-cyan-300" />
                </div>
                <h3 className="text-xl font-semibold text-white">
                  No se encontraron resultados
                </h3>
                <p className="text-base text-white/70 text-center leading-relaxed">
                  {debouncedSearch
                    ? `No hay ningún lead que coincida con "${debouncedSearch}" con los filtros seleccionados.`
                    : "No hay ningún lead que coincida con los filtros seleccionados."}
                </p>
                <button
                  type="button"
                  onClick={handleClearFilters}
                  className="aurora-chip-btn h-10 px-5 text-sm"
                >
                  <FilterX className="h-4 w-4" />
                  <span>Restablecer filtros</span>
                </button>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center text-center max-w-xl mx-auto gap-6">
                {/* User Icon: enlarged and centered */}
                <div className="w-24 h-24 rounded-full bg-white/10 flex items-center justify-center text-white/90 border border-white/20 shadow-lg shadow-black/10 shrink-0">
                  <User className="h-12 w-12 text-white/90" />
                </div>
                <p className="text-lg sm:text-xl text-white/90 font-normal text-center leading-relaxed">
                  Registra tu primer lead para comenzar a gestionar tu pipeline
                  <br className="hidden sm:inline" /> comercial y hacer seguimiento a tus ventas.
                </p>
                <button
                  type="button"
                  onClick={handleOpenCreateModal}
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

                        {/* Actions (Ver Detalle + Editar + Descartar) */}
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
                              onClick={() => handleOpenEditModal(lead)}
                              className="liquid-glass-btn-secondary px-2.5 py-1.5 rounded-xl text-xs font-medium inline-flex items-center gap-1.5 active:scale-95 cursor-pointer text-white/90 hover:text-white"
                              title="Editar este lead"
                              aria-label={`Editar lead ${lead.name}`}
                            >
                              <Pencil className="h-3.5 w-3.5 text-cyan-300" />
                              <span>Editar</span>
                            </button>

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

                  {/* Mobile Actions: Ver Detalle + Editar + Descartar */}
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
                      onClick={() => handleOpenEditModal(lead)}
                      className="liquid-glass-btn-secondary py-2 px-3 rounded-xl text-xs font-medium flex items-center justify-center gap-1.5 cursor-pointer text-white/90"
                      aria-label={`Editar lead ${lead.name}`}
                    >
                      <Pencil className="h-3.5 w-3.5 text-cyan-300" />
                      <span>Editar</span>
                    </button>

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

      {/* ── Liquid Glass Modal: Registrar / Editar Lead ── */}
      {isModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="lead-modal-title"
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
                  {editingLead ? (
                    <>
                      <Pencil className="h-3.5 w-3.5 text-cyan-300" />
                      <span>Editar Prospecto</span>
                    </>
                  ) : (
                    <>
                      <Sparkles className="h-3.5 w-3.5 text-white/90" />
                      <span>Nuevo Prospecto</span>
                    </>
                  )}
                </div>
                <h2 id="lead-modal-title" className="lead-modal-title">
                  {editingLead ? "Editar Lead" : "Registrar Lead"}
                </h2>
                <p className="lead-modal-subtitle">
                  {editingLead
                    ? `Actualiza la información comercial y seguimiento de "${editingLead.name}".`
                    : "Ingresa los datos para incorporarlo a tu embudo comercial."}
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
                    onOpenChange={setIsStageSelectOpen}
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
                    onOpenChange={setIsDatePickerOpen}
                  />
                  {formData.stage === "CERRADO" && (
                    <p className="lead-helper-text">
                      Un lead cerrado no requiere seguimiento.
                    </p>
                  )}
                </div>
              </div>

              {/* Field 3: Teléfono y Correo con helper text integrado */}
              <div
                className={`lead-field-group form-field-dimmable ${
                  isStageSelectOpen || isDatePickerOpen ? "is-dimmed" : ""
                }`}
              >
                <div className="lead-field-grid">
                  <div className="lead-field-group">
                    <label htmlFor="lead-phone">
                      Teléfono
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
                    <label htmlFor="lead-email">
                      Correo electrónico
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
              <div
                className={`lead-field-group form-field-dimmable ${
                  isDatePickerOpen ? "is-dimmed" : ""
                }`}
              >
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
                      <span>{editingLead ? "Actualizando..." : "Guardando..."}</span>
                    </>
                  ) : editingLead ? (
                    <>
                      <Check className="h-4 w-4" />
                      <span>Guardar Cambios</span>
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
