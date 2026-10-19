import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LeadStage } from '../../services/leads';

export interface RecentLeadDisplayItem {
  id: number;
  date: string;
  name: string;
  service: string;
  stage: LeadStage;
  stageLabel: string;
  stageBadgeClass: string;
}

interface RecentLeadsTableProps {
  leads: RecentLeadDisplayItem[];
}

export const RecentLeadsTable: React.FC<RecentLeadsTableProps> = ({ leads }) => {
  const navigate = useNavigate();

  return (
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
            {leads.length === 0 ? (
              <tr>
                <td colSpan={4} className="py-8 text-center text-muted-foreground">
                  No hay prospectos registrados aún.
                </td>
              </tr>
            ) : (
              leads.map((lead) => (
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
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
