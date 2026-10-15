# Frontend

El frontend usa Bun y el lockfile versionado `bun.lock` para instalar
dependencias de forma reproducible. No requiere una clave de Gemini.

## Requisitos

Instala las siguientes herramientas para ejecutar y verificar el frontend:

- Node.js 22.12 o posterior dentro de la versión 22.x.
- Bun 1.3.10, la versión usada por CI.

## Instalar y ejecutar

Desde `frontend/`, instala las dependencias y arranca Vite:

```bash
bun install --frozen-lockfile
bun run dev
```

## Checks locales

Ejecuta los mismos checks frontend que corre GitHub Actions:

```bash
bun install --frozen-lockfile
bun run lint
bun run build
```

El script `lint` ejecuta `tsc --noEmit` para revisar tipos. El proyecto no
configura actualmente un script de tests frontend.

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
