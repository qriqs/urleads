import { Routes, Route, Navigate } from "react-router-dom"
import { Toaster } from "sileo"
import "sileo/styles.css"
import { AuthProvider } from "./context/AuthContext"
import { ProtectedRoute } from "./components/ProtectedRoute"
import Login from "./pages/Login"
import AuroraDashboard from "./pages/AuroraDashboard"
import LandingPage from "./pages/LandingPage"
import LeadsList from "./pages/LeadsList"
import LeadDetail from "./pages/LeadDetail"
import MainLayout from "./components/MainLayout"
import Dashboard from "./pages/Dashboard"

export default function App() {
  return (
    <AuthProvider>
      <Toaster position="top-center" />
      <Routes>
        {/* Public Routes */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<Login />} />

        {/* Protected Private Routes */}
        <Route element={<ProtectedRoute />}>
          <Route path="/app" element={<AuroraDashboard />}>
            <Route path="leads" element={<LeadsList />} />
            <Route path="leads/:id" element={<LeadDetail />} />
            <Route path="reportes" element={<div className="p-8 text-foreground"><h2 className="text-2xl font-semibold mb-2">Reportes</h2><p className="text-muted-foreground text-sm">Módulo de reportes en construcción...</p></div>} />
            <Route path="tareas" element={<div className="p-8 text-foreground"><h2 className="text-2xl font-semibold mb-2">Tareas</h2><p className="text-muted-foreground text-sm">Módulo de tareas en construcción...</p></div>} />
            <Route path="ajustes" element={<div className="p-8 text-foreground"><h2 className="text-2xl font-semibold mb-2">Ajustes</h2><p className="text-muted-foreground text-sm">Módulo de ajustes en construcción...</p></div>} />
          </Route>
          <Route path="/preview" element={<MainLayout />}>
            <Route index element={<Dashboard />} />
          </Route>
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
