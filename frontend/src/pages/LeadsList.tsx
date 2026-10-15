import { Link } from "react-router-dom"
import { Button } from "../components/ui/button"
import { Search, Plus } from "lucide-react"

export default function LeadsList() {
  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-foreground tracking-tight">Leads</h1>
          <p className="text-sm text-muted-foreground mt-1">Gestiona tus contactos y oportunidades.</p>
        </div>
        <div className="flex items-center gap-3 w-full sm:w-auto">
          <div className="relative flex-1 sm:w-64">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <input 
              type="text" 
              placeholder="Buscar..." 
              className="w-full h-9 pl-9 pr-3 rounded-md border border-input bg-background text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            />
          </div>
          <Button className="shrink-0">
            <Plus className="h-4 w-4 mr-2" />
            Nuevo
          </Button>
        </div>
      </div>
      
      <div className="border border-border rounded-xl bg-card overflow-hidden">
        <div className="p-8 text-center">
          <p className="text-sm text-muted-foreground">Tabla de leads (Placeholder)</p>
          <Link to="/app/leads/1" className="text-sm text-accent hover:underline mt-2 inline-block">Ver detalle del lead 1 (Demo)</Link>
        </div>
      </div>
    </div>
  )
}
