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

import ReportsPage from "./pages/ReportsPage"
import TasksPage from "./pages/TasksPage"
import SettingsPage from "./pages/SettingsPage"

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
            <Route path="reportes" element={<ReportsPage />} />
            <Route path="tareas" element={<TasksPage />} />
            <Route path="ajustes" element={<SettingsPage />} />
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
