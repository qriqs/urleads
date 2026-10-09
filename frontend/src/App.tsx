/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from "react";
import { motion } from "framer-motion";
import { Play, Sparkles, X } from "lucide-react";
import { Navbar } from "./components/Navbar";
import { DashboardPreview } from "./components/DashboardPreview";
import { Button } from "./components/ui/button";

export default function App() {
  const [showVideoModal, setShowVideoModal] = useState(false);

  return (
    <div className="h-screen flex flex-col bg-background overflow-hidden relative selection:bg-accent/20 selection:text-foreground">
      {/* Background Video: Fullscreen muted autoplay loop video */}
      <video
        autoPlay
        muted
        loop
        playsInline
        className="absolute inset-0 w-full h-full object-cover z-0 pointer-events-none"
        src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260319_015952_e1deeb12-8fb7-4071-a42a-60779fc64ab6.mp4"
      />

      {/* Top Navigation Bar */}
      <Navbar />

      {/* Hero Section */}
      <main 
        className="relative z-10 flex-1 flex flex-col items-center w-full px-4 pt-2 md:pt-4 overflow-hidden"
        style={{ zoom: 0.92 }}
      >
        {/* 1. Badge (top) */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
          className="mb-5 md:mb-6"
        >
          <div className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background/90 backdrop-blur-xs px-4 py-1.5 text-sm text-muted-foreground font-body shadow-2xs hover:border-border/80 transition-colors cursor-default">
            <span>Now with GPT-5 support ✨</span>
          </div>
        </motion.div>

        {/* 2. Headline */}
        <motion.h1
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
          className="text-center font-display text-5xl md:text-6xl lg:text-[5rem] leading-[0.95] tracking-tight text-foreground max-w-xl"
        >
          The Future of <span className="italic font-display font-normal">Smarter</span> Automation
        </motion.h1>

        {/* 3. Subheadline */}
        <motion.p
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.2, ease: [0.16, 1, 0.3, 1] }}
          className="mt-4 text-center text-base md:text-lg text-muted-foreground max-w-[650px] leading-relaxed font-body"
        >
          Automate your busywork with intelligent agents that learn, adapt, and execute—so your team can focus on what matters most.
        </motion.p>

        {/* 4. CTA Buttons */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.3, ease: [0.16, 1, 0.3, 1] }}
          className="mt-5 flex items-center gap-3"
        >
          {/* Primary button */}
          <Button
            className="rounded-full px-6 py-5 text-sm font-medium font-body bg-primary text-primary-foreground hover:bg-primary/90 transition-all duration-200 shadow-sm active:scale-98"
          >
            Book a demo
          </Button>

          {/* Play button */}
          <Button
            variant="ghost"
            onClick={() => setShowVideoModal(true)}
            aria-label="Play product video"
            className="h-11 w-11 rounded-full border-0 bg-background shadow-[0_2px_12px_rgba(0,0,0,0.08)] hover:bg-background/80 flex items-center justify-center p-0 transition-all duration-200 active:scale-95"
          >
            <Play className="h-4 w-4 fill-foreground text-foreground translate-x-0.5" />
          </Button>
        </motion.div>

        {/* 5. Dashboard Preview (custom coded, NOT an image) */}
        <DashboardPreview />
      </main>

      {/* Video Modal if clicked */}
      {showVideoModal && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in"
          onClick={() => setShowVideoModal(false)}
        >
          <div
            className="relative w-full max-w-4xl bg-background rounded-2xl overflow-hidden shadow-2xl border border-border"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-4 border-b border-border">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-accent" />
                <span className="font-semibold text-sm text-foreground">Nexora Product Tour</span>
              </div>
              <button
                onClick={() => setShowVideoModal(false)}
                className="p-1 rounded-full text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
                aria-label="Close modal"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="relative aspect-video bg-black">
              <video
                autoPlay
                controls
                className="w-full h-full object-cover"
                src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260319_015952_e1deeb12-8fb7-4071-a42a-60779fc64ab6.mp4"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
