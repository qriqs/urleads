import React from "react";

interface UrLeadsLogoProps {
  size?: number;
  className?: string;
  showText?: boolean;
}

export const UrLeadsLogo: React.FC<UrLeadsLogoProps> = ({
  size = 28,
  className = "",
  showText = false,
}) => {
  return (
    <div className={`inline-flex items-center gap-2.5 ${className}`}>
      <svg
        viewBox="0 0 40 40"
        width={size}
        height={size}
        className="shrink-0 transition-transform duration-200 group-hover:scale-105"
        style={{ width: size, height: size }}
      >
        <defs>
          <clipPath id="urleads-contour-clip">
            <circle cx="20" cy="20" r="14" />
          </clipPath>
        </defs>
        {/* Outer badge rounded rectangle */}
        <rect
          width="40"
          height="40"
          rx="10"
          fill="hsl(var(--foreground))"
        />
        {/* Wavy contour lines inside circle clip */}
        <g
          clipPath="url(#urleads-contour-clip)"
          stroke="hsl(var(--background))"
          strokeWidth="1.8"
          strokeLinecap="round"
          fill="none"
        >
          <path d="M4,10 Q12,7 20,10 T36,10" />
          <path d="M4,13.5 Q12,10.5 20,13.5 T36,13.5" />
          <path d="M4,17 Q12,14 20,17 T36,17" />
          <path d="M4,20.5 Q12,17.5 20,20.5 T36,20.5" />
          <path d="M4,24 Q12,21 20,24 T36,24" />
          <path d="M4,27.5 Q12,24.5 20,27.5 T36,27.5" />
          <path d="M4,31 Q12,28 20,31 T36,31" />
        </g>
      </svg>
      {showText && (
        <span className="font-semibold text-foreground tracking-tight text-sm">
          UrLeads
        </span>
      )}
    </div>
  );
};

export default UrLeadsLogo;
