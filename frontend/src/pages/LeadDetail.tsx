import { Link, useParams } from "react-router-dom"
import { ArrowLeft } from "lucide-react"

export default function LeadDetail() {
  const { id } = useParams();

  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      <div className="flex items-center gap-4">
        <Link to="/app/leads" className="p-2 -ml-2 rounded-md hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft className="h-4 w-4" />
        </Link>
        <div>
          <h1 className="text-xl font-semibold text-foreground tracking-tight">Detalle del Lead {id}</h1>
          <p className="text-sm text-muted-foreground">Propuesta de marketing</p>
        </div>
      </div>
      
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 border border-border rounded-xl bg-card p-6 min-h-[400px]">
          <h2 className="font-medium mb-4">Bitácora y Notas (Placeholder)</h2>
          <div className="space-y-4">
            <div className="h-20 bg-muted/50 rounded-lg border border-border/50 border-dashed flex items-center justify-center text-xs text-muted-foreground">Area de nueva nota</div>
            <div className="h-24 bg-secondary/30 rounded-lg"></div>
          </div>
        </div>
        <div className="border border-border rounded-xl bg-card p-6">
          <h2 className="font-medium mb-4">Info del lead (Placeholder)</h2>
          <div className="space-y-4">
            <div className="h-8 bg-secondary/30 rounded"></div>
            <div className="h-8 bg-secondary/30 rounded"></div>
            <div className="h-8 bg-secondary/30 rounded"></div>
          </div>
        </div>
      </div>
    </div>
  )
}
