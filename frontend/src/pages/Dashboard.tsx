import { 
  Send, 
  Download, 
  ArrowLeftRight, 
  Upload, 
  Receipt, 
  FileCheck,
  Check,
  Plus,
  MoreVertical
} from "lucide-react"

export default function Dashboard() {
  return (
    <div className="flex-1 bg-background p-4 md:p-6 space-y-6 animate-in fade-in duration-500">
      {/* Top Greeting */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl md:text-2xl font-semibold text-foreground tracking-tight">
            Hola, Sebas
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            Tu resumen de ventas y prospectos
          </p>
        </div>
      </div>

      {/* Action Buttons Row */}
      <div className="flex items-center gap-2 flex-wrap">
        <button className="h-9 px-4 rounded-full bg-accent text-accent-foreground font-medium text-xs flex items-center gap-2 shadow-xs hover:bg-accent/90 transition-colors">
          <Send className="h-3.5 w-3.5" />
          <span>Llamada</span>
        </button>
        <button className="h-9 px-4 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-2 shadow-sm hover:bg-secondary transition-colors">
          <Download className="h-3.5 w-3.5 text-muted-foreground" />
          <span>Email</span>
        </button>
        <button className="h-9 px-4 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-2 shadow-sm hover:bg-secondary transition-colors">
          <ArrowLeftRight className="h-3.5 w-3.5 text-muted-foreground" />
          <span>Cita</span>
        </button>
        <button className="h-9 px-4 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-2 shadow-sm hover:bg-secondary transition-colors">
          <Upload className="h-3.5 w-3.5 text-muted-foreground" />
          <span>Nota</span>
        </button>
        <button className="h-9 px-4 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-2 shadow-sm hover:bg-secondary transition-colors">
          <Receipt className="h-3.5 w-3.5 text-muted-foreground" />
          <span>Tarea</span>
        </button>
        <button className="h-9 px-4 rounded-full bg-background border border-border text-foreground font-medium text-xs flex items-center gap-2 shadow-sm hover:bg-secondary transition-colors">
          <FileCheck className="h-3.5 w-3.5 text-muted-foreground" />
          <span>Propuesta</span>
        </button>
        <span className="text-xs text-muted-foreground hover:text-foreground ml-2 font-medium cursor-pointer">
          + Más
        </span>
      </div>

      {/* Two Equal-Width Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Pipeline Card */}
        <div className="bg-background rounded-xl border border-border p-5 flex flex-col justify-between shadow-sm">
          <div>
            <div className="flex items-center justify-between text-muted-foreground mb-2">
              <div className="flex items-center gap-2">
                <div className="h-5 w-5 rounded-full bg-emerald-50 text-emerald-600 border border-emerald-200 flex items-center justify-center">
                  <Check className="h-3 w-3 stroke-[3]" />
                </div>
                <span className="font-medium text-foreground text-sm">
                  Valor del Pipeline
                </span>
              </div>
              <span className="text-xs">Activo</span>
            </div>

            <div className="mt-2">
              <span className="text-3xl font-semibold tracking-tight text-foreground font-display">
                $120,450
              </span>
              <span className="text-sm text-muted-foreground font-medium">
                .00
              </span>
            </div>

            {/* Stats */}
            <div className="flex items-center gap-3 mt-2 text-xs">
              <span className="text-muted-foreground">Últimos 30 Días</span>
              <span className="text-emerald-600 font-semibold">
                +$12K Nuevos
              </span>
              <span className="text-rose-600 font-semibold">
                -$2K Perdidos
              </span>
            </div>
          </div>

          {/* SVG Area Chart */}
          <div className="mt-4 w-full h-24 relative overflow-hidden">
            <svg
              viewBox="0 0 360 80"
              preserveAspectRatio="none"
              className="w-full h-24"
            >
              <defs>
                <linearGradient
                  id="dashboardBalanceGradientReal"
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
                >
                  <stop
                    offset="0%"
                    stopColor="hsl(var(--accent))"
                    stopOpacity="0.15"
                  />
                  <stop
                    offset="100%"
                    stopColor="hsl(var(--accent))"
                    stopOpacity="0"
                  />
                </linearGradient>
              </defs>

              <path
                d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14 L 360 80 L 0 80 Z"
                fill="url(#dashboardBalanceGradientReal)"
              />
              <path
                d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14"
                fill="none"
                stroke="hsl(var(--accent))"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </svg>
          </div>
        </div>

        {/* Accounts Card */}
        <div className="bg-background rounded-xl border border-border p-5 flex flex-col shadow-sm">
          {/* Header */}
          <div className="flex items-center justify-between pb-3 border-b border-border/50">
            <span className="font-semibold text-foreground text-sm">
              Conversión por Etapa
            </span>
            <div className="flex items-center gap-2 text-muted-foreground">
              <div className="p-1 hover:text-foreground cursor-pointer rounded hover:bg-secondary">
                <Plus className="h-4 w-4" />
              </div>
              <div className="p-1 hover:text-foreground cursor-pointer rounded hover:bg-secondary">
                <MoreVertical className="h-4 w-4" />
              </div>
            </div>
          </div>

          {/* 3 Rows */}
          <div className="flex-1 flex flex-col justify-around py-2">
            <div className="flex items-center justify-between py-3">
              <div className="flex items-center gap-3">
                <div className="h-2.5 w-2.5 rounded-full bg-accent" />
                <span className="text-foreground font-medium text-sm">Calificados</span>
              </div>
              <span className="font-semibold text-foreground">
                45 Leads
              </span>
            </div>

            <div className="flex items-center justify-between py-3">
              <div className="flex items-center gap-3">
                <div className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
                <span className="text-foreground font-medium text-sm">Ganados</span>
              </div>
              <span className="font-semibold text-foreground">
                12 Leads
              </span>
            </div>

            <div className="flex items-center justify-between py-3">
              <div className="flex items-center gap-3">
                <div className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                <span className="text-foreground font-medium text-sm">En Seguimiento</span>
              </div>
              <span className="font-semibold text-foreground">
                84 Leads
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Transactions Table */}
      <div className="bg-background rounded-xl border border-border p-5 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-foreground">
            Leads Recientes
          </h3>
          <span className="text-xs text-muted-foreground hover:text-foreground font-medium cursor-pointer">
            Ver todos
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-border/60 text-xs text-muted-foreground uppercase font-medium">
                <th className="pb-2 font-medium">Fecha</th>
                <th className="pb-2 font-medium">Nombre</th>
                <th className="pb-2 font-medium text-right">Servicio</th>
                <th className="pb-2 font-medium text-right">Etapa</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40 text-sm">
              <tr>
                <td className="py-3 text-muted-foreground">Oct 07</td>
                <td className="py-3 font-medium text-foreground">Carlos Torres</td>
                <td className="py-3 text-right text-muted-foreground">
                  Consultoría IT
                </td>
                <td className="py-3 text-right">
                  <span className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-amber-500/10 text-amber-700 border border-amber-500/20">
                    En seguimiento
                  </span>
                </td>
              </tr>
              <tr>
                <td className="py-3 text-muted-foreground">Oct 06</td>
                <td className="py-3 font-medium text-foreground">
                  Tech Solutions S.A.
                </td>
                <td className="py-3 text-right text-muted-foreground">
                  Plan Enterprise
                </td>
                <td className="py-3 text-right">
                  <span className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                    Cerrado
                  </span>
                </td>
              </tr>
              <tr>
                <td className="py-3 text-muted-foreground">Oct 04</td>
                <td className="py-3 font-medium text-foreground">María López</td>
                <td className="py-3 text-right text-muted-foreground">
                  Marketing
                </td>
                <td className="py-3 text-right">
                  <span className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                    Cerrado
                  </span>
                </td>
              </tr>
              <tr>
                <td className="py-3 text-muted-foreground">Oct 02</td>
                <td className="py-3 font-medium text-foreground">
                  Distribuidora Sur
                </td>
                <td className="py-3 text-right text-muted-foreground">
                  Soporte
                </td>
                <td className="py-3 text-right">
                  <span className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-blue-500/10 text-blue-700 border border-blue-500/20">
                    Nuevo
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
