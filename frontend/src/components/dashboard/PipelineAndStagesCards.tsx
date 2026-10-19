import React from 'react';
import { Link } from 'react-router-dom';
import { Check, Plus, MoreVertical } from 'lucide-react';

interface PipelineAndStagesCardsProps {
  rawPipelineValue: number;
  totalLeadsCount: number;
  newCount: number;
  inProgressCount: number;
  closedCount: number;
  formatMoney: (val: number) => string;
  onOpenPipelineModal: () => void;
  onOpenCreateModal: () => void;
  onNavigateLeads: () => void;
}

export const PipelineAndStagesCards: React.FC<PipelineAndStagesCardsProps> = ({
  rawPipelineValue,
  totalLeadsCount,
  newCount,
  inProgressCount,
  closedCount,
  formatMoney,
  onOpenPipelineModal,
  onOpenCreateModal,
  onNavigateLeads,
}) => {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      {/* Pipeline Card: Clickable with live currency calculation */}
      <div
        onClick={onOpenPipelineModal}
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
              onClick={onOpenCreateModal}
              className="h-8 w-8 rounded-full flex items-center justify-center hover:text-foreground hover:bg-secondary cursor-pointer transition-colors"
              title="Registrar prospecto"
            >
              <Plus className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={onNavigateLeads}
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
  );
};
