import { Routes, Route, Navigate } from "react-router-dom"
import Login from "./pages/Login"
import AuroraDashboard from "./pages/AuroraDashboard"
import LandingPage from "./pages/LandingPage"
import LeadsList from "./pages/LeadsList"
import LeadDetail from "./pages/LeadDetail"
import MainLayout from "./components/MainLayout"
import Dashboard from "./pages/Dashboard"

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<Login />} />
      <Route path="/app" element={<AuroraDashboard />}>
        <Route path="leads" element={<LeadsList />} />
        <Route path="leads/:id" element={<LeadDetail />} />
        {/* Placeholder routes for UI testing */}
        <Route path="reportes" element={<div className="p-8 text-white"><h2 className="text-2xl font-semibold mb-4">Reportes</h2><p>Módulo de reportes en construcción...</p></div>} />
        <Route path="tareas" element={<div className="p-8 text-white"><h2 className="text-2xl font-semibold mb-4">Tareas</h2><p>Módulo de tareas en construcción...</p></div>} />
        <Route path="ajustes" element={<div className="p-8 text-white"><h2 className="text-2xl font-semibold mb-4">Ajustes</h2><p>Módulo de ajustes en construcción...</p></div>} />
      </Route>
      <Route path="/preview" element={<MainLayout />}>
        <Route index element={<Dashboard />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
