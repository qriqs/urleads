import React from "react";
import { motion } from "framer-motion";
import {
  ChevronDown,
  Search,
  Bell,
  Home,
  CheckSquare,
  ArrowLeftRight,
  CreditCard,
  Building2,
  PieChart,
  SlidersHorizontal,
  Settings,
  Plus,
  MoreVertical,
  Check,
  Send,
  Download,
  Upload,
  Receipt,
  FileCheck,
} from "lucide-react";

export const DashboardPreview: React.FC = () => {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.8, delay: 0.5, ease: [0.16, 1, 0.3, 1] }}
      className="mt-8 w-full max-w-5xl shrink-0"
    >
      {/* Frosted Glass Outer Container */}
      <div
        className="rounded-2xl overflow-hidden p-3 md:p-4 backdrop-blur-md"
        style={{
          background: "rgba(255, 255, 255, 0.4)",
          border: "1px solid rgba(255, 255, 255, 0.5)",
          boxShadow: "var(--shadow-dashboard)",
        }}
      >
        {/* Inner Dashboard Window */}
        <div className="bg-background rounded-xl border border-border/80 shadow-xs overflow-hidden text-[11px] select-none pointer-events-none">
          {/* Top Bar */}
          <div className="h-11 px-3.5 border-b border-border bg-background flex items-center justify-between gap-4">
            {/* Left: Logo & Org Switcher */}
            <div className="flex items-center gap-2">
              <div className="h-5 w-5 rounded bg-foreground text-background flex items-center justify-center font-bold text-[10px] shadow-xs">
                N
              </div>
              <span className="font-semibold text-foreground text-xs tracking-tight">
                Nexora
              </span>
              <ChevronDown className="h-3 w-3 text-muted-foreground ml-0.5" />
            </div>

            {/* Middle: Search bar with ⌘K */}
            <div className="flex-1 max-w-sm">
              <div className="h-7 px-2.5 rounded-md bg-secondary/60 border border-border/60 flex items-center justify-between text-muted-foreground">
                <div className="flex items-center gap-2">
                  <Search className="h-3 w-3" />
                  <span className="text-[10px]">Search transactions, accounts...</span>
                </div>
                <kbd className="px-1.5 py-0.5 text-[9px] font-medium bg-background border border-border/80 rounded text-muted-foreground">
                  ⌘K
                </kbd>
              </div>
            </div>

            {/* Right: Actions & Profile */}
            <div className="flex items-center gap-2.5">
              <div className="h-6 px-2 rounded-md bg-primary text-primary-foreground font-medium text-[10px] flex items-center gap-1 shadow-xs">
                <span>Move Money</span>
              </div>
              <div className="h-6 w-6 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground">
                <Bell className="h-3.5 w-3.5" />
              </div>
              <div className="h-6 w-6 rounded-full bg-secondary border border-border text-foreground font-semibold text-[10px] flex items-center justify-center">
                JB
              </div>
            </div>
          </div>

          {/* App Body: Sidebar + Main Content */}
          <div className="flex min-h-[380px]">
            {/* Sidebar (w-40) */}
            <aside className="w-40 border-r border-border bg-background p-3 flex flex-col justify-between shrink-0">
              <div className="space-y-4">
                {/* Main Navigation */}
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2 px-2 py-1.5 rounded-md bg-secondary text-foreground font-medium">
                    <Home className="h-3.5 w-3.5 text-accent" />
                    <span>Home</span>
                  </div>
                  <div className="flex items-center justify-between px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <div className="flex items-center gap-2">
                      <CheckSquare className="h-3.5 w-3.5" />
                      <span>Tasks</span>
                    </div>
                    <span className="px-1.5 py-0.2 rounded-full text-[9px] font-semibold bg-accent/15 text-accent">
                      10
                    </span>
                  </div>
                  <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <ArrowLeftRight className="h-3.5 w-3.5" />
                    <span>Transactions</span>
                  </div>
                  <div className="flex items-center justify-between px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <div className="flex items-center gap-2">
                      <CreditCard className="h-3.5 w-3.5" />
                      <span>Payments</span>
                    </div>
                    <ChevronDown className="h-3 w-3" />
                  </div>
                  <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <CreditCard className="h-3.5 w-3.5" />
                    <span>Cards</span>
                  </div>
                  <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <Building2 className="h-3.5 w-3.5" />
                    <span>Capital</span>
                  </div>
                  <div className="flex items-center justify-between px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                    <div className="flex items-center gap-2">
                      <PieChart className="h-3.5 w-3.5" />
                      <span>Accounts</span>
                    </div>
                    <ChevronDown className="h-3 w-3" />
                  </div>
                </div>

                {/* Section "Workflows" */}
                <div>
                  <div className="px-2 text-[9px] font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                    Workflows
                  </div>
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                      <SlidersHorizontal className="h-3.5 w-3.5" />
                      <span>Trake rutes</span>
                    </div>
                    <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                      <CreditCard className="h-3.5 w-3.5" />
                      <span>Payments</span>
                    </div>
                    <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                      <Bell className="h-3.5 w-3.5" />
                      <span>Notifications</span>
                    </div>
                    <div className="flex items-center gap-2 px-2 py-1.5 rounded-md text-muted-foreground hover:text-foreground">
                      <Settings className="h-3.5 w-3.5" />
                      <span>Settings</span>
                    </div>
                  </div>
                </div>
              </div>
            </aside>

            {/* Main Content Area */}
            <main className="flex-1 bg-secondary/30 p-4 space-y-4 overflow-hidden">
              {/* Top Greeting */}
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-semibold text-foreground tracking-tight">
                    Welcome, Jane
                  </h2>
                  <p className="text-[10px] text-muted-foreground">
                    Your real-time automated financial overview
                  </p>
                </div>
              </div>

              {/* Action Buttons Row */}
              <div className="flex items-center gap-1.5 flex-wrap">
                <button className="h-6 px-3 rounded-full bg-accent text-accent-foreground font-medium text-[10px] flex items-center gap-1 shadow-xs">
                  <Send className="h-2.5 w-2.5" />
                  <span>Send</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <Download className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Request</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <ArrowLeftRight className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Transfer</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <Upload className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Deposit</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <Receipt className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Pay Bill</span>
                </button>
                <button className="h-6 px-3 rounded-full bg-background border border-border text-foreground font-medium text-[10px] flex items-center gap-1 shadow-2xs">
                  <FileCheck className="h-2.5 w-2.5 text-muted-foreground" />
                  <span>Create Invoice</span>
                </button>
                <span className="text-[10px] text-muted-foreground hover:text-foreground ml-2 font-medium">
                  + Customize
                </span>
              </div>

              {/* Two Equal-Width Cards */}
              <div className="flex gap-3">
                {/* Balance Card */}
                <div className="flex-1 basis-0 bg-background rounded-lg border border-border p-3.5 flex flex-col justify-between shadow-2xs">
                  <div>
                    <div className="flex items-center justify-between text-muted-foreground mb-1.5">
                      <div className="flex items-center gap-1.5">
                        <div className="h-3.5 w-3.5 rounded-full bg-emerald-50 text-emerald-600 border border-emerald-200 flex items-center justify-center">
                          <Check className="h-2 w-2 stroke-[3]" />
                        </div>
                        <span className="font-medium text-foreground text-[11px]">
                          Mercury Balance
                        </span>
                      </div>
                      <span className="text-[10px]">Active</span>
                    </div>

                    <div className="mt-1">
                      <span className="text-xl font-semibold tracking-tight text-foreground font-body">
                        $8,450,190
                      </span>
                      <span className="text-xs text-muted-foreground font-medium">
                        .32
                      </span>
                    </div>

                    {/* Stats */}
                    <div className="flex items-center gap-2 mt-1 text-[10px]">
                      <span className="text-muted-foreground">Last 30 Days</span>
                      <span className="text-emerald-600 font-semibold">
                        +$1.8M
                      </span>
                      <span className="text-rose-600 font-semibold">
                        -$900K
                      </span>
                    </div>
                  </div>

                  {/* SVG Area Chart with smooth cubic Bézier curve */}
                  <div className="mt-2.5 w-full h-20 relative overflow-hidden">
                    <svg
                      viewBox="0 0 360 80"
                      preserveAspectRatio="none"
                      className="w-full h-20"
                    >
                      <defs>
                        <linearGradient
                          id="dashboardBalanceGradient"
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

                      {/* Area Fill */}
                      <path
                        d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14 L 360 80 L 0 80 Z"
                        fill="url(#dashboardBalanceGradient)"
                      />

                      {/* Area Stroke */}
                      <path
                        d="M 0 58 C 45 62, 85 42, 125 45 C 165 48, 195 24, 235 28 C 275 32, 310 10, 360 14"
                        fill="none"
                        stroke="hsl(var(--accent))"
                        strokeWidth="1.5"
                        strokeLinecap="round"
                      />
                    </svg>
                  </div>
                </div>

                {/* Accounts Card */}
                <div className="flex-1 basis-0 bg-background rounded-lg border border-border p-3.5 flex flex-col shadow-2xs">
                  {/* Header */}
                  <div className="flex items-center justify-between pb-1.5 border-b border-border/50">
                    <span className="font-semibold text-foreground text-xs">
                      Accounts
                    </span>
                    <div className="flex items-center gap-1 text-muted-foreground">
                      <div className="p-0.5 hover:text-foreground">
                        <Plus className="h-3 w-3" />
                      </div>
                      <div className="p-0.5 hover:text-foreground">
                        <MoreVertical className="h-3 w-3" />
                      </div>
                    </div>
                  </div>

                  {/* 3 Rows (py-3, no dividers, text-xs, justify-between) */}
                  <div className="flex-1 flex flex-col justify-around py-1">
                    <div className="flex items-center justify-between py-3 text-xs">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-2 rounded-full bg-accent" />
                        <span className="text-foreground font-medium">Credit</span>
                      </div>
                      <span className="font-semibold text-foreground font-body">
                        $98,125.50
                      </span>
                    </div>

                    <div className="flex items-center justify-between py-3 text-xs">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-2 rounded-full bg-emerald-500" />
                        <span className="text-foreground font-medium">Treasury</span>
                      </div>
                      <span className="font-semibold text-foreground font-body">
                        $6,750,200.00
                      </span>
                    </div>

                    <div className="flex items-center justify-between py-3 text-xs">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-2 rounded-full bg-blue-500" />
                        <span className="text-foreground font-medium">Operations</span>
                      </div>
                      <span className="font-semibold text-foreground font-body">
                        $1,592,864.82
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Transactions Table */}
              <div className="bg-background rounded-lg border border-border p-3 shadow-2xs">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-xs font-semibold text-foreground">
                    Recent Transactions
                  </h3>
                  <span className="text-[10px] text-muted-foreground hover:text-foreground font-medium">
                    View all
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left">
                    <thead>
                      <tr className="border-b border-border/60 text-[10px] text-muted-foreground uppercase font-medium">
                        <th className="pb-1.5 font-medium">Date</th>
                        <th className="pb-1.5 font-medium">Description</th>
                        <th className="pb-1.5 font-medium text-right">Amount</th>
                        <th className="pb-1.5 font-medium text-right">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/40 text-[11px]">
                      {/* Row 1: AWS -$5,200 Pending (amber) */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 07</td>
                        <td className="py-2 font-medium text-foreground">AWS</td>
                        <td className="py-2 text-right font-medium text-foreground">
                          -$5,200.00
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-amber-500/10 text-amber-700 border border-amber-500/20">
                            Pending
                          </span>
                        </td>
                      </tr>

                      {/* Row 2: Client Payment +$125,000 Completed (green) */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 06</td>
                        <td className="py-2 font-medium text-foreground">
                          Client Payment
                        </td>
                        <td className="py-2 text-right font-medium text-emerald-600">
                          +$125,000.00
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                            Completed
                          </span>
                        </td>
                      </tr>

                      {/* Row 3: Payroll -$85,450 Completed */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 04</td>
                        <td className="py-2 font-medium text-foreground">Payroll</td>
                        <td className="py-2 text-right font-medium text-foreground">
                          -$85,450.00
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                            Completed
                          </span>
                        </td>
                      </tr>

                      {/* Row 4: Office Supplies -$1,200 Completed */}
                      <tr>
                        <td className="py-2 text-muted-foreground">Oct 02</td>
                        <td className="py-2 font-medium text-foreground">
                          Office Supplies
                        </td>
                        <td className="py-2 text-right font-medium text-foreground">
                          -$1,200.00
                        </td>
                        <td className="py-2 text-right">
                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-medium bg-emerald-500/10 text-emerald-700 border border-emerald-500/20">
                            Completed
                          </span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </main>
          </div>
        </div>
      </div>
    </motion.div>
  );
};
