import React from "react";
import { Button } from "./ui/button";

export const Navbar: React.FC = () => {
  return (
    <header className="relative z-20 flex items-center justify-between px-6 md:px-12 lg:px-20 py-5 font-body shrink-0 bg-transparent">
      {/* Left: Brand Logo */}
      <div className="flex items-center gap-2">
        <a
          href="#"
          className="text-xl font-semibold tracking-tight text-foreground hover:opacity-90 transition-opacity flex items-center gap-1.5"
        >
          <span className="text-base select-none text-foreground">✦</span>
          <span>Nexora</span>
        </a>
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
            Home
          </a>
          <a
            href="#pricing"
            className="hover:text-foreground transition-colors"
          >
            Pricing
          </a>
          <a
            href="#about"
            className="hover:text-foreground transition-colors"
          >
            About
          </a>
          <a
            href="#contact"
            className="hover:text-foreground transition-colors"
          >
            Contact
          </a>
        </nav>

        <Button
          className="rounded-full px-5 text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-all duration-200 shadow-sm"
          size="default"
        >
          Get started
        </Button>
      </div>
    </header>
  );
};
