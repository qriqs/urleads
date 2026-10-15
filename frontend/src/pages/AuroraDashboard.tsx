import { Link, useLocation, Outlet, useNavigate } from 'react-router-dom';
import { Users, DollarSign, Activity, FileText, CheckCircle, Target, Grid, BarChart2, Calendar, Settings } from 'lucide-react';
import React from 'react';
import './AuroraDashboard.css';
import { useAuth } from '../context/AuthContext';

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
          <button type="button" className="tool" aria-label="Add location">
            <svg><use href="#i-plus"></use></svg>
          </button>
          <button type="button" className="tool" aria-label="Search">
            <svg><use href="#i-search"></use></svg>
          </button>
          <button type="button" className="tool" aria-label="Notifications">
            <svg><use href="#i-bell"></use></svg>
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
        <div className="chip">Tu Pipeline</div>
        <h1>
          <span className="ln"><span>Cierra</span></span>
          <span className="ln"><span>más Ventas</span></span>
        </h1>
        <p className="blurb">
          Tu resumen de ventas, leads calificados y próximos seguimientos.<br />
          Monitorea el valor de tu pipeline, tasas de conversión y<br />
          tareas pendientes para hoy. Todo en tiempo real.
        </p>
      </section>
          <section className="forecast">
        <div className="forecast-temps">
          <div className="ft-item">
            <span className="ft-val">$12K</span>
            <Target className="ft-icon" strokeWidth={2} />
          </div>
          <div className="ft-item">
            <span className="ft-val">$45K</span>
            <CheckCircle className="ft-icon" strokeWidth={2} />
          </div>
          <div className="ft-item">
            <span className="ft-val">$58K</span>
            <CheckCircle className="ft-icon" strokeWidth={2} />
          </div>
          <div className="ft-item">
            <span className="ft-val">$80K</span>
            <Users className="ft-icon" strokeWidth={2} />
          </div>
          <div className="ft-item">
            <span className="ft-val">$84K</span>
            <Activity className="ft-icon" strokeWidth={2} />
          </div>
          <div className="ft-item">
            <span className="ft-val">$120K</span>
            <Target className="ft-icon" strokeWidth={2} />
          </div>
        </div>

        <svg className="wave-chart" viewBox="0 0 835 230" preserveAspectRatio="none">
          <defs>
            
            <linearGradient id="wg" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="#ffffff" stop-opacity="0.3"/>
              <stop offset="25%" stop-color="#ffffff" stop-opacity="0.85"/>
              <stop offset="50%" stop-color="#ffffff" stop-opacity="1"/>
              <stop offset="75%" stop-color="#ffffff" stop-opacity="0.9"/>
              <stop offset="100%" stop-color="#ffffff" stop-opacity="0.4"/>
            </linearGradient>

            
            <linearGradient id="wf" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="#ffffff" stop-opacity="0.32"/>
              <stop offset="45%" stop-color="#ffffff" stop-opacity="0.14"/>
              <stop offset="100%" stop-color="#ffffff" stop-opacity="0.0"/>
            </linearGradient>

            
            <mask id="wfade">
              <linearGradient id="wfade-grad" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="#ffffff" stop-opacity="1"/>
                <stop offset="70%" stop-color="#ffffff" stop-opacity="0.5"/>
                <stop offset="100%" stop-color="#ffffff" stop-opacity="0"/>
              </linearGradient>
              <rect x="0" y="0" width="835" height="230" fill="url(#wfade-grad)"/>
            </mask>

            
            <clipPath id="wclip">
              <rect id="wclipr" x="0" y="0" width="835" height="230" />
            </clipPath>
          </defs>

          
          <path className="wfill" clipPath="url(#wclip)" mask="url(#wfade)"
                d="M0,79 C 80,75 110,68 170,68 C 240,68 280,60 340,60 C 410,60 440,98 505,98 C 570,98 610,22 675,22 C 740,22 780,80 835,86 L835,230 L0,230 Z"
                fill="url(#wf)" />

          
          <path className="wline wline-outer"
                d="M0,79 C 80,75 110,68 170,68 C 240,68 280,60 340,60 C 410,60 440,98 505,98 C 570,98 610,22 675,22 C 740,22 780,80 835,86"
                fill="none" stroke="url(#wg)" strokeWidth="6.2" opacity="0.17" strokeLinecap="round" pathLength="1" />
          <path className="wline wline-mid"
                d="M0,79 C 80,75 110,68 170,68 C 240,68 280,60 340,60 C 410,60 440,98 505,98 C 570,98 610,22 675,22 C 740,22 780,80 835,86"
                fill="none" stroke="url(#wg)" strokeWidth="4.6" opacity="0.26" strokeLinecap="round" pathLength="1" />
          <path className="wline wline-core"
                d="M0,79 C 80,75 110,68 170,68 C 240,68 280,60 340,60 C 410,60 440,98 505,98 C 570,98 610,22 675,22 C 740,22 780,80 835,86"
                fill="none" stroke="url(#wg)" strokeWidth="3.4" opacity="1" strokeLinecap="round" pathLength="1" />
        </svg>

        <div className="forecast-days">
          <div className="day">Ene</div>
          <div className="day">Feb</div>
          <div className="day">Mar</div>
          <div className="day on">Abr</div>
          <div className="day">May</div>
          <div className="day">Jun</div>
        </div>
      </section>
          <aside className="rail">
        
        <div className="card big">
          <div className="card-loc">
            <span>Leads Activos</span>
            <Target className="loc-pin" strokeWidth={2} />
          </div>

          <div className="big-temp">$120K</div>

          <div className="card-metrics">
            <div className="metric">
              <Activity className="m-icon" strokeWidth={2} />
              <span>45 Leads</span>
            </div>
            <div className="metric">
              <FileText className="m-icon" strokeWidth={2} />
              <span>24%</span>
            </div>
            <div className="metric">
              <DollarSign className="m-icon" strokeWidth={2} />
              <span>34K ARR</span>
            </div>
          </div>
        </div>

        
        <div className="card card-row">
          <div className="row-info">
            <span className="row-country">Consultoría IT</span>
<h3 className="row-city">Carlos Torres</h3>
<span className="row-cond">Hace 2 horas</span>
          </div>
          <div className="row-temp-box">
            <span className="row-temp">Ganado</span>
            <CheckCircle className="row-icon" color="#10b981" strokeWidth={2} />
          </div>
        </div>

        
        <div className="card card-row">
          <div className="row-info">
            <span className="row-country">Plan Enterprise</span>
<h3 className="row-city">Tech Solutions</h3>
<span className="row-cond">Hace 5 horas</span>
          </div>
          <div className="row-temp-box">
            <span className="row-temp">Activo</span>
            <CheckCircle className="row-icon" color="#10b981" strokeWidth={2} />
          </div>
        </div>

        
        <div className="card card-row">
          <div className="row-info">
            <span className="row-country">Marketing</span>
<h3 className="row-city">María López</h3>
<span className="row-cond">Ayer</span>
          </div>
          <div className="row-temp-box">
            <span className="row-temp">Nuevo</span>
            <Target className="row-icon" color="#3b82f6" strokeWidth={2} />
          </div>
        </div>
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
