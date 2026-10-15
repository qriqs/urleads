import React, { useState, useRef, useEffect } from "react";
import { ChevronDown, Check } from "lucide-react";

export interface GlassSelectOption {
  value: string;
  label: string;
  description?: string;
  colorDot?: string;
}

interface GlassSelectProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  options: GlassSelectOption[];
  disabled?: boolean;
  placeholder?: string;
}

export const GlassSelect: React.FC<GlassSelectProps> = ({
  id,
  value,
  onChange,
  options,
  disabled = false,
  placeholder = "Seleccionar...",
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((opt) => opt.value === value);

  // Close when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsOpen(false);
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
        setIsOpen(false);
      }
    };
    if (isOpen) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  const handleSelect = (optionValue: string) => {
    onChange(optionValue);
    setIsOpen(false);
  };

  return (
    <div ref={containerRef} className="relative w-full">
      {/* Trigger Button */}
      <button
        type="button"
        id={id}
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        className={`w-full h-[52px] px-5 rounded-2xl liquid-glass-input flex items-center justify-between text-left transition-all cursor-pointer ${
          disabled ? "opacity-40 cursor-not-allowed" : "hover:border-white/40"
        } ${isOpen ? "border-white/60 ring-2 ring-white/20" : ""}`}
      >
        <div className="flex items-center gap-2.5 min-w-0">
          {selectedOption?.colorDot && (
            <span
              className={`w-2.5 h-2.5 rounded-full shrink-0 ${selectedOption.colorDot}`}
            />
          )}
          <span className="text-[15px] font-medium text-white truncate">
            {selectedOption ? selectedOption.label : placeholder}
          </span>
        </div>

        <ChevronDown
          className={`h-4 w-4 text-white/70 shrink-0 transition-transform duration-200 ${
            isOpen ? "rotate-180 text-white" : ""
          }`}
        />
      </button>

      {/* Liquid Glass Dropdown Menu */}
      {isOpen && (
        <div
          role="listbox"
          tabIndex={-1}
          className="glass-dropdown-menu absolute top-[calc(100%+8px)] left-0 right-0 z-50 rounded-2xl p-2 space-y-1 animate-in fade-in zoom-in-95 duration-150"
          style={{
            background:
              "linear-gradient(160deg, rgba(255, 255, 255, 0.22) 0%, rgba(255, 255, 255, 0.08) 100%), rgba(30, 42, 54, 0.94)",
            backdropFilter: "blur(36px) saturate(135%)",
            WebkitBackdropFilter: "blur(36px) saturate(135%)",
            border: "1px solid rgba(255, 255, 255, 0.30)",
            boxShadow:
              "0 25px 60px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.45)",
          }}
        >
          {options.map((option) => {
            const isSelected = option.value === value;
            return (
              <div
                key={option.value}
                role="option"
                aria-selected={isSelected}
                onClick={() => handleSelect(option.value)}
                className={`w-full px-4 py-3 rounded-xl flex items-center justify-between transition-all cursor-pointer ${
                  isSelected
                    ? "bg-white/25 border border-white/35 text-white font-semibold shadow-sm"
                    : "text-white/80 hover:bg-white/15 hover:text-white"
                }`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  {option.colorDot && (
                    <span
                      className={`w-2.5 h-2.5 rounded-full shrink-0 ${option.colorDot}`}
                    />
                  )}
                  <div className="min-w-0">
                    <div className="text-[14px] leading-tight text-white font-medium">
                      {option.label}
                    </div>
                    {option.description && (
                      <div className="text-[11px] text-white/60 mt-0.5 truncate">
                        {option.description}
                      </div>
                    )}
                  </div>
                </div>

                {isSelected && (
                  <Check className="h-4 w-4 text-white shrink-0 ml-2" />
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
