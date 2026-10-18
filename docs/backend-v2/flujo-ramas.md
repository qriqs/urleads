# Flujo de ramas y ciclo de integración Backend V2

Este documento describe la estrategia de ramas en Git, las reglas de publicación
y el flujo de trabajo para el desarrollo colaborativo y asíncrono de los tres
microservicios de UrLeads Backend V2.

---

## 1. Ramas del proyecto

El equipo trabaja sobre un conjunto cerrado de ramas publicadas en GitHub:

| Rama | Propósito | Responsable principal |
|---|---|---|
| `integration/backend-v2` | Rama base compartida y destino de integración de los tres servicios. | Cristopher |
| `feat/crm` | Desarrollo del servicio CRM (`backend/`). | Cristopher |
| `feat/comercial` | Desarrollo del servicio Comercial (`services/comercial/`). | Sebas |
| `feat/agenda-notificaciones` | Desarrollo del servicio Agenda (`services/agenda/`). | Alex |

> **Regla de oro:** Las ramas de servicio comparten la misma punta base que
> `integration/backend-v2`. Ningún integrante hace push directo a `main` ni
> sobreescribe ramas ajenas.

---

## 2. Configuración inicial para cada desarrollador

Cada integrante debe clonar el repositorio o actualizar sus referencias remotas
y activar el seguimiento de su rama de trabajo:

```bash
# 1. Obtener todas las ramas y commits del repositorio remoto
git fetch origin

# 2. Cambiar a la rama de trabajo propia con seguimiento remoto
# (Ejemplo para Sebas en Comercial)
git switch --track origin/feat/comercial

# (Ejemplo para Alex en Agenda)
git switch --track origin/feat/agenda-notificaciones

# (Ejemplo para Cris en CRM)
git switch --track origin/feat/crm
```

Si la rama local ya existía previamente, utiliza `git switch <nombre-rama>` sin
hacer rebase ni forzar cambios.

### Advertencia sobre ramas simultáneas en una misma máquina

Si trabajas con varias terminales o procesos al mismo tiempo en un único
checkout, cambiar de rama en una terminal afectará a todas. Para trabajar en
paralelo en varias ramas se recomienda usar `git worktree` o clones separados:

```bash
# Crear un worktree aislado para otra rama (opcional):
git worktree add ../urleads-comercial feat/comercial
```

---

## 3. Flujo diario de trabajo por servicio

1. **Desarrollo y commits frecuentes:**
   - Trabaja exclusivamente dentro de tu directorio asignado (`backend/`,
     `services/comercial/` o `services/agenda/`).
   - Realiza commits pequeños y semánticos describiendo la tarea (`feat:`, `test:`,
     `docs:`, `fix:`).

2. **Publicación en tu rama:**
   ```bash
   git push origin feat/<tu-servicio>
   ```
   - No utilices `--force` ni modifiques el historial público compartido.

3. **Incorporación de cambios de integración:**
   Cuando se aprueben y publiquen cambios en `integration/backend-v2`, integra
   las novedades en tu rama mediante un commit de merge estándar:
   ```bash
   git fetch origin
   git merge origin/integration/backend-v2
   git push origin feat/<tu-servicio>
   ```
   > **Importante:** No utilices `squash` repetidamente en una rama de servicio de
   > larga duración, ya que dificulta las integraciones posteriores.

---

## 4. Proceso de Pull Request hacia `integration/backend-v2`

1. **Apertura del Pull Request:**
   - Una vez que tu servicio tiene un bloque funcional con tests automatizados
     en verde, abre un Pull Request desde tu rama (`feat/<servicio>`) hacia
     `integration/backend-v2`.
   - Incluye en la descripción el código de tarea Linear (ej. `[V2 S03]`) y un
     resumen de las comprobaciones ejecutadas.
   - *Nota:* No se pueden abrir PRs con cero cambios; ábrelo únicamente cuando tu
     rama haya avanzado con commits propios de implementación.

2. **Revisión e integración:**
   - Cristopher revisa que no haya colisiones de archivos fuera del directorio
     del servicio y ejecuta los checks de integración.
   - El PR se fusiona mediante un merge commit que preserve el historial.

3. **Integración final a `main`:**
   - La rama `integration/backend-v2` se fusionará a `main` únicamente tras la
     finalización y validación de las tareas de integración técnica (C14, C15,
     S15, A15). No se realizan fusiones directas individuales de ramas de
     servicio hacia `main`.
