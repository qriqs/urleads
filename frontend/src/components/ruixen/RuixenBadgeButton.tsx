import React from "react";
import { motion } from "framer-motion";

export interface RuixenBadgeButtonProps {
  label: string;
  badge?: string | number;
  icon?: React.ComponentType<{ className?: string }>;
  onClick?: () => void;
  variant?: "default" | "accent" | "outline" | "secondary";
  className?: string;
  badgeColor?: string;
  title?: string;
}

export function RuixenBadgeButton({
  label,
  badge,
  icon: Icon,
  onClick,
  variant = "default",
  className = "",
  badgeColor,
  title,
}: RuixenBadgeButtonProps) {
  const getVariantStyles = () => {
    switch (variant) {
      case "accent":
        return "bg-accent text-accent-foreground border-transparent shadow-xs";
      case "secondary":
        return "bg-secondary text-foreground border-border/60 hover:bg-secondary/80";
      case "outline":
        return "bg-background border-border text-foreground hover:bg-secondary shadow-2xs";
      default:
        return "bg-primary text-primary-foreground border-transparent shadow-xs";
    }
  };

  return (
    <motion.button
      type="button"
      title={title}
      onClick={onClick}
      whileHover={{ scale: 1.03 }}
      whileTap={{ scale: 0.96 }}
      transition={{ type: "spring", stiffness: 450, damping: 28 }}
      className={`inline-flex items-center gap-2 px-4 py-2 rounded-full font-semibold text-xs sm:text-sm border transition-colors cursor-pointer select-none active:scale-95 ${getVariantStyles()} ${className}`}
    >
      {Icon && <Icon className="h-3.5 w-3.5 shrink-0" />}
      <span>{label}</span>
      {badge !== undefined && (
        <span
          className={`inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-[10px] font-bold ${
            badgeColor || "bg-foreground/10 text-inherit"
          }`}
        >
          {badge}
        </span>
      )}
    </motion.button>
  );
}

export default RuixenBadgeButton;
