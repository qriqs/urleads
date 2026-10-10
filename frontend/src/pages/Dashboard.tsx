export default function Dashboard() {
  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      <div>
        <h1 className="text-2xl font-semibold text-foreground tracking-tight">Resumen</h1>
        <p className="text-sm text-muted-foreground mt-1">Métricas principales de tus leads.</p>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[1, 2, 3, 4].map(i => (
          <div key={i} className="p-4 border border-border rounded-xl bg-card">
            <div className="h-4 w-1/2 bg-muted rounded mb-3"></div>
            <div className="h-8 w-1/3 bg-muted rounded"></div>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="p-4 border border-border rounded-xl bg-card min-h-[300px]">Panel 1 Placeholder</div>
        <div className="p-4 border border-border rounded-xl bg-card min-h-[300px]">Panel 2 Placeholder</div>
      </div>
    </div>
  )
}
