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

El script `lint` ejecuta `tsc --noEmit` para revisar tipos. El proyecto no
configura actualmente un script de tests frontend.

## Tecnologías principales
- React 19 & Vite
- Tailwind CSS
- React Router DOM
- TanStack Query
- Lucide React (Iconos)
- Framer Motion (Animaciones)

## Build integrado de producción

En un checkout nuevo, instala primero las dependencias con
`bun install --frozen-lockfile` desde `frontend/`. Luego, desde la raíz del
repositorio, ejecuta `./scripts/build.sh`. El script ejecuta
el type-check y build de Vite y luego `clean verify` con Maven Wrapper. Maven
incluye el contenido recién generado de `frontend/dist/` bajo `static/` dentro
del JAR ejecutable. La aplicación Spring Boot sirve la interfaz y `/api` desde
el mismo origen; no se necesita un servidor Bun/Node para ejecutar el JAR.
Los directorios `frontend/dist/` y `backend/target/` son artefactos generados y
no se versionan.
