# UrLeads - Frontend

Sistema CRM para gestión de Leads, construido con React 19, Vite, Tailwind CSS y Bun. El frontend usa Bun y el lockfile versionado `bun.lock` para instalar dependencias de forma reproducible.

## Requisitos

- Node.js 22.12 o posterior dentro de la versión 22.x.
- Bun (v1.0+, 1.3.10 usado por CI).

## Instalar y ejecutar

Desde `frontend/`, instala las dependencias y arranca Vite:

```bash
bun install --frozen-lockfile
bun run dev
```

## Scripts disponibles

```bash
bun run dev      # Servidor de desarrollo
bun run build    # Compilación para producción
bun run lint     # Verificación de tipos (tsc --noEmit)
```

## Tecnologías principales
- React 19 & Vite
- Tailwind CSS
- React Router DOM
- TanStack Query
- Lucide React (Iconos)
- Framer Motion (Animaciones)
