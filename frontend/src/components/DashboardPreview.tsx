import React, { useRef, useState, useEffect } from "react";
import { motion } from "framer-motion";

export const DashboardPreview: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const updateScale = () => {
      if (containerRef.current) {
        setScale(containerRef.current.clientWidth / 1357);
      }
    };

    updateScale();

    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0) {
          setScale(entry.contentRect.width / 1357);
        }
      }
    });

    if (containerRef.current) {
      observer.observe(containerRef.current);
    }

    window.addEventListener("resize", updateScale);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", updateScale);
    };
  }, []);

  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8, delay: 0.5, ease: [0.16, 1, 0.3, 1] }}
      className="mt-6 w-full max-w-4xl shrink-0 mx-auto"
    >
      {/* Thick Frosted Glass Outer Frame */}
      <div
        className="rounded-[32px] p-3 sm:p-5 md:p-6 backdrop-blur-md"
        style={{
          background: "rgba(255, 255, 255, 0.35)",
          border: "1px solid rgba(255, 255, 255, 0.5)",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
        }}
      >
        {/* Inner Dashboard Window with proportional scaling */}
        <div
          ref={containerRef}
          className="rounded-[24px] shadow-2xl overflow-hidden relative w-full bg-black"
          style={{ paddingTop: "64.18%" /* 871 / 1357 aspect ratio */ }}
        >
          <iframe
            src="/app"
            title="Dashboard Preview"
            className="absolute top-0 left-0 border-none pointer-events-none select-none"
            scrolling="no"
            style={{
              width: "1357px",
              height: "871px",
              transform: `scale(${scale})`,
              transformOrigin: "top left",
              border: "none",
            }}
          />
          {/* Overlay to avoid capturing mouse/touch events */}
          <div className="absolute inset-0 z-50 pointer-events-none" />
        </div>
      </div>
    </motion.div>
  );
};
