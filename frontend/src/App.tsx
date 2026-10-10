import { Routes, Route } from "react-router-dom"
import MainLayout from "./components/MainLayout"
import Login from "./pages/Login"
import Dashboard from "./pages/Dashboard"
import LeadsList from "./pages/LeadsList"
import LeadDetail from "./pages/LeadDetail"

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<MainLayout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/leads" element={<LeadsList />} />
        <Route path="/leads/:id" element={<LeadDetail />} />
      </Route>
    </Routes>
  )
}
