import { readFileSync, writeFileSync } from 'fs';

let jsx = readFileSync('../src/pages/AuroraDashboard.tsx', 'utf-8');

// Fix unclosed tags
jsx = jsx.replace(/<img([^>]*)>/g, (match, attrs) => {
    if (attrs.endsWith('/')) return match;
    return `<img${attrs} />`;
});
jsx = jsx.replace(/<br([^>]*)>/g, (match, attrs) => {
    if (attrs.endsWith('/')) return match;
    return `<br${attrs} />`;
});
jsx = jsx.replace(/<path([^>]*)>/g, (match, attrs) => {
    if (attrs.endsWith('/')) return match;
    return `<path${attrs} />`;
});
jsx = jsx.replace(/<circle([^>]*)>/g, (match, attrs) => {
    if (attrs.endsWith('/')) return match;
    return `<circle${attrs} />`;
});
jsx = jsx.replace(/<rect([^>]*)>/g, (match, attrs) => {
    if (attrs.endsWith('/')) return match;
    return `<rect${attrs} />`;
});
jsx = jsx.replace(/<stop([^>]*)>/g, (match, attrs) => {
    if (attrs.endsWith('/')) return match;
    return `<stop${attrs} />`;
});

// Also fix `onerror` to `onError` just in case
jsx = jsx.replace(/onerror=/g, 'onError=');
jsx = jsx.replace(/onload=/g, 'onLoad=');

writeFileSync('../src/pages/AuroraDashboard.tsx', jsx);
console.log('Fixed JSX tags');
