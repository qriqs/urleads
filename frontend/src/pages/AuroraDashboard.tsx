import { Link, useLocation, Outlet, useNavigate } from 'react-router-dom';
import {
  Users,
  DollarSign,
  Activity,
  FileText,
  CheckCircle,
  Target,
  Grid,
  BarChart2,
  Calendar,
  Settings,
  AlertCircle,
  Clock,
  ArrowRight,
  Sparkles,
  Plus,
  RefreshCw,
} from 'lucide-react';
import React, { useState, useEffect, useCallback } from 'react';
import './AuroraDashboard.css';
import { useAuth } from '../context/AuthContext';
import {
  getDashboardMetricsApi,
  DashboardMetrics,
  DashboardApiError,
  formatDayDate,
} from '../services/dashboard';

export default function AuroraDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;
  
  let activeIndex = 0;
  if (path === '/app/reportes') activeIndex = 1;
  else if (path.startsWith('/app/leads')) activeIndex = 2;
  else if (path === '/app/tareas') activeIndex = 3;
  else if (path === '/app/ajustes') activeIndex = 4;
  
  const pipTop = 131 + (activeIndex * 66);

  // Real dashboard metrics state
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [isLoadingMetrics, setIsLoadingMetrics] = useState<boolean>(true);
  const [metricsError, setMetricsError] = useState<DashboardApiError | null>(null);

  const fetchMetrics = useCallback(async () => {
    setIsLoadingMetrics(true);
    setMetricsError(null);
    try {
      const data = await getDashboardMetricsApi();
      setMetrics(data);
    } catch (err: any) {
      if (err?.status === 401) {
        navigate('/login');
        return;
      }
      setMetricsError(err);
    } finally {
      setIsLoadingMetrics(false);
    }
  }, [navigate]);

  useEffect(() => {
    if (activeIndex === 0) {
      fetchMetrics();
    }
  }, [activeIndex, fetchMetrics]);

  const totalLeads = metrics?.totalLeads ?? 0;
  const newCount = metrics?.leadsPorEtapa?.['NUEVO'] ?? 0;
  const inProgressCount = metrics?.leadsPorEtapa?.['EN_SEGUIMIENTO'] ?? 0;
  const closedCount = metrics?.leadsPorEtapa?.['CERRADO'] ?? 0;

  const newPercent = totalLeads > 0 ? Math.round((newCount / totalLeads) * 100) : 0;
  const inProgressPercent = totalLeads > 0 ? Math.round((inProgressCount / totalLeads) * 100) : 0;
  const closedPercent = totalLeads > 0 ? Math.round((closedCount / totalLeads) * 100) : 0;

  const pendingLeads = [
    ...(metrics?.seguimientos?.leadsAtrasados ?? []),
    ...(metrics?.seguimientos?.leadsHoy ?? []),
  ];


  return (
    <div className="aurora-wrapper">
      <div className="stage">
        <video 
          autoPlay 
          loop 
          muted 
          playsInline 
          style={{
            position: 'absolute',
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            top: 0,
            left: 0,
            zIndex: -1,
            opacity: 0.8
          }}
        >
          <source src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260330_153826_e9005cf7-a1c7-4c7d-886f-fea22d644a9c.mp4" type="video/mp4" />
        </video>
      </div>
      
    
    <svg style={{ 'display': 'none' }} xmlns="http://www.w3.org/2000/svg">
      <defs>
        
        <symbol id="i-grid" viewBox="0 0 24 24">
          <rect x="3" y="3" width="7.5" height="7.5" rx="2.2" fill="none" stroke="currentColor" strokeWidth="2"/>
          <rect x="13.5" y="3" width="7.5" height="7.5" rx="2.2" fill="none" stroke="currentColor" strokeWidth="2"/>
          <rect x="3" y="13.5" width="7.5" height="7.5" rx="2.2" fill="none" stroke="currentColor" strokeWidth="2"/>
          <rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2.2" fill="none" stroke="currentColor" strokeWidth="2"/>
        </symbol>

        
        <symbol id="i-chart" viewBox="0 0 24 24">
          <path d="M4 19h16 M7 15v-4 M12 15V7 M17 15v-6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-globe" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="2"/>
          <ellipse cx="12" cy="12" rx="4.5" ry="9" fill="none" stroke="currentColor" strokeWidth="1.8"/>
          <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" strokeWidth="1.8"/>
        </symbol>

        
        <symbol id="i-cal" viewBox="0 0 24 24">
          <rect x="3" y="4" width="18" height="17" rx="3" fill="none" stroke="currentColor" strokeWidth="2"/>
          <line x1="3" y1="9" x2="21" y2="9" stroke="currentColor" strokeWidth="2"/>
          <line x1="8" y1="2" x2="8" y2="5" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          <line x1="16" y1="2" x2="16" y2="5" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-gear" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="3.2" fill="none" stroke="currentColor" strokeWidth="2"/>
          <path d="M12 2v2.5m0 15V22m10-10h-2.5m-15 0H2m16.07-7.07l-1.77 1.77m-8.6 8.6l-1.77 1.77m12.14 0l-1.77-1.77m-8.6-8.6L3.93 4.93" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-out" viewBox="0 0 24 24">
          <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4m7 14l5-5-5-5m5 5H9" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </symbol>

        
        <symbol id="i-plus" viewBox="0 0 24 24">
          <line x1="12" y1="5" x2="12" y2="19" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"/>
          <line x1="5" y1="12" x2="19" y2="12" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-search" viewBox="0 0 24 24">
          <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="2"/>
          <line x1="16" y1="16" x2="21" y2="21" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-bell" viewBox="0 0 24 24">
          <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <path d="M13.73 21a2 2 0 01-3.46 0" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-pin" viewBox="0 0 24 24">
          <path d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          <circle cx="12" cy="9" r="2.5" fill="currentColor"/>
        </symbol>

        
        <symbol id="i-wind" viewBox="0 0 24 24">
          <path d="M4 8h11a2.5 2.5 0 000-5c-1.38 0-2.5 1.12-2.5 2.5 M2 12h16a3 3 0 000-6c-1.65 0-3 1.35-3 3 M4 16h8a2 2 0 000-4c-1.1 0-2 .9-2 2" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-drop" viewBox="0 0 24 24">
          <path d="M12 2.69l5.66 5.66a8 8 0 11-11.31 0z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
        </symbol>

        
        <symbol id="i-gust" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.8"/>
          <path d="M12 7v5l3 3" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-cloud" viewBox="0 0 24 24">
          <path d="M18 10h-.63A5.5 5.5 0 007 11.5a4 4 0 00.5 7.97H18a4.5 4.5 0 000-9z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
        </symbol>

        
        <symbol id="i-cloud2" viewBox="0 0 24 24">
          <circle cx="8" cy="8" r="3" fill="none" stroke="currentColor" strokeWidth="1.8"/>
          <path d="M8 2.5v2M8 11.5v2M2.5 8h2M13.5 8h2M4.1 4.1l1.4 1.4M10.5 10.5l1.4 1.4M4.1 11.9l1.4-1.4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/>
          <path d="M19 14h-.5A4.2 4.2 0 0011 15.2a3.2 3.2 0 00.4 6.38H19a3.6 3.6 0 000-7.2z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
        </symbol>

        
        <symbol id="i-hail" viewBox="0 0 24 24">
          <path d="M18 10h-.63A5.5 5.5 0 007 11.5a4 4 0 00.5 7.97H18a4.5 4.5 0 000-9z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
          <line x1="8" y1="18" x2="8" y2="20" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          <line x1="12" y1="18" x2="12" y2="20" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          <line x1="16" y1="18" x2="16" y2="20" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-sun" viewBox="0 0 24 24">
          <circle cx="12" cy="12" r="4.5" fill="none" stroke="currentColor" strokeWidth="2"/>
          <path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.93 4.93l1.77 1.77M17.3 17.3l1.77 1.77M4.93 19.07l1.77-1.77M17.3 6.7l1.77-1.77" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </symbol>

        
        <symbol id="i-avatar" viewBox="0 0 24 24">
          <circle cx="12" cy="8" r="4" fill="none" stroke="currentColor" strokeWidth="2"/>
          <path d="M4 20c0-4 4-7 8-7s8 3 8 7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
        </symbol>
      </defs>
    </svg>

    
    <div className="stage">
      
      <aside className="sidebar">
        <div className="pip" style={{ top: `calc(${pipTop} * var(--u))` }}></div>

        <Link to="/" className="logo-btn" aria-label="UrLeads CRM">
          <svg viewBox="0 0 40 40" width="40" height="40">
            <defs>
              <clipPath id="logo-clip">
                <circle cx="20" cy="20" r="14" />
              </clipPath>
            </defs>
            <rect width="40" height="40" rx="12" fill="rgba(255,255,255,0.18)" stroke="rgba(255,255,255,0.25)" strokeWidth="1" />
            <g clipPath="url(#logo-clip)" stroke="#ffffff" strokeWidth="1.6" strokeLinecap="round" fill="none">
              <path d="M4,10 Q12,7 20,10 T36,10" />
              <path d="M4,13.5 Q12,10.5 20,13.5 T36,13.5" />
              <path d="M4,17 Q12,14 20,17 T36,17" />
              <path d="M4,20.5 Q12,17.5 20,20.5 T36,20.5" />
              <path d="M4,24 Q12,21 20,24 T36,24" />
              <path d="M4,27.5 Q12,24.5 20,27.5 T36,27.5" />
              <path d="M4,31 Q12,28 20,31 T36,31" />
            </g>
          </svg>
        </Link>

        <nav className="nav">
          <Link to="/app" aria-label="Dashboard" aria-current={activeIndex === 0 ? "page" : undefined}><Grid strokeWidth={2} /></Link>
          <Link to="/app/reportes" aria-label="Reportes" aria-current={activeIndex === 1 ? "page" : undefined}><BarChart2 strokeWidth={2} /></Link>
          <Link to="/app/leads" aria-label="Leads" aria-current={activeIndex === 2 ? "page" : undefined}><Users strokeWidth={2} /></Link>
          <Link to="/app/tareas" aria-label="Tareas" aria-current={activeIndex === 3 ? "page" : undefined}><Calendar strokeWidth={2} /></Link>
          <Link to="/app/ajustes" aria-label="Ajustes" aria-current={activeIndex === 4 ? "page" : undefined}><Settings strokeWidth={2} /></Link>
        </nav>

        <button
          type="button"
          onClick={async () => {
            await logout();
            navigate('/login');
          }}
          className="logout"
          aria-label="Cerrar sesión"
          style={{ background: 'none', border: 'none', cursor: 'pointer' }}
        >
          <svg><use href="#i-out"></use></svg>
        </button>
      </aside>

      
      <header className="header">
        <div className="header-user">
          <span className="welcome">Hola,</span>
          <span className="who">{user?.username || "Sebas"}</span>
        </div>

        <div className="header-tools">
          <button
            type="button"
            className="tool"
            aria-label="Nuevo Lead"
            title="Ir a leads y registrar prospecto"
            onClick={() => navigate('/app/leads')}
          >
            <svg><use href="#i-plus"></use></svg>
          </button>
          <button
            type="button"
            className="tool"
            aria-label="Buscar Leads"
            title="Buscar prospectos"
            onClick={() => navigate('/app/leads')}
          >
            <svg><use href="#i-search"></use></svg>
          </button>
          <button
            type="button"
            className="tool"
            aria-label="Refrescar métricas"
            title="Refrescar métricas del dashboard"
            onClick={() => fetchMetrics()}
          >
            <RefreshCw className={`w-4 h-4 text-white ${isLoadingMetrics ? 'animate-spin' : ''}`} />
          </button>
          <button 
            type="button" 
            onClick={async () => {
              await logout();
              navigate('/login');
            }}
            className="tool tool-avatar" 
            aria-label="Perfil / Cerrar sesión"
            title="Cerrar sesión"
            style={{ background: 'none', border: 'none', cursor: 'pointer' }}
          >
            <svg className="avatar-fallback"><use href="#i-avatar"></use></svg>
          </button>
        </div>
      </header>

      
      

      
      

      
      
      {activeIndex === 0 ? (
        <>
          <section className="hero">
            <div className="chip">
              <Sparkles className="w-3.5 h-3.5 text-cyan-300 inline mr-1" />
              <span>Resumen Comercial</span>
            </div>
            <h1>
              <span className="ln"><span>Cierra</span></span>
              <span className="ln"><span>más Ventas</span></span>
            </h1>
            <p className="blurb">
              Tu resumen de ventas, leads calificados y próximos seguimientos.<br />
              Monitorea el estado de tu pipeline, tasas de conversión y<br />
              tareas pendientes para hoy. Todo en tiempo real.
            </p>
          </section>

          {/* Central Pipeline Area */}
          <section className="forecast">
            {/* Quick Metrics Chips Row */}
            <div className="forecast-temps">
              <Link
                to="/app/leads"
                className="ft-item group"
                title="Ver todos los prospectos"
              >
                <span className="ft-val">{metrics ? totalLeads : (isLoadingMetrics ? "..." : 0)}</span>
                <div className="flex flex-col">
                  <span className="text-[11px] uppercase tracking-wider text-white/70 font-semibold">Total</span>
                  <span className="text-[10px] text-white/50">Prospectos</span>
                </div>
                <Users className="ft-icon group-hover:scale-110 transition-transform text-white/80" strokeWidth={2} />
              </Link>

              <Link
                to="/app/leads?stage=NUEVO"
                className="ft-item group"
                title="Filtrar prospectos Nuevos"
              >
                <span className="ft-val">{metrics ? newCount : (isLoadingMetrics ? "..." : 0)}</span>
                <div className="flex flex-col">
                  <span className="text-[11px] uppercase tracking-wider text-sky-300 font-semibold">Nuevos</span>
                  <span className="text-[10px] text-white/50">Por calificar</span>
                </div>
                <Target className="ft-icon group-hover:scale-110 transition-transform text-sky-400" strokeWidth={2} />
              </Link>

              <Link
                to="/app/leads?stage=EN_SEGUIMIENTO"
                className="ft-item group"
                title="Filtrar prospectos En Seguimiento"
              >
                <span className="ft-val">{metrics ? inProgressCount : (isLoadingMetrics ? "..." : 0)}</span>
                <div className="flex flex-col">
                  <span className="text-[11px] uppercase tracking-wider text-amber-300 font-semibold">Activos</span>
                  <span className="text-[10px] text-white/50">En gestión</span>
                </div>
                <Activity className="ft-icon group-hover:scale-110 transition-transform text-amber-400" strokeWidth={2} />
              </Link>

              <Link
                to="/app/leads?stage=CERRADO"
                className="ft-item group"
                title="Filtrar prospectos Cerrados"
              >
                <span className="ft-val">{metrics ? closedCount : (isLoadingMetrics ? "..." : 0)}</span>
                <div className="flex flex-col">
                  <span className="text-[11px] uppercase tracking-wider text-emerald-300 font-semibold">Cerrados</span>
                  <span className="text-[10px] text-white/50">Concretados</span>
                </div>
                <CheckCircle className="ft-icon group-hover:scale-110 transition-transform text-emerald-400" strokeWidth={2} />
              </Link>

              <Link
                to="/app/leads?followUp=today"
                className="ft-item group"
                title="Filtrar seguimientos programados para hoy"
              >
                <span className="ft-val">{metrics ? metrics.seguimientos.hoy : (isLoadingMetrics ? "..." : 0)}</span>
                <div className="flex flex-col">
                  <span className="text-[11px] uppercase tracking-wider text-cyan-300 font-semibold">Para Hoy</span>
                  <span className="text-[10px] text-white/50">Programados</span>
                </div>
                <Calendar className="ft-icon group-hover:scale-110 transition-transform text-cyan-300" strokeWidth={2} />
              </Link>

              <Link
                to="/app/leads?followUp=overdue"
                className="ft-item group"
                title="Filtrar seguimientos atrasados"
              >
                <span className="ft-val">{metrics ? metrics.seguimientos.atrasados : (isLoadingMetrics ? "..." : 0)}</span>
                <div className="flex flex-col">
                  <span className="text-[11px] uppercase tracking-wider text-rose-300 font-semibold">Atrasados</span>
                  <span className="text-[10px] text-white/50">Urgentes</span>
                </div>
                <AlertCircle className="ft-icon group-hover:scale-110 transition-transform text-rose-400" strokeWidth={2} />
              </Link>
            </div>

            {/* Central Liquid Glass Pipeline Card */}
            <div className="card mt-6 p-6 rounded-2xl border border-white/20 backdrop-blur-xl space-y-4">
              <div className="flex items-center justify-between pb-3 border-b border-white/10">
                <div className="flex items-center gap-2.5">
                  <div className="w-7 h-7 rounded-lg bg-emerald-400/20 border border-emerald-400/30 flex items-center justify-center text-emerald-300">
                    <BarChart2 className="w-4 h-4" />
                  </div>
                  <h2 className="text-base font-semibold text-white">
                    Distribución de tu Pipeline Comercial
                  </h2>
                </div>
                <span className="text-xs text-white/70 font-medium bg-white/10 px-2.5 py-1 rounded-full border border-white/10">
                  {totalLeads} {totalLeads === 1 ? "lead registrado" : "leads registrados"}
                </span>
              </div>

              {isLoadingMetrics ? (
                <div className="py-8 flex flex-col items-center justify-center gap-3 text-white/60">
                  <RefreshCw className="w-6 h-6 animate-spin text-emerald-400" />
                  <span className="text-xs">Sincronizando métricas en tiempo real...</span>
                </div>
              ) : metricsError ? (
                <div className="py-6 flex flex-col items-center justify-center gap-3 text-center">
                  <AlertCircle className="w-6 h-6 text-rose-400" />
                  <p className="text-xs text-rose-200">{metricsError.message}</p>
                  <button
                    type="button"
                    onClick={() => fetchMetrics()}
                    className="aurora-chip-btn h-8 px-4 text-xs font-medium text-white flex items-center gap-1.5 cursor-pointer"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    <span>Reintentar</span>
                  </button>
                </div>
              ) : totalLeads === 0 ? (
                <div className="py-8 px-4 text-center flex flex-col items-center justify-center gap-3">
                  <div className="w-12 h-12 rounded-2xl bg-white/5 border border-white/10 flex items-center justify-center text-white/40">
                    <Users className="w-6 h-6 text-cyan-300/70" />
                  </div>
                  <h3 className="text-sm font-semibold text-white">
                    Aún no tienes prospectos registrados
                  </h3>
                  <p className="text-xs text-white/60 max-w-md leading-relaxed">
                    Registra tu primer lead para comenzar a gestionar tu pipeline comercial y hacer seguimiento a tus ventas.
                  </p>
                  <Link
                    to="/app/leads"
                    className="aurora-chip-btn h-9 px-5 text-xs font-medium text-white inline-flex items-center gap-1.5 mt-2 hover:border-emerald-400/40 cursor-pointer"
                  >
                    <Plus className="w-4 h-4 text-emerald-400" />
                    <span>Registrar primer lead</span>
                  </Link>
                </div>
              ) : (
                <div className="space-y-4">
                  {/* Multi-segment Segmented Progress Bar */}
                  <div className="space-y-2">
                    <div className="h-3 w-full rounded-full bg-white/10 overflow-hidden flex p-0.5 gap-1">
                      {newPercent > 0 && (
                        <div
                          style={{ width: `${newPercent}%` }}
                          className="h-full rounded-full bg-gradient-to-r from-sky-400 to-sky-500 transition-all duration-500"
                          title={`Nuevos: ${newCount} (${newPercent}%)`}
                        />
                      )}
                      {inProgressPercent > 0 && (
                        <div
                          style={{ width: `${inProgressPercent}%` }}
                          className="h-full rounded-full bg-gradient-to-r from-amber-400 to-amber-500 transition-all duration-500"
                          title={`En seguimiento: ${inProgressCount} (${inProgressPercent}%)`}
                        />
                      )}
                      {closedPercent > 0 && (
                        <div
                          style={{ width: `${closedPercent}%` }}
                          className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-emerald-500 transition-all duration-500"
                          title={`Cerrados: ${closedCount} (${closedPercent}%)`}
                        />
                      )}
                    </div>

                    {/* Stage Distribution Legend with Direct Filter Navigation */}
                    <div className="flex flex-wrap items-center justify-between gap-3 text-xs pt-1">
                      <Link
                        to="/app/leads?stage=NUEVO"
                        className="flex items-center gap-1.5 hover:underline text-white/80 hover:text-sky-300"
                      >
                        <span className="w-2.5 h-2.5 rounded-full bg-sky-400 inline-block" />
                        <span>Nuevos: <strong>{newCount}</strong> ({newPercent}%)</span>
                      </Link>
                      <Link
                        to="/app/leads?stage=EN_SEGUIMIENTO"
                        className="flex items-center gap-1.5 hover:underline text-white/80 hover:text-amber-300"
                      >
                        <span className="w-2.5 h-2.5 rounded-full bg-amber-400 inline-block" />
                        <span>En Seguimiento: <strong>{inProgressCount}</strong> ({inProgressPercent}%)</span>
                      </Link>
                      <Link
                        to="/app/leads?stage=CERRADO"
                        className="flex items-center gap-1.5 hover:underline text-white/80 hover:text-emerald-300"
                      >
                        <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 inline-block" />
                        <span>Cerrados: <strong>{closedCount}</strong> ({closedPercent}%)</span>
                      </Link>
                    </div>
                  </div>

                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-3 border-t border-white/[0.08]">
                    <span className="text-[11px] text-white/50">
                      Horario y clasificación calculados bajo zona horaria <strong>America/Lima</strong>.
                    </span>
                    <div className="flex items-center gap-2">
                      <Link
                        to="/app/leads"
                        className="aurora-chip-btn h-8 px-4 text-xs font-medium text-white flex items-center gap-1.5 hover:border-emerald-400/40"
                      >
                        <span>Gestionar leads</span>
                        <ArrowRight className="w-3.5 h-3.5" />
                      </Link>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </section>

          {/* Right Rail: Real Follow-ups and Pending Contacts */}
          <aside className="rail">
            {/* Big Card: Pending Summary */}
            <div className="card big">
              <div className="card-loc">
                <span>Seguimientos</span>
                <Calendar className="loc-pin text-cyan-300" strokeWidth={2} />
              </div>

              <div className="big-temp">
                {metrics ? (metrics.seguimientos.hoy + metrics.seguimientos.atrasados) : (isLoadingMetrics ? "..." : 0)}
                <i>pendientes</i>
              </div>

              <div className="card-metrics">
                <Link
                  to="/app/leads?followUp=today"
                  className="metric hover:text-cyan-300 transition-colors"
                  title="Ver seguimientos para hoy"
                >
                  <Clock className="m-icon text-cyan-300" strokeWidth={2} />
                  <span>{metrics?.seguimientos?.hoy ?? 0} Hoy</span>
                </Link>
                <Link
                  to="/app/leads?followUp=overdue"
                  className="metric hover:text-rose-300 transition-colors"
                  title="Ver seguimientos atrasados"
                >
                  <AlertCircle className="m-icon text-rose-400" strokeWidth={2} />
                  <span>{metrics?.seguimientos?.atrasados ?? 0} Atrasados</span>
                </Link>
                <Link
                  to="/app/leads"
                  className="metric hover:text-emerald-300 transition-colors"
                  title="Ver total de prospectos"
                >
                  <Users className="m-icon text-emerald-400" strokeWidth={2} />
                  <span>{totalLeads} Total</span>
                </Link>
              </div>
            </div>

            {/* Row Cards: Real Priority Pending Leads */}
            {isLoadingMetrics ? (
              <div className="card card-row flex items-center justify-center text-white/50 text-xs">
                <RefreshCw className="w-4 h-4 animate-spin mr-2" />
                <span>Cargando pendientes...</span>
              </div>
            ) : pendingLeads.length > 0 ? (
              pendingLeads.slice(0, 3).map((lead) => {
                const isOverdue = metrics?.seguimientos?.leadsAtrasados.some((al) => al.id === lead.id);
                return (
                  <Link
                    key={lead.id}
                    to={`/app/leads/${lead.id}`}
                    className="card card-row group"
                    title={`Ver detalle de ${lead.nombre}`}
                  >
                    <div className="row-info">
                      <span className={`row-country ${isOverdue ? 'text-rose-300' : 'text-cyan-300'} font-medium`}>
                        {isOverdue ? 'Atrasado' : 'Para Hoy'} • {lead.etapa.replace('_', ' ')}
                      </span>
                      <h3 className="row-city group-hover:text-cyan-200 transition-colors line-clamp-1">
                        {lead.nombre}
                      </h3>
                      <span className="row-cond text-white/70">
                        {formatDayDate(lead.proximoSeguimiento)}
                      </span>
                    </div>
                    <div className="row-temp-box">
                      {isOverdue ? (
                        <AlertCircle className="row-icon text-rose-400 group-hover:scale-110 transition-transform" strokeWidth={2} />
                      ) : (
                        <Clock className="row-icon text-cyan-300 group-hover:scale-110 transition-transform" strokeWidth={2} />
                      )}
                    </div>
                  </Link>
                );
              })
            ) : (
              <div className="card card-row">
                <div className="row-info">
                  <span className="row-country text-emerald-300 font-medium">Todo al día</span>
                  <h3 className="row-city">Sin pendientes</h3>
                  <span className="row-cond text-white/70">No hay seguimientos atrasados ni para hoy</span>
                </div>
                <div className="row-temp-box">
                  <CheckCircle className="row-icon text-emerald-400" strokeWidth={2} />
                </div>
              </div>
            )}
          </aside>
        </>
      ) : (
        <div className="aurora-outlet-container">
          <div className="aurora-outlet-card">
             <Outlet />
          </div>
        </div>
      )}


    </div>

    
  
    </div>
  );
}
