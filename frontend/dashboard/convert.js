import { readFileSync, writeFileSync } from 'fs';

let html = readFileSync('index.html', 'utf-8');

// Extract styles
const styleMatch = html.match(/<style>([\s\S]*?)<\/style>/);
const styles = styleMatch ? styleMatch[1] : '';

// Extract body
const bodyMatch = html.match(/<body>([\s\S]*?)<\/body>/);
let body = bodyMatch ? bodyMatch[1] : '';

// Adapt texts to UrLeads
body = body.replace(/Weather Forecast — Central Jakarta/g, 'Dashboard — UrLeads');
body = body.replace(/Aurora Weather/g, 'UrLeads CRM');
body = body.replace(/Calfin Danang/g, 'Sebas');
body = body.replace(/Welcome/g, 'Hola,');
body = body.replace(/Weather Forecast/g, 'Tu Pipeline');
body = body.replace(/Strom/g, 'Cierra');
body = body.replace(/with Heavy Rain/g, 'más Ventas');
body = body.replace(/Partly cloudy with occasional snow showers.*?less than an inch\./g, 'Tu resumen de ventas, leads calificados y próximos seguimientos. Todo en un solo lugar y en tiempo real.');
body = body.replace(/Central Jakarta/g, 'Leads Activos');
body = body.replace(/10° C/g, '$120,450');
body = body.replace(/19 mph/g, '45 Leads');
body = body.replace(/40%/g, '24%');
body = body.replace(/15km\/h/g, '34K ARR');
body = body.replace(/Indonesia \/ North Jakarta \/ Mostly Sunny/g, 'Consultoría IT - Carlos Torres');
body = body.replace(/Indonesia \/ Bandung \/ Cloudy/g, 'Plan Enterprise - Tech Solutions');
body = body.replace(/Indonesia \/ South Jakarta \/ Sunny/g, 'Marketing - María López');
body = body.replace(/12°/g, 'Ganado');
body = body.replace(/10°/g, 'Activo');
body = body.replace(/14°/g, 'Nuevo');
body = body.replace(/Explore regions/g, 'Leads');
body = body.replace(/Calendar/g, 'Tareas');
body = body.replace(/Reports/g, 'Reportes');
body = body.replace(/Settings/g, 'Ajustes');
body = body.replace(/Sign out/g, 'Cerrar sesión');
body = body.replace(/Sunday/g, 'Lun');
body = body.replace(/Monday/g, 'Mar');
body = body.replace(/Tuesday/g, 'Mié');
body = body.replace(/Wednesday/g, 'Jue');
body = body.replace(/Thursday/g, 'Vie');
body = body.replace(/Friday/g, 'Sáb');
body = body.replace(/assets\/storm-background.jpg/g, '/assets/storm-background.jpg'); // Adjust for public dir

// Reactify attributes
body = body.replace(/class=/g, 'className=');
body = body.replace(/for=/g, 'htmlFor=');
body = body.replace(/stroke-width=/g, 'strokeWidth=');
body = body.replace(/stroke-dasharray=/g, 'strokeDasharray=');
body = body.replace(/stroke-dashoffset=/g, 'strokeDashoffset=');
body = body.replace(/stroke-linecap=/g, 'strokeLinecap=');
body = body.replace(/stroke-linejoin=/g, 'strokeLinejoin=');
body = body.replace(/clip-path=/g, 'clipPath=');
body = body.replace(/fill-rule=/g, 'fillRule=');
body = body.replace(/clip-rule=/g, 'clipRule=');
body = body.replace(/viewbox=/gi, 'viewBox=');
body = body.replace(/preserveaspectratio=/gi, 'preserveAspectRatio=');
body = body.replace(/vector-effect=/g, 'vectorEffect=');

body = body.replace(/style="([^"]*)"/g, (match, p1) => {
    const styleObj = p1.split(';').filter(s => s.trim()).map(s => {
        let [k, v] = s.split(':');
        k = k.trim().replace(/-([a-z])/g, (g) => g[1].toUpperCase());
        return `'${k}': '${v.trim()}'`;
    }).join(', ');
    return `style={{ ${styleObj} }}`;
});
// Remove comments
body = body.replace(/<!--[\s\S]*?-->/g, '');

const reactComponent = `import React from 'react';
import './AuroraDashboard.css';

export default function AuroraDashboard() {
  return (
    <div className="aurora-wrapper">
      <div className="stage" />
      ${body}
    </div>
  );
}
`;

writeFileSync('../src/pages/AuroraDashboard.tsx', reactComponent);

// Ensure CSS doesn't bleed into rest of the app by nesting it if possible, but since we are replacing the dashboard, we'll just prepend the wrapper class to body selectors
let finalStyles = styles.replace(/html, body {/g, '.aurora-wrapper {\n  width: 100vw;\n  height: 100vh;\n  overflow: hidden;\n  background-color: #04121b;\n  color: var(--ink);\n  font-family: "Inter", sans-serif;\n');
finalStyles = finalStyles.replace(/\*\, \*::before\, \*::after/g, '.aurora-wrapper *, .aurora-wrapper *::before, .aurora-wrapper *::after');
// Any other global selectors need scoping or we just let it be since this is a dedicated page
writeFileSync('../src/pages/AuroraDashboard.css', finalStyles);

console.log('Done!');
