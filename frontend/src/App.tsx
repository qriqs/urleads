import { Routes, Route, Navigate } from "react-router-dom"
import MainLayout from "./components/MainLayout"
import Login from "./pages/Login"
import Dashboard from "./pages/Dashboard"
import LeadsList from "./pages/LeadsList"
import LeadDetail from "./pages/LeadDetail"
import LandingPage from "./pages/LandingPage"

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<Login />} />
      <Route path="/app" element={<MainLayout />}>
        <Route index element={<Dashboard />} />
        <Route path="leads" element={<LeadsList />} />
        <Route path="leads/:id" element={<LeadDetail />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
