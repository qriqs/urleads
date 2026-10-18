import React, { useState, useRef, useEffect } from "react";
import { motion } from "framer-motion";

export interface RuixenTabItem {
  value: string;
  label: string;
  badge?: string | number;
  icon?: React.ComponentType<{ className?: string }>;
}

export interface RuixenMagneticTabsProps {
  items: RuixenTabItem[];
  value: string;
  onChange: (value: string) => void;
  className?: string;
  size?: "sm" | "md";
}

export function RuixenMagneticTabs({
  items,
  value,
  onChange,
  className = "",
  size = "md",
}: RuixenMagneticTabsProps) {
  const [hoveredTab, setHoveredTab] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const activeIndex = items.findIndex((item) => item.value === value);

  return (
    <div
      ref={containerRef}
      onMouseLeave={() => setHoveredTab(null)}
      className={`relative inline-flex items-center p-1 rounded-full bg-secondary/60 border border-border/80 backdrop-blur-md shadow-2xs ${className}`}
    >
      {items.map((item) => {
        const isActive = item.value === value;
        const Icon = item.icon;

        return (
          <button
            key={item.value}
            type="button"
            onClick={() => onChange(item.value)}
            onMouseEnter={() => setHoveredTab(item.value)}
            className={`relative z-10 flex items-center gap-2 rounded-full font-semibold transition-colors cursor-pointer select-none whitespace-nowrap ${
              size === "sm" ? "px-3 py-1 text-xs" : "px-4 py-1.5 text-xs sm:text-sm"
            } ${
              isActive
                ? "text-foreground font-bold"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {/* Active Pill Spring Indicator */}
            {isActive && (
              <motion.div
                layoutId="ruixenActivePill"
                className="absolute inset-0 bg-background rounded-full shadow-xs border border-border/60 z-[-1]"
                transition={{ type: "spring", stiffness: 450, damping: 32 }}
              />
            )}

            {/* Hover Ghost Pill Indicator */}
            {!isActive && hoveredTab === item.value && (
              <motion.div
                layoutId="ruixenHoverPill"
                className="absolute inset-0 bg-secondary/80 rounded-full z-[-1]"
                transition={{ type: "spring", stiffness: 500, damping: 35 }}
              />
            )}

            {Icon && <Icon className="h-3.5 w-3.5 shrink-0" />}
            <span>{item.label}</span>
            {item.badge !== undefined && (
              <span
                className={`ml-1 px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                  isActive
                    ? "bg-accent text-accent-foreground"
                    : "bg-secondary text-muted-foreground"
                }`}
              >
                {item.badge}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

export default RuixenMagneticTabs;
