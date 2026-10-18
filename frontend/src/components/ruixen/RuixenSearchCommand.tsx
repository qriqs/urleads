import React, { useState, useRef, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search,
  X,
  User,
  Home,
  Users,
  Calendar,
  BarChart2,
  CheckSquare,
  Settings,
  Activity,
  ArrowRight,
  Phone,
  Mail,
} from "lucide-react";
import { Lead } from "../../services/leads";

export interface RuixenSearchCommandProps {
  leads: Lead[];
  onOpenPipelineModal?: () => void;
  className?: string;
}

interface NavItem {
  id: string;
  title: string;
  category: string;
  path: string;
  icon: React.ComponentType<{ className?: string }>;
  isSpecial?: boolean;
}

const APP_NAVIGATION_ITEMS: NavItem[] = [
  { id: "nav-resumen", title: "Resumen Comercial", category: "Navegación", path: "/app", icon: Home },
  { id: "nav-leads", title: "Listado de Leads", category: "Navegación", path: "/app/leads", icon: Users },
  { id: "nav-followups", title: "Seguimientos para Hoy", category: "Navegación", path: "/app/leads?followUp=today", icon: Calendar },
  { id: "nav-reports", title: "Reportes & Analítica", category: "Navegación", path: "/app/reportes", icon: BarChart2 },
  { id: "nav-tasks", title: "Tareas Comerciales", category: "Navegación", path: "/app/tareas", icon: CheckSquare },
  { id: "nav-pipeline", title: "Desglose del Pipeline", category: "Navegación", path: "/app", icon: Activity, isSpecial: true },
  { id: "nav-settings", title: "Ajustes & Preferencias", category: "Navegación", path: "/app/ajustes", icon: Settings },
];

