import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

interface ProtectedRouteProps {
  children?: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  // Allow iframe embedding for the landing page widget preview
  const isInsideIframe = typeof window !== 'undefined' && window.self !== window.top;
  if (isInsideIframe) {
    return children ? <>{children}</> : <Outlet />;
  }

  if (isLoading) {
    return (
      <div 
        className="flex flex-col items-center justify-center min-h-screen bg-background text-foreground"
        role="status"
        aria-live="polite"
      >
        <div className="relative flex items-center justify-center mb-4">
          <div className="w-12 h-12 rounded-full border-2 border-primary/20 border-t-primary animate-spin" />
          <span className="absolute text-sm select-none text-foreground font-semibold">✦</span>
        </div>
        <p className="text-sm text-muted-foreground animate-pulse font-medium">
          Verificando sesión...
        </p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children ? <>{children}</> : <Outlet />;
};
