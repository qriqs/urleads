import { Outlet, Link, useLocation, useNavigate } from "react-router-dom"
import {
  ChevronDown,
  Search,
  Bell,
  Home,
  CheckSquare,
  Users,
  LineChart,
  Building2,
  PieChart,
  Settings,
  Filter,
  Megaphone,
  BellRing,
  LogOut
} from "lucide-react";
import { useAuth } from "../context/AuthContext";

export default function MainLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const userInitials = user?.username ? user.username.slice(0, 2).toUpperCase() : "US";

  const mainNav = [
    { name: "Resumen", path: "/app", icon: Home },
    { name: "Tareas", path: "/app/tareas", icon: CheckSquare, badge: "5" },
    { name: "Pipeline", path: "/app/pipeline", icon: LineChart },
    { name: "Leads", path: "/app/leads", icon: Users, hasChevron: true },
    { name: "Contactos", path: "/app/contactos", icon: Users },
    { name: "Empresas", path: "/app/empresas", icon: Building2 },
    { name: "Reportes", path: "/app/reportes", icon: PieChart, hasChevron: true },
  ];

  const opNav = [
    { name: "Embudo", path: "/app/embudo", icon: Filter },
    { name: "Campañas", path: "/app/campanas", icon: Megaphone },
    { name: "Notificaciones", path: "/app/notificaciones", icon: BellRing },
    { name: "Ajustes", path: "/app/ajustes", icon: Settings },
  ];

  return (
    <div className="flex flex-col h-full w-full bg-background overflow-hidden text-[13px]">
      
      {/* Top Header */}
      <header className="h-16 px-6 border-b border-border flex items-center justify-between gap-6 shrink-0 bg-background">
        {/* Left: Logo */}
        <div className="flex items-center gap-2 w-48 shrink-0">
          <Link to="/" className="flex items-center gap-2 hover:opacity-90 transition-opacity">
            <span className="text-xl select-none text-foreground">✦</span>
            <span className="font-semibold text-foreground text-sm tracking-tight">
              UrLeads
            </span>
          </Link>
          <ChevronDown className="h-4 w-4 text-muted-foreground ml-1" />
        </div>

        {/* Middle: Search */}
        <div className="flex-1 max-w-xl hidden md:block">
          <div className="h-10 px-3 rounded-lg bg-secondary/60 border border-border/60 flex items-center justify-between text-muted-foreground">
            <div className="flex items-center gap-2.5">
              <Search className="h-4 w-4" />
              <span className="text-xs">Buscar leads, notas...</span>
            </div>
            <kbd className="px-1.5 py-0.5 text-[10px] font-medium bg-background border border-border/80 rounded text-muted-foreground">
              ⌘K
            </kbd>
          </div>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="h-9 px-4 rounded-lg bg-foreground text-background font-medium text-xs flex items-center justify-center shadow-sm cursor-pointer hover:opacity-90">
            Nuevo Lead
          </div>
          <div className="h-9 w-9 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground border border-transparent cursor-pointer">
            <Bell className="h-4 w-4" />
          </div>
          <button
            type="button"
            onClick={async () => {
              await logout();
              navigate('/login');
            }}
            title={`Usuario: ${user?.username || 'Usuario'} (Clic para cerrar sesión)`}
            className="h-9 w-9 rounded-full bg-secondary border border-border text-foreground font-semibold text-xs flex items-center justify-center cursor-pointer hover:bg-red-500/10 hover:text-red-500 transition-colors"
          >
            {userInitials}
          </button>
        </div>
      </header>

      {/* Main Body */}
      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar */}
        <aside className="w-56 border-r border-border bg-card p-4 hidden md:flex flex-col justify-between shrink-0 overflow-y-auto">
          <div className="space-y-6">
            
            {/* Main Navigation */}
            <nav className="space-y-1">
              {mainNav.map((item) => {
                const isActive = location.pathname === item.path || (item.path !== '/app' && location.pathname.startsWith(item.path));
                return (
                  <Link
                    key={item.name}
                    to={item.path}
                    className={`flex items-center justify-between px-3 py-2 rounded-lg text-sm transition-colors ${
                      isActive ? 'bg-secondary font-medium text-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-secondary/50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <item.icon className={`h-4 w-4 ${isActive ? 'text-accent' : ''}`} />
                      <span>{item.name}</span>
                    </div>
                    {item.badge && (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-500/15 text-green-600">
                        {item.badge}
                      </span>
                    )}
                    {item.hasChevron && (
                      <ChevronDown className="h-3.5 w-3.5 opacity-40" />
                    )}
                  </Link>
                )
              })}
            </nav>

            {/* Sub Navigation */}
            <div>
              <div className="px-3 mb-2 text-[10px] font-bold tracking-wider text-muted-foreground/70 uppercase">
                Operaciones
              </div>
              <nav className="space-y-1">
                {opNav.map((item) => {
                  const isActive = location.pathname === item.path;
                  return (
                    <Link
                      key={item.name}
                      to={item.path}
                      className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
                        isActive ? 'bg-secondary font-medium text-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-secondary/50'
                      }`}
                    >
                      <item.icon className={`h-4 w-4 ${isActive ? 'text-accent' : ''}`} />
                      <span>{item.name}</span>
                    </Link>
                  )
                })}
              </nav>
            </div>
          </div>

          {/* User Profile & Logout section at sidebar bottom */}
          <div className="pt-4 border-t border-border mt-auto">
            <div className="flex items-center gap-3 px-3 py-2 mb-2">
              <div className="h-8 w-8 rounded-full bg-secondary border border-border text-foreground font-semibold text-xs flex items-center justify-center shrink-0">
                {userInitials}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-foreground truncate">{user?.username || 'Usuario'}</p>
                <p className="text-[10px] text-muted-foreground truncate">Sesión activa</p>
              </div>
            </div>
            <button
              type="button"
              onClick={async () => {
                await logout();
                navigate('/login');
              }}
              className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium text-red-500 hover:bg-red-500/10 transition-colors cursor-pointer"
            >
              <LogOut className="h-4 w-4" />
              <span>Cerrar sesión</span>
            </button>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 flex flex-col h-full overflow-y-auto pb-16 md:pb-0 bg-[#fcfcfc] dark:bg-background">
          <div className="flex-1 p-4 md:p-6 lg:p-8">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Bottom Nav - Mobile */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 h-16 border-t border-border bg-background flex items-center justify-around px-2 z-20">
        {[mainNav[0], mainNav[3], opNav[3]].map((item) => {
          const isActive = location.pathname === item.path || (item.path !== '/app' && location.pathname.startsWith(item.path));
          return (
            <Link
              key={item.name}
              to={item.path}
              className={`flex flex-col items-center justify-center gap-1 w-full h-full ${isActive ? 'text-accent' : 'text-muted-foreground'}`}
            >
              <item.icon className="h-5 w-5" />
              <span className="text-[10px] font-medium">{item.name}</span>
            </Link>
          )
        })}
      </nav>
    </div>
  )
}
