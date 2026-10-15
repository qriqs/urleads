import React, { useState, useRef, useEffect } from "react";
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight, X } from "lucide-react";

interface GlassDatePickerProps {
  id?: string;
  value: string; // ISO format "YYYY-MM-DD"
  onChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
  onOpenChange?: (isOpen: boolean) => void;
}

const MONTH_NAMES = [
  "Enero",
  "Febrero",
  "Marzo",
  "Abril",
  "Mayo",
  "Junio",
  "Julio",
  "Agosto",
  "Septiembre",
  "Octubre",
  "Noviembre",
  "Diciembre",
];

const WEEKDAYS = ["Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do"];

export const GlassDatePicker: React.FC<GlassDatePickerProps> = ({
  id,
  value,
  onChange,
  disabled = false,
  placeholder = "Seleccionar fecha...",
  onOpenChange,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const updateOpenState = (next: boolean) => {
    setIsOpen(next);
    onOpenChange?.(next);
  };

  // Parse current value without timezone offset issues
  const parseDate = (str: string) => {
    if (!str) return null;
    const parts = str.split("-");
    if (parts.length !== 3) return null;
    const y = parseInt(parts[0], 10);
    const m = parseInt(parts[1], 10) - 1;
    const d = parseInt(parts[2], 10);
    return { year: y, month: m, day: d };
  };

  const parsed = parseDate(value);
  const today = new Date();
  const currentYear = today.getFullYear();
  const currentMonth = today.getMonth();
  const currentDay = today.getDate();

  const [viewYear, setViewYear] = useState<number>(parsed?.year || currentYear);
  const [viewMonth, setViewMonth] = useState<number>(
    parsed?.month ?? currentMonth
  );

  // Sync view if value changes externally
  useEffect(() => {
    if (parsed) {
      setViewYear(parsed.year);
      setViewMonth(parsed.month);
    }
  }, [value]);

  // Close when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        updateOpenState(false);
      }
    };

    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen]);

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && isOpen) {
        updateOpenState(false);
      }
    };
    if (isOpen) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  const handlePrevMonth = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear((prev) => prev - 1);
    } else {
      setViewMonth((prev) => prev - 1);
    }
  };

  const handleNextMonth = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear((prev) => prev + 1);
    } else {
      setViewMonth((prev) => prev + 1);
    }
  };

  const handleSelectDay = (day: number) => {
    const pad = (n: number) => n.toString().padStart(2, "0");
    const dateStr = `${viewYear}-${pad(viewMonth + 1)}-${pad(day)}`;
    onChange(dateStr);
    updateOpenState(false);
  };

  const handleSelectToday = (e: React.MouseEvent) => {
    e.stopPropagation();
    const pad = (n: number) => n.toString().padStart(2, "0");
    const dateStr = `${currentYear}-${pad(currentMonth + 1)}-${pad(currentDay)}`;
    onChange(dateStr);
    setViewYear(currentYear);
    setViewMonth(currentMonth);
    updateOpenState(false);
  };

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    onChange("");
    updateOpenState(false);
  };

  // Format display text: "15 oct. 2026"
  const getDisplayText = () => {
    if (!parsed) return "";
    const monthShort = MONTH_NAMES[parsed.month]?.slice(0, 3).toLowerCase();
    return `${parsed.day} ${monthShort}. ${parsed.year}`;
  };

  // Build calendar matrix
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const firstDayOfWeek = (new Date(viewYear, viewMonth, 1).getDay() + 6) % 7; // Monday=0
  const daysInPrevMonth = new Date(viewYear, viewMonth, 0).getDate();

  const calendarDays: Array<{
    day: number;
    isCurrentMonth: boolean;
    isSelected: boolean;
    isToday: boolean;
  }> = [];

  // Trailing days from previous month
  for (let i = firstDayOfWeek - 1; i >= 0; i--) {
    calendarDays.push({
      day: daysInPrevMonth - i,
      isCurrentMonth: false,
      isSelected: false,
      isToday: false,
    });
  }

  // Days of current month
  for (let d = 1; d <= daysInMonth; d++) {
    const isSelected =
      parsed !== null &&
      parsed.year === viewYear &&
      parsed.month === viewMonth &&
      parsed.day === d;
    const isToday =
      currentYear === viewYear && currentMonth === viewMonth && currentDay === d;
    calendarDays.push({
      day: d,
      isCurrentMonth: true,
      isSelected,
      isToday,
    });
  }

  // Next month leading days to complete rows (up to multiple of 7)
  const remaining = (7 - (calendarDays.length % 7)) % 7;
  for (let d = 1; d <= remaining; d++) {
    calendarDays.push({
      day: d,
      isCurrentMonth: false,
      isSelected: false,
      isToday: false,
    });
  }

  return (
    <div ref={containerRef} className="relative w-full">
      {/* Trigger Button */}
      <button
        type="button"
        id={id}
        disabled={disabled}
        onClick={() => updateOpenState(!isOpen)}
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        className={`w-full h-[52px] px-5 rounded-2xl liquid-glass-input flex items-center justify-between text-left transition-all cursor-pointer ${
          disabled ? "opacity-35 cursor-not-allowed" : "hover:border-white/40"
        } ${isOpen ? "border-white/60 ring-2 ring-white/20" : ""}`}
      >
        <div className="flex items-center gap-3 min-w-0">
          <CalendarIcon className="h-4 w-4 text-white/80 shrink-0" />
          <span
            className={`text-[15px] truncate ${
              value ? "text-white font-medium" : "text-white/50 font-normal"
            }`}
          >
            {value ? getDisplayText() : placeholder}
          </span>
        </div>

        {value && !disabled && (
          <span
            onClick={handleClear}
            title="Limpiar fecha"
            className="w-6 h-6 rounded-full bg-white/10 hover:bg-white/25 flex items-center justify-center text-white/70 hover:text-white transition-all cursor-pointer shrink-0 ml-2"
          >
            <X className="h-3 w-3" />
          </span>
        )}
      </button>

      {/* Floating Transparent Liquid Glass Calendar Popover */}
      {isOpen && (
        <div
          role="dialog"
          aria-label="Selector de calendario"
          className="glass-calendar-popover absolute top-[calc(100%+8px)] left-0 sm:left-auto sm:right-0 z-50 rounded-2xl p-3.5 w-[290px] animate-in fade-in zoom-in-95 duration-150"
          style={{
            background:
              "linear-gradient(135deg, rgba(255, 255, 255, 0.20) 0%, rgba(255, 255, 255, 0.08) 100%)",
            backdropFilter: "blur(28px) saturate(130%)",
            WebkitBackdropFilter: "blur(28px) saturate(130%)",
            border: "1px solid rgba(255, 255, 255, 0.28)",
            boxShadow:
              "0 20px 50px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.35)",
          }}
        >
          {/* Calendar Header */}
          <div className="flex items-center justify-between pb-2.5 mb-1.5 border-b border-white/15">
            <button
              type="button"
              onClick={handlePrevMonth}
              aria-label="Mes anterior"
              className="w-7 h-7 rounded-full bg-white/10 hover:bg-white/20 border border-white/20 flex items-center justify-center text-white/80 hover:text-white transition-all cursor-pointer"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
            </button>

            <div className="inline-flex items-center px-3 py-0.5 rounded-full text-xs font-semibold bg-white/15 border border-white/25 text-white backdrop-blur-md shadow-sm">
              <span>
                {MONTH_NAMES[viewMonth]} {viewYear}
              </span>
            </div>

            <button
              type="button"
              onClick={handleNextMonth}
              aria-label="Mes siguiente"
              className="w-7 h-7 rounded-full bg-white/10 hover:bg-white/20 border border-white/20 flex items-center justify-center text-white/80 hover:text-white transition-all cursor-pointer"
            >
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>

          {/* Weekday Labels */}
          <div className="grid grid-cols-7 gap-1 text-center mb-1">
            {WEEKDAYS.map((w) => (
              <span
                key={w}
                className="text-[10px] font-semibold text-white/60 uppercase tracking-wider py-0.5"
              >
                {w}
              </span>
            ))}
          </div>

          {/* Days Grid */}
          <div className="grid grid-cols-7 gap-1 text-center">
            {calendarDays.map((item, idx) => {
              if (!item.isCurrentMonth) {
                return (
                  <div
                    key={`muted-${idx}`}
                    className="w-8 h-8 flex items-center justify-center text-[11px] text-white/20 select-none cursor-default"
                  >
                    {item.day}
                  </div>
                );
              }

              return (
                <button
                  key={`day-${item.day}`}
                  type="button"
                  onClick={() => handleSelectDay(item.day)}
                  className={`w-8 h-8 rounded-full flex flex-col items-center justify-center text-[11px] font-medium transition-all cursor-pointer relative ${
                    item.isSelected
                      ? "aurora-chip-btn bg-white/35 text-white font-bold border-white/60 shadow-md scale-105"
                      : "text-white/90 hover:bg-white/20 hover:text-white hover:scale-105"
                  }`}
                >
                  <span>{item.day}</span>
                  {item.isToday && !item.isSelected && (
                    <span className="w-1 h-1 rounded-full bg-cyan-300 absolute bottom-0.5" />
                  )}
                </button>
              );
            })}
          </div>

          {/* Footer Toolbar: Hoy / Limpiar */}
          <div className="flex items-center justify-between pt-2.5 mt-2 border-t border-white/15">
            <button
              type="button"
              onClick={handleClear}
              className="px-2.5 py-0.5 rounded-full bg-white/10 hover:bg-white/20 border border-white/20 text-white/70 hover:text-white text-[11px] font-medium transition-all cursor-pointer"
            >
              Limpiar
            </button>

            <button
              type="button"
              onClick={handleSelectToday}
              className="aurora-chip-btn h-6 px-3 text-[11px] font-semibold"
            >
              Hoy
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
