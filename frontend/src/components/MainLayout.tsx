import { Outlet, Link, useLocation } from "react-router-dom"
import { Home, Users, LogOut } from "lucide-react"

export default function MainLayout() {
  const location = useLocation();

  const navItems = [
    { name: "Resumen", path: "/app", icon: Home },
    { name: "Leads", path: "/app/leads", icon: Users },
  ];

  return (
    <div className="flex min-h-screen bg-background overflow-hidden">
      {/* Sidebar - Desktop (hidden on mobile <=760px based on UL-05) */}
      <aside className="hidden md:flex flex-col w-[246px] border-r border-border bg-card p-4 justify-between">
        <div>
          <Link
            to="/"
            className="flex items-center gap-1.5 mb-8 px-2 hover:opacity-90 transition-opacity"
          >
            <span className="text-base select-none text-foreground">✦</span>
            <span className="font-semibold text-foreground tracking-tight">UrLeads</span>
          </Link>
          
          <nav className="space-y-1">
            {navItems.map((item) => {
              const isActive = location.pathname === item.path || (item.path !== '/' && location.pathname.startsWith(item.path));
              return (
                <Link
                  key={item.name}
                  to={item.path}
                  className={`flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${isActive ? 'bg-secondary font-medium text-foreground' : 'text-muted-foreground hover:text-foreground hover:bg-secondary/50'}`}
                >
                  <item.icon className={`h-4 w-4 ${isActive ? 'text-accent' : ''}`} />
                  {item.name}
                </Link>
              )
            })}
          </nav>
        </div>
        
        <button className="flex items-center gap-3 px-3 py-2 rounded-md text-sm text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors">
          <LogOut className="h-4 w-4" />
          Cerrar sesión
        </button>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col h-screen overflow-y-auto pb-16 md:pb-0">
        {/* Mobile Top Bar */}
        <header className="md:hidden flex items-center justify-between p-4 border-b border-border bg-background sticky top-0 z-10">
          <Link
            to="/"
            className="flex items-center gap-1.5 hover:opacity-90 transition-opacity"
          >
            <span className="text-base select-none text-foreground">✦</span>
            <span className="font-semibold text-foreground text-sm tracking-tight">UrLeads</span>
          </Link>
          <button className="text-muted-foreground">
            <LogOut className="h-5 w-5" />
          </button>
        </header>

        <div className="flex-1 p-4 md:p-6 lg:p-8">
          <Outlet />
        </div>
      </main>

      {/* Bottom Nav - Mobile */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 h-16 border-t border-border bg-background flex items-center justify-around px-2 z-20">
        {navItems.map((item) => {
          const isActive = location.pathname === item.path || (item.path !== '/' && location.pathname.startsWith(item.path));
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
