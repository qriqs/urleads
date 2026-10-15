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
  size?: "default" | "sm";
  className?: string;
  onOpenChange?: (isOpen: boolean) => void;
}

export const GlassSelect: React.FC<GlassSelectProps> = ({
  id,
  value,
  onChange,
  options,
  disabled = false,
  placeholder = "Seleccionar...",
  size = "default",
  className = "",
  onOpenChange,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((opt) => opt.value === value);

  const updateOpenState = (next: boolean) => {
    setIsOpen(next);
    onOpenChange?.(next);
  };

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

  const handleSelect = (optionValue: string) => {
    onChange(optionValue);
    updateOpenState(false);
  };

  const isSmall = size === "sm";

  return (
    <div ref={containerRef} className={`relative w-full ${className}`}>
      {/* Trigger Button */}
      <button
        type="button"
        id={id}
        disabled={disabled}
        onClick={() => updateOpenState(!isOpen)}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        className={`w-full ${
          isSmall
            ? "h-10 px-3.5 rounded-xl text-xs sm:text-sm"
            : "h-[52px] px-5 rounded-2xl text-[15px]"
        } liquid-glass-input flex items-center justify-between text-left transition-all cursor-pointer ${
          disabled ? "opacity-40 cursor-not-allowed" : "hover:border-white/40"
        } ${isOpen ? "border-white/60 ring-2 ring-white/20" : ""}`}
      >
        <div className="flex items-center gap-2 min-w-0">
          {selectedOption?.colorDot && (
            <span
              className={`w-2 h-2 rounded-full shrink-0 ${selectedOption.colorDot}`}
            />
          )}
          <span
            className={`font-medium truncate ${
              isSmall ? "text-xs sm:text-sm" : "text-[15px]"
            } ${selectedOption ? "text-white" : "text-white/60"}`}
          >
            {selectedOption ? selectedOption.label : placeholder}
          </span>
        </div>

        <ChevronDown
          className={`h-3.5 w-3.5 text-white/70 shrink-0 transition-transform duration-200 ml-1.5 ${
            isOpen ? "rotate-180 text-white" : ""
          }`}
        />
      </button>

      {/* Transparent Liquid Glass Dropdown Menu */}
      {isOpen && (
        <div
          role="listbox"
          tabIndex={-1}
          className={`glass-dropdown-menu absolute left-0 right-0 z-50 rounded-2xl p-1.5 space-y-1 ${
            isSmall
              ? "top-[calc(100%+6px)] min-w-[210px]"
              : "top-[calc(100%+8px)] p-2"
          }`}
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
          {options.map((option) => {
            const isSelected = option.value === value;
            return (
              <div
                key={option.value}
                role="option"
                aria-selected={isSelected}
                onClick={() => handleSelect(option.value)}
                className={`w-full ${
                  isSmall ? "px-3 py-2 rounded-lg" : "px-4 py-3 rounded-xl"
                } flex items-center justify-between transition-all cursor-pointer ${
                  isSelected
                    ? "bg-white/25 border border-white/35 text-white font-semibold shadow-sm"
                    : "text-white/85 hover:bg-white/15 hover:text-white"
                }`}
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  {option.colorDot && (
                    <span
                      className={`w-2 h-2 rounded-full shrink-0 ${option.colorDot}`}
                    />
                  )}
                  <div className="min-w-0">
                    <div
                      className={`leading-tight text-white font-medium ${
                        isSmall ? "text-xs sm:text-sm" : "text-[14px]"
                      }`}
                    >
                      {option.label}
                    </div>
                    {option.description && (
                      <div
                        className={`text-white/60 mt-0.5 truncate ${
                          isSmall ? "text-[10px]" : "text-[11px]"
                        }`}
                      >
                        {option.description}
                      </div>
                    )}
                  </div>
                </div>

                {isSelected && (
                  <Check className="h-3.5 w-3.5 text-white shrink-0 ml-2" />
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
