import { readFileSync, writeFileSync } from 'fs';

let jsx = readFileSync('../src/pages/AuroraDashboard.tsx', 'utf-8');

// Replace <a> in nav with <Link>
jsx = `import { Link } from 'react-router-dom';\nimport { Users, DollarSign, Activity, FileText, CheckCircle, Target, Sun, Cloud, CloudRain, Wind, Droplet, Navigation } from 'lucide-react';\n` + jsx;

jsx = jsx.replace(/<a href="#" aria-label="Dashboard"([^>]*)>/g, '<Link to="/app" aria-label="Dashboard"$1>');
jsx = jsx.replace(/<a href="#" aria-label="Reportes"([^>]*)>/g, '<Link to="/app" aria-label="Reportes"$1>');
jsx = jsx.replace(/<a href="#" aria-label="Leads"([^>]*)>/g, '<Link to="/app/leads" aria-label="Leads"$1>');
jsx = jsx.replace(/<a href="#" aria-label="Tareas"([^>]*)>/g, '<Link to="/app" aria-label="Tareas"$1>');
jsx = jsx.replace(/<a href="#" aria-label="Ajustes"([^>]*)>/g, '<Link to="/app" aria-label="Ajustes"$1>');
jsx = jsx.replace(/<a href="#" className="logout"([^>]*)>/g, '<Link to="/" className="logout"$1>');
// Close Link tags
jsx = jsx.replace(/<\/a>/g, '</Link>');

// Wait, some other <a> tags exist (like logo and avatar). Let's fix logo.
jsx = jsx.replace(/<Link href="#" className="logo-btn" aria-label="UrLeads CRM">/, '<Link to="/" className="logo-btn" aria-label="UrLeads CRM">');
jsx = jsx.replace(/<Link href="#" className="tool tool-avatar"/, '<Link to="/" className="tool tool-avatar"');

// Fix Hero Text
jsx = jsx.replace(/Partly cloudy with occasional snow showers\. High around 50°F\.<br \/>/g, 'Tu resumen de ventas, leads calificados y próximos seguimientos.<br />');
jsx = jsx.replace(/Wind from the east 11 to 21 mph\. Snow chance is 24%, with<br \/>/g, 'Monitorea el valor de tu pipeline, tasas de conversión y<br />');
jsx = jsx.replace(/rainfall expected to be less than an inch\./g, 'tareas pendientes para hoy. Todo en tiempo real.');

// Remove script tag
jsx = jsx.replace(/<script type="module" src="\/src\/main\.tsx"><\/script>/g, '');

// Fix Big Card temp value
jsx = jsx.replace(/<div className="big-temp">Activo<i>C<\/i><\/div>/, '<div className="big-temp">$120K</div>');

// Fix row cards (Leads Recientes)
jsx = jsx.replace(/<span className="row-country">Indonesia<\/span>\s*<h3 className="row-city">North Jakarta<\/h3>\s*<span className="row-cond">Mostly Sunny<\/span>/, 
  '<span className="row-country">Consultoría IT</span>\n<h3 className="row-city">Carlos Torres</h3>\n<span className="row-cond">Hace 2 horas</span>');

jsx = jsx.replace(/<span className="row-country">Indonesia<\/span>\s*<h3 className="row-city">Bandung<\/h3>\s*<span className="row-cond">Cloudy<\/span>/, 
  '<span className="row-country">Plan Enterprise</span>\n<h3 className="row-city">Tech Solutions</h3>\n<span className="row-cond">Hace 5 horas</span>');

jsx = jsx.replace(/<span className="row-country">Indonesia<\/span>\s*<h3 className="row-city">South Jakarta<\/h3>\s*<span className="row-cond">Sunny<\/span>/, 
  '<span className="row-country">Marketing</span>\n<h3 className="row-city">María López</h3>\n<span className="row-cond">Ayer</span>');

// Replace weather icons in the SVG symbol list, wait, it's easier to just swap the uses
// Let's replace the actual SVG use tags with lucide components inside the JSX
jsx = jsx.replace(/<svg><use href="#i-globe"><\/use><\/svg>/g, '<Users strokeWidth={2} />');
jsx = jsx.replace(/<svg className="loc-pin"><use href="#i-pin"><\/use><\/svg>/g, '<Target className="loc-pin" strokeWidth={2} />');

// Metric 1 (Wind -> Activity)
jsx = jsx.replace(/<svg className="m-icon"><use href="#i-wind"><\/use><\/svg>/g, '<Activity className="m-icon" strokeWidth={2} />');
// Metric 2 (Drop -> FileText)
jsx = jsx.replace(/<svg className="m-icon"><use href="#i-drop"><\/use><\/svg>/g, '<FileText className="m-icon" strokeWidth={2} />');
// Metric 3 (Gust -> DollarSign)
jsx = jsx.replace(/<svg className="m-icon"><use href="#i-gust"><\/use><\/svg>/g, '<DollarSign className="m-icon" strokeWidth={2} />');

// Forecast icons
jsx = jsx.replace(/<svg className="ft-icon"><use href="#i-cloud"><\/use><\/svg>/g, '<Target className="ft-icon" strokeWidth={2} />');
jsx = jsx.replace(/<svg className="ft-icon"><use href="#i-cloud2"><\/use><\/svg>/g, '<CheckCircle className="ft-icon" strokeWidth={2} />');
jsx = jsx.replace(/<svg className="ft-icon"><use href="#i-sun"><\/use><\/svg>/g, '<Activity className="ft-icon" strokeWidth={2} />');
jsx = jsx.replace(/<svg className="ft-icon"><use href="#i-hail"><\/use><\/svg>/g, '<Users className="ft-icon" strokeWidth={2} />');

// Row card icons
jsx = jsx.replace(/<svg className="row-icon"><use href="#i-cloud"><\/use><\/svg>/g, '<CheckCircle className="row-icon" color="#10b981" strokeWidth={2} />');
jsx = jsx.replace(/<svg className="row-icon"><use href="#i-cloud2"><\/use><\/svg>/g, '<Target className="row-icon" color="#3b82f6" strokeWidth={2} />');

// Convert HTML temps
jsx = jsx.replace(/<span className="ft-val">11°<\/span>/, '<span className="ft-val">12</span>');
jsx = jsx.replace(/<span className="ft-val">13°<\/span>/, '<span className="ft-val">45</span>');
jsx = jsx.replace(/<span className="ft-val">19°<\/span>/, '<span className="ft-val">84</span>');

writeFileSync('../src/pages/AuroraDashboard.tsx', jsx);
console.log('Fixed AuroraDashboard content');