export function RuixenSearchCommand({
  leads,
  onOpenPipelineModal,
  className = "",
}: RuixenSearchCommandProps) {
  const [query, setQuery] = useState("");
  const [isOpen, setIsOpen] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);

  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  // Clear all handler (called when clicking ⌘K or when query exists)
  const handleClearAll = (e?: React.MouseEvent) => {
    e?.stopPropagation();
    setQuery("");
    setIsOpen(false);
    inputRef.current?.focus();
  };

  // Keyboard shortcut: Cmd+K / Ctrl+K
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        if (query) {
          handleClearAll();
        } else {
          inputRef.current?.focus();
          setIsOpen(true);
        }
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [query]);

  // Click outside to close
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // Filtered navigation and lead suggestions
  const { matchedNav, matchedLeads } = useMemo(() => {
    if (!query.trim()) {
      return { matchedNav: [], matchedLeads: [] };
    }
    const q = query.toLowerCase().trim();

    const nav = APP_NAVIGATION_ITEMS.filter((item) =>
      item.title.toLowerCase().includes(q)
    );

    const lds = leads.filter((lead) => {
      const nameMatch = lead.name.toLowerCase().includes(q);
      const emailMatch = lead.email ? lead.email.toLowerCase().includes(q) : false;
      const phoneMatch = lead.phone ? lead.phone.includes(q) : false;
      const serviceMatch = lead.service ? lead.service.toLowerCase().includes(q) : false;
      const stageMatch = lead.stage.toLowerCase().includes(q);
      return nameMatch || emailMatch || phoneMatch || serviceMatch || stageMatch;
    }).slice(0, 6);

    return { matchedNav: nav, matchedLeads: lds };
  }, [query, leads]);

  const totalResults = matchedNav.length + matchedLeads.length;

  // Handle item selection
  const handleSelectNav = (item: NavItem) => {
    if (item.isSpecial && onOpenPipelineModal) {
      onOpenPipelineModal();
    } else {
      navigate(item.path);
    }
    setIsOpen(false);
    setQuery("");
  };

  const handleSelectLead = (leadId: number) => {
    navigate(`/app/leads/${leadId}`);
    setIsOpen(false);
    setQuery("");
  };

  const handleFullSearch = () => {
    if (query.trim()) {
      navigate(`/app/leads?search=${encodeURIComponent(query.trim())}`);
      setIsOpen(false);
    }
  };

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Search Input Container */}
      <div className="relative h-10 px-3.5 rounded-full bg-secondary/60 border border-border/70 focus-within:border-accent/60 focus-within:ring-2 focus-within:ring-accent/20 transition-all flex items-center justify-between text-muted-foreground gap-2">
        <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
        
        <input
          ref={inputRef}
          type="text"
          value={query}
          onFocus={() => {
            if (query.trim()) setIsOpen(true);
          }}
          onChange={(e) => {
            setQuery(e.target.value);
            setIsOpen(true);
            setSelectedIndex(0);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              handleFullSearch();
            } else if (e.key === "Escape") {
              setIsOpen(false);
            }
          }}
          placeholder="Buscar prospectos, notas, vistas..."
          className="bg-transparent border-none outline-none text-xs sm:text-sm text-foreground placeholder:text-muted-foreground w-full font-medium"
        />

        {/* ⌘K Badge Button (Acts as "Borrar Todo" when text is entered) */}
        <button
          type="button"
          onClick={handleClearAll}
          title={query ? "Borrar todo (⌘K)" : "Atajo de teclado: ⌘K"}
          className={`px-2 py-0.5 text-[10px] font-semibold rounded-full shrink-0 transition-all cursor-pointer select-none flex items-center gap-1 ${
            query
              ? "bg-rose-500/15 text-rose-600 hover:bg-rose-500 hover:text-white border border-rose-300 shadow-2xs"
              : "bg-background border border-border text-muted-foreground hover:text-foreground shadow-2xs"
          }`}
        >
          {query ? (
            <>
              <span>Borrar</span>
              <X className="h-3 w-3" />
            </>
          ) : (
            <span>⌘K</span>
          )}
        </button>
      </div>

      {/* Indexed Suggestions Dropdown */}
      {isOpen && query.trim().length > 0 && (
        <div className="absolute top-12 left-0 right-0 z-50 bg-background/95 backdrop-blur-xl border border-border rounded-3xl shadow-xl overflow-hidden p-3 space-y-3 animate-in fade-in zoom-in-95 duration-150 max-h-96 overflow-y-auto">
          {totalResults === 0 ? (
            <div className="p-6 text-center text-xs text-muted-foreground">
              <p className="font-semibold text-foreground">No hay coincidencias exactas para "{query}"</p>
              <button
                type="button"
                onClick={handleFullSearch}
                className="mt-2.5 px-4 py-1.5 rounded-full bg-secondary hover:bg-secondary/80 text-foreground font-semibold inline-flex items-center gap-1.5 transition-colors cursor-pointer"
              >
                <span>Buscar en todos los leads</span>
                <ArrowRight className="h-3 w-3" />
              </button>
            </div>
          ) : (
            <>
              {/* Navigation Matches */}
              {matchedNav.length > 0 && (
                <div>
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider px-2">
                    Vistas & Herramientas
                  </span>
                  <div className="mt-1 space-y-1">
                    {matchedNav.map((item) => {
                      const Icon = item.icon;
                      return (
                        <div
                          key={item.id}
                          onClick={() => handleSelectNav(item)}
                          className="flex items-center justify-between p-2.5 rounded-2xl hover:bg-secondary cursor-pointer transition-colors group"
                        >
                          <div className="flex items-center gap-2.5">
                            <div className="h-7 w-7 rounded-full bg-secondary group-hover:bg-accent/15 group-hover:text-accent flex items-center justify-center text-muted-foreground transition-colors">
                              <Icon className="h-3.5 w-3.5" />
                            </div>
                            <span className="text-xs sm:text-sm font-semibold text-foreground">
                              {item.title}
                            </span>
                          </div>
                          <ArrowRight className="h-3.5 w-3.5 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Leads Matches */}
              {matchedLeads.length > 0 && (
                <div>
                  <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider px-2">
                    Prospectos Coincidentes ({matchedLeads.length})
                  </span>
                  <div className="mt-1 space-y-1">
                    {matchedLeads.map((lead) => (
                      <div
                        key={lead.id}
                        onClick={() => handleSelectLead(lead.id)}
                        className="flex items-center justify-between p-2.5 rounded-2xl hover:bg-secondary cursor-pointer transition-colors group"
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          <div className="h-7 w-7 rounded-full bg-accent/15 text-accent flex items-center justify-center font-bold text-[11px] shrink-0">
                            {lead.name.slice(0, 1).toUpperCase()}
                          </div>
                          <div className="truncate">
                            <p className="text-xs sm:text-sm font-semibold text-foreground truncate">
                              {lead.name}
                            </p>
                            <p className="text-[11px] text-muted-foreground truncate">
                              {lead.service || lead.phone || lead.email || "Lead comercial"}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          <span
                            className={`px-2 py-0.5 rounded-full text-[10px] font-bold border ${
                              lead.stage === "CERRADO"
                                ? "bg-emerald-500/10 text-emerald-700 border-emerald-500/20"
                                : lead.stage === "EN_SEGUIMIENTO"
                                ? "bg-amber-500/10 text-amber-700 border-amber-500/20"
                                : "bg-sky-500/10 text-sky-700 border-sky-500/20"
                            }`}
                          >
                            {lead.stage}
                          </span>
                          <ArrowRight className="h-3.5 w-3.5 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Footer CTA */}
              <div className="pt-2 border-t border-border flex items-center justify-between px-2">
                <span className="text-[11px] text-muted-foreground">
                  Presiona <kbd className="px-1.5 py-0.5 rounded bg-secondary font-mono text-[10px]">↵</kbd> para buscar todo
                </span>
                <button
                  type="button"
                  onClick={handleFullSearch}
                  className="text-xs font-semibold text-accent hover:underline flex items-center gap-1 cursor-pointer"
                >
                  <span>Ver todos los resultados</span>
                  <ArrowRight className="h-3 w-3" />
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

export default RuixenSearchCommand;
