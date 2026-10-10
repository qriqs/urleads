import { readFileSync, writeFileSync } from 'fs';

// Fix Background in CSS
let css = readFileSync('../src/pages/AuroraDashboard.css', 'utf-8');
css = css.replace(/background-image: url\("\.\/assets\/storm-background\.jpg"\);/g, 'background-image: linear-gradient(135deg, #020617 0%, #0f172a 50%, #1e293b 100%);');
css = css.replace(/background-position: center 25%;/g, 'background-position: center;');

// The pip animation grows from scaleY(0) to scaleY(1). I need to make sure we can set 'top' dynamically.
// I will just remove the hardcoded 'top' from CSS, and set it via style in JSX.
css = css.replace(/top: calc\(131 \* var\(--u\)\);/g, '/* top set via style */ transition: top 0.3s cubic-bezier(0.22, 0.61, 0.36, 1);');
writeFileSync('../src/pages/AuroraDashboard.css', css);

// Now Fix TSX
let jsx = readFileSync('../src/pages/AuroraDashboard.tsx', 'utf-8');

// We need to use useLocation to calculate the active index.
jsx = jsx.replace(/export default function AuroraDashboard\(\) \{/, 
`import { useLocation } from 'react-router-dom';
export default function AuroraDashboard() {
  const location = useLocation();
  const path = location.pathname;
  
  let activeIndex = 0;
  if (path === '/app/reportes') activeIndex = 1;
  else if (path.startsWith('/app/leads')) activeIndex = 2;
  else if (path === '/app/tareas') activeIndex = 3;
  else if (path === '/app/ajustes') activeIndex = 4;
  
  const pipTop = 131 + (activeIndex * 66);
`);

// Apply pipTop to pip
jsx = jsx.replace(/<div className="pip"><\/div>/, `<div className="pip" style={{ top: \`calc(\${pipTop} * var(--u))\` }}></div>`);

// Update Nav Links to have proper paths and highlight them if needed. 
// Note: SVG icons in nav. The user wanted me to use lucide icons. Let's replace the <svg><use ...></svg> with Lucide icons.
// Also we need to import Grid, BarChart2, Calendar, Settings.
jsx = jsx.replace(/import \{ Link \} from 'react-router-dom';\nimport \{ Users, DollarSign, Activity, FileText, CheckCircle, Target, Sun, Cloud, CloudRain, Wind, Droplet, Navigation \} from 'lucide-react';/, 
`import { Link } from 'react-router-dom';
import { Users, DollarSign, Activity, FileText, CheckCircle, Target, Grid, BarChart2, Calendar, Settings } from 'lucide-react';`);

// Link 1: Dashboard
jsx = jsx.replace(/<Link to="\/app" aria-label="Dashboard" aria-current="page"><svg><use href="#i-grid"><\/use><\/svg><\/Link>/, 
`<Link to="/app" aria-label="Dashboard" aria-current={activeIndex === 0 ? "page" : undefined}><Grid strokeWidth={2} /></Link>`);

// Link 2: Reportes
jsx = jsx.replace(/<Link to="\/app" aria-label="Reportes"><svg><use href="#i-chart"><\/use><\/svg><\/Link>/, 
`<Link to="/app/reportes" aria-label="Reportes" aria-current={activeIndex === 1 ? "page" : undefined}><BarChart2 strokeWidth={2} /></Link>`);

// Link 3: Leads
jsx = jsx.replace(/<Link to="\/app\/leads" aria-label="Leads"><Users strokeWidth={2} \/><\/Link>/, 
`<Link to="/app/leads" aria-label="Leads" aria-current={activeIndex === 2 ? "page" : undefined}><Users strokeWidth={2} /></Link>`);

// Link 4: Tareas
jsx = jsx.replace(/<Link to="\/app" aria-label="Tareas"><svg><use href="#i-cal"><\/use><\/svg><\/Link>/, 
`<Link to="/app/tareas" aria-label="Tareas" aria-current={activeIndex === 3 ? "page" : undefined}><Calendar strokeWidth={2} /></Link>`);

// Link 5: Ajustes
jsx = jsx.replace(/<Link to="\/app" aria-label="Ajustes"><svg><use href="#i-gear"><\/use><\/svg><\/Link>/, 
`<Link to="/app/ajustes" aria-label="Ajustes" aria-current={activeIndex === 4 ? "page" : undefined}><Settings strokeWidth={2} /></Link>`);

// Now wrap the Dashboard specific content (hero, forecast, rail) in a condition so it only renders on '/app'. 
// We will use the Outlet to render other pages (like /app/leads) in the main area.
jsx = jsx.replace(/import \{ useLocation \} from 'react-router-dom';/, `import { useLocation, Outlet } from 'react-router-dom';`);

const heroRegex = /<section className="hero">[\s\S]*?<\/section>/;
const forecastRegex = /<section className="forecast">[\s\S]*?<\/section>/;
const railRegex = /<aside className="rail">[\s\S]*?<\/aside>/;

const heroStr = jsx.match(heroRegex)[0];
const forecastStr = jsx.match(forecastRegex)[0];
const railStr = jsx.match(railRegex)[0];

const fullDashboardContent = `
      {activeIndex === 0 ? (
        <>
          ${heroStr}
          ${forecastStr}
          ${railStr}
        </>
      ) : (
        <div style={{ position: 'absolute', top: 'calc(136 * var(--u))', left: 'calc(126 * var(--u))', right: 'calc(38 * var(--u))', bottom: 'calc(38 * var(--u))', zIndex: 5, overflow: 'auto' }}>
          <div style={{ background: 'rgba(255,255,255,0.05)', backdropFilter: 'blur(16px)', borderRadius: '24px', padding: '32px', minHeight: '100%', border: '1px solid rgba(255,255,255,0.1)' }}>
             <Outlet />
          </div>
        </div>
      )}
`;

jsx = jsx.replace(heroRegex, '');
jsx = jsx.replace(forecastRegex, '');
jsx = jsx.replace(railRegex, fullDashboardContent);

writeFileSync('../src/pages/AuroraDashboard.tsx', jsx);
console.log('Fixed AuroraDashboard selectable menus and bg');
