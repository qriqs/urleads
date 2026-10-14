import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  User as UserIcon, 
  Lock, 
  Eye, 
  EyeOff, 
  ArrowRight, 
  AlertCircle, 
  CheckCircle2, 
  Loader2,
  Sparkles
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, isAuthenticated, sessionExpired, clearSessionExpired } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Field validation errors
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  // Server error
  const [serverError, setServerError] = useState<string | null>(null);

  // Determine redirect target (default to /app)
  const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/app';

  // If already authenticated, redirect immediately
  useEffect(() => {
    if (isAuthenticated) {
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, navigate, from]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError(null);
    clearSessionExpired();

    // Client validation
    let hasError = false;
    const trimmedUsername = username.trim();

    if (!trimmedUsername) {
      setUsernameError('El nombre de usuario es obligatorio.');
      hasError = true;
    } else {
      setUsernameError(null);
    }

    if (!password) {
      setPasswordError('La contraseña es obligatoria.');
      hasError = true;
    } else {
      setPasswordError(null);
    }

    if (hasError) return;

    setIsSubmitting(true);

    try {
      // Safe login call - no passwords printed to console
      await login({ username: trimmedUsername, password });
      navigate(from, { replace: true });
    } catch (err: unknown) {
      if (err instanceof Error) {
        setServerError(err.message);
      } else {
        setServerError('No fue posible iniciar sesión. Intenta de nuevo.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex flex-col lg:flex-row bg-background selection:bg-primary/20 selection:text-foreground">
      
      {/* Left Artwork / Video Panel (motionsites inspired) - Hidden on small mobile screens, visible lg+ */}
      <section 
        className="relative hidden lg:flex lg:w-1/2 xl:w-7/12 flex-col justify-between p-12 overflow-hidden bg-black"
        aria-label="Panel de presentación"
      >
        {/* Looping background video */}
        <video
          autoPlay
          muted
          loop
          playsInline
          className="absolute inset-0 w-full h-full object-cover opacity-75"
          src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260813_052122_e77a27e6-17f1-4794-889b-3ceaa0e9e8cb.mp4"
        />

        {/* Scrim Overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/40 to-black/60 pointer-events-none" />

        {/* Top Branding */}
        <div className="relative z-10">
          <Link to="/" className="inline-flex items-center gap-2 text-white font-semibold text-lg tracking-tight hover:opacity-90 transition-opacity">
            <span className="text-xl">✦</span>
            <span>UrLeads CRM</span>
          </Link>
        </div>

        {/* Bottom Hero Info */}
        <div className="relative z-10 max-w-lg space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/10 backdrop-blur-md border border-white/20 text-white text-xs font-medium">
            <Sparkles className="h-3.5 w-3.5 text-emerald-400" />
            <span>Plataforma de Ventas en Tiempo Real</span>
          </div>

          <h2 className="text-4xl xl:text-5xl font-display font-medium text-white tracking-tight leading-tight">
            Cierra más ventas a máxima velocidad.
          </h2>

          <p className="text-white/80 text-sm md:text-base leading-relaxed font-body">
            Centraliza tus prospectos, gestiona el embudo comercial y mantén la bitácora de seguimiento actualizada al instante.
          </p>

          <div className="flex items-center gap-6 pt-2 text-xs text-white/70 font-medium">
            <div className="flex items-center gap-1.5">
              <CheckCircle2 className="h-4 w-4 text-emerald-400" />
              <span>Sesión segura Spring Security</span>
            </div>
            <div className="flex items-center gap-1.5">
              <CheckCircle2 className="h-4 w-4 text-emerald-400" />
              <span>Protección CSRF activa</span>
            </div>
          </div>
        </div>
      </section>

      {/* Right Login Form Pane */}
      <section 
        className="flex-1 flex flex-col justify-center items-center px-4 sm:px-6 md:px-10 py-10 overflow-y-auto"
        aria-label="Formulario de inicio de sesión"
      >
        <div className="w-full max-w-[420px] mx-auto space-y-6">
          
          {/* Header Mobile Brand */}
          <div className="lg:hidden flex justify-center mb-2">
            <Link to="/" className="inline-flex items-center gap-2 text-foreground font-semibold text-xl tracking-tight">
              <span className="text-2xl">✦</span>
              <span>UrLeads</span>
            </Link>
          </div>

          {/* Form Header */}
          <div className="text-center space-y-2">
            <h1 className="text-2xl sm:text-3xl font-display font-semibold text-foreground tracking-tight">
              Bienvenido de nuevo
            </h1>
            <p className="text-xs sm:text-sm text-muted-foreground font-body">
              Ingresa tus credenciales para acceder a tu panel de CRM.
            </p>
          </div>

          {/* Session Expired Notice */}
          <AnimatePresence>
            {sessionExpired && (
              <motion.div
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                className="p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-700 dark:text-amber-400 flex items-start gap-2.5 text-xs sm:text-sm"
                role="alert"
              >
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>Tu sesión ha expirado por inactividad. Por favor, identifícate nuevamente.</span>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Server Error Notice */}
          <AnimatePresence>
            {serverError && (
              <motion.div
                initial={{ opacity: 0, y: -8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                className="p-3.5 rounded-xl bg-red-500/10 border border-red-500/20 text-red-600 dark:text-red-400 flex items-start gap-2.5 text-xs sm:text-sm"
                role="alert"
                aria-live="assertive"
              >
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{serverError}</span>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Login Form */}
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            
            {/* Username Field */}
            <div className="space-y-1.5">
              <label 
                htmlFor="username"
                className="block text-xs font-medium text-foreground tracking-wide"
              >
                Usuario
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground">
                  <UserIcon className="h-4 w-4" />
                </div>
                <input
                  id="username"
                  name="username"
                  type="text"
                  autoComplete="username"
                  autoFocus
                  disabled={isSubmitting}
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                    if (usernameError) setUsernameError(null);
                  }}
                  placeholder="ej. admin"
                  aria-invalid={!!usernameError}
                  aria-describedby={usernameError ? "username-error" : undefined}
                  className={`w-full h-11 pl-10 pr-3.5 rounded-xl border bg-background text-sm text-foreground placeholder:text-muted-foreground transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 disabled:opacity-50 disabled:cursor-not-allowed ${
                    usernameError 
                      ? 'border-red-500 focus-visible:ring-red-500/30' 
                      : 'border-border focus-visible:border-primary focus-visible:ring-primary/20'
                  }`}
                />
              </div>
              {usernameError && (
                <p id="username-error" className="text-xs text-red-500 mt-1 pl-1" role="alert">
                  {usernameError}
                </p>
              )}
            </div>

            {/* Password Field */}
            <div className="space-y-1.5">
              <label 
                htmlFor="password"
                className="block text-xs font-medium text-foreground tracking-wide"
              >
                Contraseña
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-muted-foreground">
                  <Lock className="h-4 w-4" />
                </div>
                <input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  disabled={isSubmitting}
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (passwordError) setPasswordError(null);
                  }}
                  placeholder="••••••••"
                  aria-invalid={!!passwordError}
                  aria-describedby={passwordError ? "password-error" : undefined}
                  className={`w-full h-11 pl-10 pr-11 rounded-xl border bg-background text-sm text-foreground placeholder:text-muted-foreground transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 disabled:opacity-50 disabled:cursor-not-allowed ${
                    passwordError 
                      ? 'border-red-500 focus-visible:ring-red-500/30' 
                      : 'border-border focus-visible:border-primary focus-visible:ring-primary/20'
                  }`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  disabled={isSubmitting}
                  aria-label={showPassword ? "Ocultar contraseña" : "Ver contraseña"}
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {passwordError && (
                <p id="password-error" className="text-xs text-red-500 mt-1 pl-1" role="alert">
                  {passwordError}
                </p>
              )}
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full h-11 mt-2 rounded-xl bg-primary text-primary-foreground font-medium text-sm flex items-center justify-center gap-2 hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-all duration-150 shadow-sm active:scale-[0.99] disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>Iniciando sesión...</span>
                </>
              ) : (
                <>
                  <span>Ingresar</span>
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          {/* Footer Back link */}
          <div className="pt-4 border-t border-border/60 text-center">
            <Link 
              to="/" 
              className="text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              ← Volver a la página principal
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
