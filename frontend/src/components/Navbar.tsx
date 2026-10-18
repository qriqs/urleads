import React from "react";
import { Button } from "./ui/button";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

import { UrLeadsLogo } from "./UrLeadsLogo";

export const Navbar: React.FC = () => {
  const { isAuthenticated } = useAuth();

  return (
    <header className="relative z-20 flex items-center justify-between px-6 md:px-12 lg:px-20 py-5 font-body shrink-0 bg-transparent">
      {/* Left: Brand Logo */}
      <div className="flex items-center gap-2">
        <Link
          to="/"
          className="text-xl font-semibold tracking-tight text-foreground hover:opacity-90 transition-opacity flex items-center gap-1.5 group"
        >
          <UrLeadsLogo size={28} showText />
        </Link>
      </div>

      {/* Right: Nav Links (hidden on mobile) + CTA Button */}
      <div className="flex items-center gap-8">
        <nav
          className="hidden md:flex items-center gap-8 text-sm text-muted-foreground font-medium"
          aria-label="Main Navigation"
        >
          <a
            href="#home"
            className="hover:text-foreground transition-colors"
          >
            Inicio
          </a>
          <a
            href="#pricing"
            className="hover:text-foreground transition-colors"
          >
            Precios
          </a>
          <a
            href="#about"
            className="hover:text-foreground transition-colors"
          >
            Nosotros
          </a>
          <a
            href="#contact"
            className="hover:text-foreground transition-colors"
          >
            Contacto
          </a>
        </nav>

        <Link to={isAuthenticated ? "/app" : "/login"}>
          <Button
            className="rounded-full px-5 text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-all duration-200 shadow-sm cursor-pointer"
            size="default"
          >
            {isAuthenticated ? "Ir al CRM" : "Iniciar Sesión"}
          </Button>
        </Link>
      </div>
    </header>
  );
};
