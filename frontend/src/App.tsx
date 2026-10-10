import { Routes, Route, Navigate } from "react-router-dom"
import Login from "./pages/Login"
import AuroraDashboard from "./pages/AuroraDashboard"
import LandingPage from "./pages/LandingPage"

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<Login />} />
      <Route path="/app" element={<AuroraDashboard />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
