import React, { useRef, useState, useEffect } from "react";
import { motion } from "framer-motion";

export const DashboardPreview: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const observer = new ResizeObserver((entries) => {
      for (let entry of entries) {
        // Calculate scale based on a 1357px original width
        setScale(entry.contentRect.width / 1357);
      }
    });

    if (containerRef.current) {
      observer.observe(containerRef.current);
    }

    return () => observer.disconnect();
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
        className="rounded-[32px] p-4 md:p-6 backdrop-blur-md"
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
          style={{ paddingTop: '64.18%' /* 871 / 1357 */ }}
        >
           <iframe 
             src="/app" 
             title="Dashboard Preview" 
             className="absolute top-0 left-0 border-none pointer-events-none"
             scrolling="no"
             style={{
               width: '1357px',
               height: '871px',
               transform: `scale(${scale})`,
               transformOrigin: 'top left'
             }}
           />
           {/* Invisible overlay to prevent clicking inside the widget */}
           <div className="absolute inset-0 z-50"></div>
        </div>
      </div>
    </motion.div>
  );
};
