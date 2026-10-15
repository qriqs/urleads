import React, { useState, useEffect, useCallback, useId } from "react";
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
  ArrowUpDown,
  ExternalLink,
} from "lucide-react";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import {
  getLeadsApi,
  createLeadApi,
  Lead,
  LeadStage,
  CreateLeadPayload,
  LeadApiError,
} from "../services/leads";

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

  // Debounce search input by 350ms
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchInput.trim());
      setPage(0); // Reset to first page on search change
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

  // Handle ESC key to close modal
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && isModalOpen) {
        setIsModalOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isModalOpen]);

  // Clear success notification after 5 seconds
  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [successMessage]);

  // Open modal and reset errors (keep fields clean for fresh lead)
  const handleOpenModal = () => {
    setFormErrors({});
    setFormGeneralError(null);
    setIsModalOpen(true);
  };

  // Close modal without resetting data if user wants to resume, or reset
  const handleCloseModal = () => {
    setIsModalOpen(false);
    setFormErrors({});
    setFormGeneralError(null);
  };

  // Form input changes
  const handleInputChange = (
    field: keyof CreateLeadPayload,
    value: string
  ) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    // Clear field-specific error as user types
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

  // Form submit handler
  const handleSubmitLead = async (e: React.FormEvent) => {
    e.preventDefault();

    // Reset previous errors
    setFormErrors({});
    setFormGeneralError(null);

    const trimmedName = formData.name.trim();
    const trimmedPhone = formData.phone?.trim() || "";
    const trimmedEmail = formData.email?.trim() || "";

    // Client-side quick check (Backend performs the definitive validation)
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

      // On successful creation:
      // 1. Close modal
      setIsModalOpen(false);
      // 2. Reset form data
      setFormData({
        name: "",
        phone: "",
        email: "",
        stage: "NUEVO",
        nextFollowUp: "",
        service: "",
      });
      // 3. Show notification
      setSuccessMessage("¡Lead creado exitosamente!");
      // 4. Refresh lead list from backend
      fetchLeads(debouncedSearch, 0);
      setPage(0);
    } catch (err: any) {
      // NOTE: Form values are NOT erased on error! Preserving user input.
      const apiErr = err as LeadApiError;
      if (apiErr.fieldErrors && Object.keys(apiErr.fieldErrors).length > 0) {
        setFormErrors(apiErr.fieldErrors);
      }
      setFormGeneralError(apiErr.message || "Error al registrar el lead. Revisa los datos.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // Helper for stage badge
  const renderStageBadge = (stage: LeadStage | string) => {
    switch (stage) {
      case "NUEVO":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-sky-500/15 text-sky-300 border border-sky-500/30">
            <span className="w-1.5 h-1.5 rounded-full bg-sky-400 animate-pulse" />
            Nuevo
          </span>
        );
      case "EN_SEGUIMIENTO":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/15 text-amber-300 border border-amber-500/30">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
            En seguimiento
          </span>
        );
      case "CERRADO":
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            Cerrado
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-gray-500/15 text-gray-300 border border-gray-500/30">
            {stage}
          </span>
        );
    }
  };

  return (
    <div className="w-full max-w-full space-y-6">
      {/* ── Top Header & Actions ── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-white drop-shadow-sm">
              Leads
            </h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-white/10 text-white/90 border border-white/15">
              {totalElements} {totalElements === 1 ? "contacto" : "contactos"}
            </span>
          </div>
          <p className="text-sm text-white/70 mt-1">
            Consulta, busca y registra nuevos prospectos comerciales en tiempo real.
          </p>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          {/* Search bar */}
          <div className="relative flex-1 sm:w-72">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-white/50 pointer-events-none" />
            <input
              type="text"
              id="lead-search-input"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Buscar por nombre..."
              aria-label="Buscar leads por nombre"
              className="w-full h-10 pl-9 pr-9 rounded-xl border border-white/15 bg-white/10 text-sm text-white placeholder:text-white/40 focus:outline-none focus:ring-2 focus:ring-white/40 focus:border-transparent transition-all backdrop-blur-md"
            />
            {searchInput && (
              <button
                type="button"
                onClick={() => setSearchInput("")}
                aria-label="Limpiar búsqueda"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 text-white/50 hover:text-white rounded-md transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>

          {/* New Lead Button */}
          <Button
            type="button"
            onClick={handleOpenModal}
            className="shrink-0 h-10 px-4 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-600 hover:to-teal-700 text-white font-medium shadow-lg shadow-emerald-500/20 border border-emerald-400/30 transition-all flex items-center gap-2 cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            <span>Nuevo Lead</span>
          </Button>
        </div>
      </div>

      {/* ── Success Banner ── */}
      {successMessage && (
        <div
          role="status"
          className="flex items-center justify-between p-4 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-200 text-sm backdrop-blur-md animate-in fade-in duration-300"
        >
          <div className="flex items-center gap-2.5">
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
          className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl bg-red-500/20 border border-red-500/40 text-red-200 text-sm backdrop-blur-md animate-in fade-in duration-300"
        >
          <div className="flex items-center gap-2.5">
            <AlertCircle className="h-5 w-5 text-red-400 shrink-0" />
            <div>
              <p className="font-semibold text-white">Error al cargar la lista de leads</p>
              <p className="text-red-200 text-xs sm:text-sm">{error.message}</p>
            </div>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={() => fetchLeads(debouncedSearch, page)}
            className="shrink-0 text-white border-white/20 bg-white/10 hover:bg-white/20 h-8 px-3 text-xs"
          >
            <RefreshCw className="h-3.5 w-3.5 mr-1.5" />
            Reintentar
          </Button>
        </div>
      )}

      {/* ── Main Content Area ── */}
      <div className="w-full">
        {/* Loading State */}
        {isLoading ? (
          <div className="p-8 sm:p-12 text-center rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
            <div className="inline-block animate-spin text-emerald-400 mb-3">
              <RefreshCw className="h-8 w-8" />
            </div>
            <p className="text-white font-medium text-base">Cargando leads...</p>
            <p className="text-white/60 text-xs sm:text-sm mt-1">
              Conectando con el servidor en tiempo real
            </p>
          </div>
        ) : leads.length === 0 ? (
          /* Empty State */
          <div className="p-8 sm:p-14 text-center rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-white/10 flex items-center justify-center text-white/60 border border-white/15">
              <User className="h-8 w-8" />
            </div>
            {debouncedSearch ? (
              <>
                <h3 className="text-lg font-semibold text-white">
                  No se encontraron resultados
                </h3>
                <p className="text-sm text-white/60 mt-1 max-w-md mx-auto">
                  No hay ningún lead que coincida con &ldquo;{debouncedSearch}&rdquo;.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setSearchInput("")}
                  className="mt-4 text-white border-white/20 bg-white/10 hover:bg-white/20"
                >
                  Limpiar búsqueda
                </Button>
              </>
            ) : (
              <>
                <h3 className="text-lg font-semibold text-white">
                  No tienes leads registrados aún
                </h3>
                <p className="text-sm text-white/60 mt-1 max-w-md mx-auto">
                  Registra tu primer lead para comenzar a gestionar tu pipeline comercial y hacer seguimiento a tus ventas.
                </p>
                <Button
                  type="button"
                  onClick={handleOpenModal}
                  className="mt-5 bg-emerald-500 hover:bg-emerald-600 text-white font-medium px-5 rounded-xl shadow-lg shadow-emerald-500/20"
                >
                  <Plus className="h-4 w-4 mr-2" />
                  Crear mi primer lead
                </Button>
              </>
            )}
          </div>
        ) : (
          /* Data Exists: Table (Desktop) & Cards (Mobile) */
          <div className="space-y-4">
            {/* 1. Desktop Table (Hidden on Mobile) */}
            <div className="hidden md:block rounded-2xl border border-white/15 bg-white/5 backdrop-blur-xl overflow-hidden shadow-2xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm text-white/90">
                  <thead className="bg-white/10 text-white/60 text-xs font-semibold uppercase tracking-wider border-b border-white/15">
                    <tr>
                      <th scope="col" className="px-6 py-3.5">
                        Nombre / Servicio
                      </th>
                      <th scope="col" className="px-6 py-3.5">
                        Contacto
                      </th>
                      <th scope="col" className="px-6 py-3.5">
                        Etapa
                      </th>
                      <th scope="col" className="px-6 py-3.5">
                        Próximo Seguimiento
                      </th>
                      <th scope="col" className="px-6 py-3.5 text-right">
                        Acciones
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/10">
                    {leads.map((lead) => (
                      <tr
                        key={lead.id}
                        className="hover:bg-white/[0.08] transition-colors group"
                      >
                        {/* Name & Service */}
                        <td className="px-6 py-4">
                          <div className="font-semibold text-white group-hover:text-emerald-300 transition-colors">
                            {lead.name}
                          </div>
                          {lead.service && (
                            <div className="flex items-center gap-1.5 text-xs text-white/60 mt-0.5">
                              <Briefcase className="h-3 w-3 text-white/40" />
                              <span>{lead.service}</span>
                            </div>
                          )}
                        </td>

                        {/* Contact (Phone / Email) */}
                        <td className="px-6 py-4">
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

                        {/* Stage */}
                        <td className="px-6 py-4 whitespace-nowrap">
                          {renderStageBadge(lead.stage)}
                        </td>

                        {/* Next Follow Up */}
                        <td className="px-6 py-4 whitespace-nowrap">
                          {lead.nextFollowUp ? (
                            <div className="flex items-center gap-1.5 text-xs text-white/90">
                              <Calendar className="h-3.5 w-3.5 text-white/50" />
                              <span>{lead.nextFollowUp}</span>
                            </div>
                          ) : (
                            <span className="text-xs text-white/40 italic">
                              Sin fecha programada
                            </span>
                          )}
                        </td>

                        {/* Actions */}
                        <td className="px-6 py-4 text-right whitespace-nowrap">
                          <Link
                            to={`/app/leads/${lead.id}`}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium text-white/90 bg-white/10 hover:bg-white/20 hover:text-white border border-white/15 transition-all focus:outline-none focus:ring-2 focus:ring-white/40"
                          >
                            <span>Ver detalle</span>
                            <ChevronRight className="h-3.5 w-3.5" />
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 2. Mobile Cards (Visible only on screens < 768px, optimized for 360px) */}
            <div className="block md:hidden space-y-3">
              {leads.map((lead) => (
                <div
                  key={lead.id}
                  className="p-4 rounded-xl border border-white/15 bg-white/5 backdrop-blur-xl space-y-3 transition-colors hover:bg-white/[0.08]"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <h2 className="font-semibold text-white text-base truncate">
                        {lead.name}
                      </h2>
                      {lead.service && (
                        <p className="text-xs text-white/60 flex items-center gap-1 mt-0.5 truncate">
                          <Briefcase className="h-3 w-3 text-white/40 shrink-0" />
                          <span>{lead.service}</span>
                        </p>
                      )}
                    </div>
                    <div className="shrink-0">{renderStageBadge(lead.stage)}</div>
                  </div>

                  {/* Contact Info */}
                  <div className="space-y-1.5 pt-1 border-t border-white/10 text-xs text-white/80">
                    {lead.phone && (
                      <div className="flex items-center gap-2">
                        <Phone className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
                        <a
                          href={`tel:${lead.phone}`}
                          className="hover:underline text-white/90"
                        >
                          {lead.phone}
                        </a>
                      </div>
                    )}
                    {lead.email && (
                      <div className="flex items-center gap-2">
                        <Mail className="h-3.5 w-3.5 text-sky-400 shrink-0" />
                        <a
                          href={`mailto:${lead.email}`}
                          className="hover:underline text-white/90 truncate"
                        >
                          {lead.email}
                        </a>
                      </div>
                    )}
                    {lead.nextFollowUp && (
                      <div className="flex items-center gap-2 text-white/70">
                        <Calendar className="h-3.5 w-3.5 text-white/50 shrink-0" />
                        <span>Seguimiento: {lead.nextFollowUp}</span>
                      </div>
                    )}
                  </div>

                  {/* Card Action Link */}
                  <div className="pt-2">
                    <Link
                      to={`/app/leads/${lead.id}`}
                      className="w-full flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg text-xs font-medium text-white bg-white/10 hover:bg-white/20 border border-white/15 transition-all text-center focus:outline-none focus:ring-2 focus:ring-white/40"
                    >
                      <span>Ver detalle completo</span>
                      <ChevronRight className="h-3.5 w-3.5" />
                    </Link>
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
                  <Button
                    type="button"
                    variant="outline"
                    disabled={page === 0 || isLoading}
                    onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                    className="h-8 px-3 text-xs bg-white/10 text-white border-white/15 disabled:opacity-40"
                  >
                    Anterior
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={page >= totalPages - 1 || isLoading}
                    onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
                    className="h-8 px-3 text-xs bg-white/10 text-white border-white/15 disabled:opacity-40"
                  >
                    Siguiente
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Modal / Dialog: Crear Lead ── */}
      {isModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-lead-title"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-in fade-in duration-200"
        >
          <div
            className="w-full max-w-lg rounded-2xl bg-[#081824] border border-white/20 p-6 sm:p-7 shadow-2xl text-white space-y-5 relative max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div className="flex items-start justify-between border-b border-white/10 pb-4">
              <div>
                <h2 id="new-lead-title" className="text-xl font-bold text-white">
                  Registrar Nuevo Lead
                </h2>
                <p className="text-xs sm:text-sm text-white/60 mt-0.5">
                  Completa los datos del prospecto comercial.
                </p>
              </div>
              <button
                type="button"
                onClick={handleCloseModal}
                disabled={isSubmitting}
                aria-label="Cerrar modal"
                className="text-white/60 hover:text-white p-1 rounded-lg hover:bg-white/10 transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* General Server Error Banner inside Form */}
            {formGeneralError && (
              <div
                role="alert"
                className="p-3.5 rounded-xl bg-red-500/20 border border-red-500/40 text-red-200 text-xs sm:text-sm flex items-start gap-2.5"
              >
                <AlertCircle className="h-5 w-5 text-red-400 shrink-0 mt-0.5" />
                <div className="flex-1 font-medium leading-relaxed">
                  {formGeneralError}
                </div>
              </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmitLead} className="space-y-4" noValidate>
              {/* Field: Nombre (Name) */}
              <div className="space-y-1.5">
                <Label htmlFor="lead-name" className="text-xs font-semibold text-white/90">
                  Nombre completo <span className="text-red-400">*</span>
                </Label>
                <Input
                  id="lead-name"
                  type="text"
                  required
                  autoFocus
                  placeholder="Ej: Laura Mendoza"
                  value={formData.name}
                  onChange={(e) => handleInputChange("name", e.target.value)}
                  disabled={isSubmitting}
                  className={`bg-white/10 border-white/20 text-white placeholder:text-white/30 h-10 ${
                    formErrors.name ? "border-red-400 ring-1 ring-red-400" : ""
                  }`}
                  aria-invalid={!!formErrors.name}
                  aria-describedby={formErrors.name ? "lead-name-error" : undefined}
                />
                {formErrors.name && (
                  <p id="lead-name-error" role="alert" className="text-xs text-red-400 font-medium">
                    {formErrors.name}
                  </p>
                )}
              </div>

              {/* Two columns: Phone & Email */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Field: Phone */}
                <div className="space-y-1.5">
                  <Label htmlFor="lead-phone" className="text-xs font-semibold text-white/90">
                    Teléfono
                  </Label>
                  <Input
                    id="lead-phone"
                    type="tel"
                    placeholder="Ej: 998877665"
                    value={formData.phone || ""}
                    onChange={(e) => handleInputChange("phone", e.target.value)}
                    disabled={isSubmitting}
                    className={`bg-white/10 border-white/20 text-white placeholder:text-white/30 h-10 ${
                      formErrors.phone ? "border-red-400 ring-1 ring-red-400" : ""
                    }`}
                    aria-invalid={!!formErrors.phone}
                    aria-describedby={formErrors.phone ? "lead-phone-error" : undefined}
                  />
                  {formErrors.phone && (
                    <p id="lead-phone-error" role="alert" className="text-xs text-red-400 font-medium">
                      {formErrors.phone}
                    </p>
                  )}
                </div>

                {/* Field: Email */}
                <div className="space-y-1.5">
                  <Label htmlFor="lead-email" className="text-xs font-semibold text-white/90">
                    Correo electrónico
                  </Label>
                  <Input
                    id="lead-email"
                    type="email"
                    placeholder="laura@empresa.com"
                    value={formData.email || ""}
                    onChange={(e) => handleInputChange("email", e.target.value)}
                    disabled={isSubmitting}
                    className={`bg-white/10 border-white/20 text-white placeholder:text-white/30 h-10 ${
                      formErrors.email ? "border-red-400 ring-1 ring-red-400" : ""
                    }`}
                    aria-invalid={!!formErrors.email}
                    aria-describedby={formErrors.email ? "lead-email-error" : undefined}
                  />
                  {formErrors.email && (
                    <p id="lead-email-error" role="alert" className="text-xs text-red-400 font-medium">
                      {formErrors.email}
                    </p>
                  )}
                </div>
              </div>

              <p className="text-[11px] text-white/50 italic">
                * Debe ingresar al menos un teléfono o un correo electrónico para registrar el lead.
              </p>

              {/* Two columns: Stage & Follow Up */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Field: Stage */}
                <div className="space-y-1.5">
                  <Label htmlFor="lead-stage" className="text-xs font-semibold text-white/90">
                    Etapa comercial <span className="text-red-400">*</span>
                  </Label>
                  <select
                    id="lead-stage"
                    value={formData.stage}
                    onChange={(e) =>
                      handleInputChange("stage", e.target.value as LeadStage)
                    }
                    disabled={isSubmitting}
                    className="w-full h-10 px-3 rounded-md bg-[#0d2232] border border-white/20 text-white text-sm focus:outline-none focus:ring-1 focus:ring-white/40"
                  >
                    <option value="NUEVO">NUEVO</option>
                    <option value="EN_SEGUIMIENTO">EN SEGUIMIENTO</option>
                    <option value="CERRADO">CERRADO</option>
                  </select>
                  {formErrors.stage && (
                    <p role="alert" className="text-xs text-red-400 font-medium">
                      {formErrors.stage}
                    </p>
                  )}
                </div>

                {/* Field: Next Follow Up Date */}
                <div className="space-y-1.5">
                  <Label htmlFor="lead-followup" className="text-xs font-semibold text-white/90">
                    Próximo seguimiento
                  </Label>
                  <Input
                    id="lead-followup"
                    type="date"
                    value={formData.nextFollowUp || ""}
                    onChange={(e) => handleInputChange("nextFollowUp", e.target.value)}
                    disabled={isSubmitting || formData.stage === "CERRADO"}
                    className={`bg-white/10 border-white/20 text-white h-10 ${
                      formData.stage === "CERRADO" ? "opacity-40 cursor-not-allowed" : ""
                    }`}
                  />
                  {formData.stage === "CERRADO" && (
                    <p className="text-[11px] text-white/40">
                      Un lead cerrado no requiere fecha de seguimiento.
                    </p>
                  )}
                  {formErrors.nextFollowUp && (
                    <p role="alert" className="text-xs text-red-400 font-medium">
                      {formErrors.nextFollowUp}
                    </p>
                  )}
                </div>
              </div>

              {/* Field: Service */}
              <div className="space-y-1.5">
                <Label htmlFor="lead-service" className="text-xs font-semibold text-white/90">
                  Servicio de interés (Opcional)
                </Label>
                <Input
                  id="lead-service"
                  type="text"
                  placeholder="Ej: Consultoría Cloud, Auditoría de Seguridad"
                  value={formData.service || ""}
                  onChange={(e) => handleInputChange("service", e.target.value)}
                  disabled={isSubmitting}
                  className="bg-white/10 border-white/20 text-white placeholder:text-white/30 h-10"
                />
              </div>

              {/* Form Action Buttons */}
              <div className="flex items-center justify-end gap-3 pt-4 border-t border-white/10">
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleCloseModal}
                  disabled={isSubmitting}
                  className="h-10 px-4 text-sm text-white/80 border-white/20 bg-white/5 hover:bg-white/15"
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  disabled={isSubmitting}
                  className="h-10 px-5 text-sm bg-gradient-to-r from-emerald-500 to-teal-600 hover:from-emerald-600 hover:to-teal-700 text-white font-medium rounded-xl shadow-lg shadow-emerald-500/20 border border-emerald-400/30 flex items-center gap-2 cursor-pointer disabled:opacity-50"
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
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
