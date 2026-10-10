import { readFileSync, writeFileSync } from 'fs';

let jsx = readFileSync('../src/pages/AuroraDashboard.tsx', 'utf-8');

jsx = jsx.replace(/<span className="ft-val">12<\/span>/, '<span className="ft-val">$12K</span>');
jsx = jsx.replace(/<span className="ft-val">45<\/span>/, '<span className="ft-val">$45K</span>');
jsx = jsx.replace(/<span className="ft-val">Nuevo<\/span>/, '<span className="ft-val">$58K</span>');
jsx = jsx.replace(/<span className="ft-val">Activo<\/span>/, '<span className="ft-val">$80K</span>');
jsx = jsx.replace(/<span className="ft-val">84<\/span>/, '<span className="ft-val">$84K</span>');
jsx = jsx.replace(/<span className="ft-val">Ganado<\/span>/, '<span className="ft-val">$120K</span>');

jsx = jsx.replace(/<div className="day">Lun<\/div>/, '<div className="day">Ene</div>');
jsx = jsx.replace(/<div className="day">Mar<\/div>/, '<div className="day">Feb</div>');
jsx = jsx.replace(/<div className="day">Mié<\/div>/, '<div className="day">Mar</div>');
jsx = jsx.replace(/<div className="day on">Jue<\/div>/, '<div className="day on">Abr</div>');
jsx = jsx.replace(/<div className="day">Vie<\/div>/, '<div className="day">May</div>');
jsx = jsx.replace(/<div className="day">Sáb<\/div>/, '<div className="day">Jun</div>');

writeFileSync('../src/pages/AuroraDashboard.tsx', jsx);
