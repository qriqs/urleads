import React from 'react';
import { Plus, Phone, Mail, Calendar, FileText, CheckSquare } from 'lucide-react';

interface QuickActionsBarProps {
  onOpenNewLead: () => void;
  onOpenCall: () => void;
  onOpenEmail: () => void;
  onOpenMeeting: () => void;
  onOpenNote: () => void;
  onOpenTask: () => void;
}

export const QuickActionsBar: React.FC<QuickActionsBarProps> = ({
  onOpenNewLead,
  onOpenCall,
  onOpenEmail,
  onOpenMeeting,
  onOpenNote,
  onOpenTask,
}) => {
  return (
    <div className="flex items-center gap-2 sm:gap-2.5 overflow-x-auto no-scrollbar pb-1">
      <button
        type="button"
        onClick={onOpenNewLead}
        className="h-10 px-5 rounded-full bg-accent text-accent-foreground font-semibold text-xs sm:text-sm flex items-center gap-2 shadow-xs hover:opacity-90 active:scale-95 transition-all cursor-pointer shrink-0"
      >
        <Plus className="h-3.5 w-3.5 stroke-[2.5]" />
        <span>Nuevo Lead</span>
      </button>

      <button
        type="button"
        onClick={onOpenCall}
        className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
      >
        <Phone className="h-3.5 w-3.5 text-sky-500" />
        <span>Llamada</span>
      </button>

      <button
        type="button"
        onClick={onOpenEmail}
        className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
      >
        <Mail className="h-3.5 w-3.5 text-indigo-500" />
        <span>Email</span>
      </button>

      <button
        type="button"
        onClick={onOpenMeeting}
        className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
      >
        <Calendar className="h-3.5 w-3.5 text-emerald-500" />
        <span>Cita</span>
      </button>

      <button
        type="button"
        onClick={onOpenNote}
        className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
      >
        <FileText className="h-3.5 w-3.5 text-amber-500" />
        <span>Nota</span>
      </button>

      <button
        type="button"
        onClick={onOpenTask}
        className="h-10 px-4.5 rounded-full bg-background border border-border text-foreground font-medium text-xs sm:text-sm flex items-center gap-2 shadow-2xs hover:bg-secondary active:scale-95 transition-all cursor-pointer shrink-0"
      >
        <CheckSquare className="h-3.5 w-3.5 text-purple-500" />
        <span>Tarea</span>
      </button>
    </div>
  );
};
