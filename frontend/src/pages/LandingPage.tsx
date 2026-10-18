/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from "react";
import { motion } from "framer-motion";
import { Link } from "react-router-dom";
import { Navbar } from "../components/Navbar";
import { DashboardPreview } from "../components/DashboardPreview";
import { Button } from "../components/ui/button";
import { useAuth } from "../context/AuthContext";

export default function LandingPage() {
  const { isAuthenticated } = useAuth();
  const [videoLoaded, setVideoLoaded] = useState(false);

  return (
    <div className="min-h-screen flex flex-col bg-background overflow-hidden relative selection:bg-accent/20 selection:text-foreground">
      {/* Ambient lightweight background (0 KB, renders instantly with forest green tone matching UrLeads brand) */}
      <div className="absolute inset-0 z-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-emerald-950/20 via-background to-background pointer-events-none" />

      {/* Background Video: Non-blocking with preload='none' and smooth opacity fade to prevent blocking page paint */}
      <video
        autoPlay
        muted
        loop
        playsInline
        preload="none"
        onLoadedData={() => setVideoLoaded(true)}
        className={`absolute inset-0 w-full h-full object-cover z-0 pointer-events-none transition-opacity duration-1000 ${
          videoLoaded ? "opacity-30" : "opacity-0"
        }`}
        src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260319_015952_e1deeb12-8fb7-4071-a42a-60779fc64ab6.mp4"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-background/30 via-transparent to-background z-0 pointer-events-none" />

      {/* Top Navigation Bar */}
      <Navbar />

      {/* Hero Section */}
      <main className="relative z-10 flex-1 flex flex-col items-center w-full px-4 pt-2 md:pt-4 overflow-hidden max-w-7xl mx-auto">
        {/* 1. Badge (top) */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
          className="mb-4 md:mb-5"
        >
          <div className="inline-flex items-center gap-1.5 rounded-full border border-border bg-background/90 backdrop-blur-xs px-4 py-1.5 text-sm text-muted-foreground font-body shadow-2xs hover:border-border/80 transition-colors cursor-default">
            <span>Gestión de leads inteligente ✨</span>
          </div>
        </motion.div>

        {/* 2. Headline */}
        <motion.h1
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
          className="text-center font-display text-4xl sm:text-5xl md:text-5xl lg:text-[4.25rem] leading-[0.95] tracking-tight text-foreground max-w-xl"
        >
          Cierra más <span className="italic font-display font-normal">ventas</span> con UrLeads
        </motion.h1>

        {/* 3. Subheadline */}
        <motion.p
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.2, ease: [0.16, 1, 0.3, 1] }}
          className="mt-3 text-center text-sm md:text-base text-muted-foreground max-w-[580px] leading-relaxed font-body"
        >
          Centraliza tus contactos, haz seguimiento oportuno y convierte prospectos en clientes de manera rápida, limpia y profesional.
        </motion.p>

        {/* 4. CTA Buttons */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.3, ease: [0.16, 1, 0.3, 1] }}
          className="mt-3.5 flex items-center justify-center"
        >
          {/* Primary button */}
          <Link to={isAuthenticated ? "/app" : "/login"}>
            <Button
              className="rounded-full px-5 py-4 text-xs md:text-sm font-medium font-body bg-primary text-primary-foreground hover:bg-primary/90 transition-all duration-200 shadow-sm active:scale-98 cursor-pointer"
            >
              Go to CRM
            </Button>
          </Link>
        </motion.div>

        {/* 5. Dashboard Preview (custom coded, optimized GPU compositing layer) */}
        <DashboardPreview />
      </main>
    </div>
  );
}
