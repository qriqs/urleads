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
