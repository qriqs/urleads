<div align="center">

<h1>🎯 UrLeads</h1>

<p><strong>Tus leads. Su historia. El próximo paso.</strong></p>

<p>
Un CRM privado para organizar contactos, registrar notas<br>
y mantener cada seguimiento a la vista.
</p>

<p>
  <img src="https://img.shields.io/badge/Java-21_LTS-4f46e5?style=flat-square&amp;logo=openjdk&amp;logoColor=white" alt="Java 21 LTS">
  <img src="https://img.shields.io/badge/Spring_Boot-3.3.5-4f46e5?style=flat-square&amp;logo=springboot&amp;logoColor=white" alt="Spring Boot 3.3.5">
  <img src="https://img.shields.io/badge/Maven_Wrapper-3.10.0-334155?style=flat-square&amp;logo=apachemaven&amp;logoColor=white" alt="Maven Wrapper con Maven 3.10.0">
</p>

<p>🔒 Privado &nbsp; · &nbsp; 📝 Notas internas &nbsp; · &nbsp; ⏰ Seguimientos</p>

<p><sub>🎓 Proyecto académico · Desarrollo de Sistemas Web II</sub></p>

</div>

---

> [!NOTE]
> 🚧 **En construcción.** El repositorio ya incluye un backend mínimo con
> `GET /api/health`. El frontend, PostgreSQL, login, CRUD y despliegue siguen
> planificados. La documentación describe el MVP, no una aplicación terminada.

## 🧭 El producto

UrLeads propone un espacio sencillo para gestionar clientes potenciales, saber
en qué etapa se encuentran y recordar cuándo volver a contactarlos. El MVP se
enfoca en **una cuenta preconfigurada**, un flujo privado y una interfaz
responsive.

| 📇 Organiza | 📝 Registra | ⏰ Da seguimiento |
|---|---|---|
| Crea, busca, filtra y edita leads. | Conserva notas internas por contacto. | Consulta fechas, etapas y pendientes. |

Las etapas previstas son `NUEVO`, `EN_SEGUIMIENTO` y `CERRADO`. **Historial y
notas** es una bitácora privada con apariencia de conversación: no envía
mensajes al cliente ni cambia su etapa o fecha de seguimiento.

Fuera del MVP: proyectos, colaboración multiusuario, registro público, IA,
archivos adjuntos, integraciones WhatsApp/email y mensajes automáticos.

## 🚧 Estado del proyecto

La base técnica existe; las funciones de producto se implementan en las
siguientes tareas del [backlog](docs/backlog.md).

| Área | Disponible hoy | Trabajo pendiente |
|---|---|---|
| Backend | Spring Boot, Java 21, Maven Wrapper y health público. | Login, CRUD, filtros, bitácora y dashboard. |
| Pruebas | Carga del contexto y contrato HTTP de health. | Pruebas de datos, seguridad y flujos de producto. |
| Interfaz | Prototipo HTML con datos ficticios. | Aplicación React responsive conectada a la API. |
| Datos | Modelo y estrategia documentados. | PostgreSQL, Flyway y entorno local. |
| Entrega | Guías técnicas y planificación del equipo. | CI, build integrado y despliegue autorizado. |

## 🎨 Diseño del MVP

El [prototipo de dashboard y bitácora](docs/dashboard-preview.html) es la
referencia visual del equipo. Descarga o abre ese archivo localmente en tu
navegador para explorar el diseño.

> [!IMPORTANT]
> El prototipo no es la aplicación. Sus datos son ficticios, sus cambios viven
> en memoria y desaparecen al recargar. No implementa persistencia ni mensajería.

## 🛠️ Stack

El stack distingue las herramientas del scaffold actual de las elecciones
acordadas para construir el MVP.

| Capa | Tecnologías | Estado |
|---|---|---|
| API | Java 21 · Spring Boot 3.3.5 · Spring MVC | Scaffold implementado |
| Build y tests | Maven Wrapper · JUnit · Spring Boot Test · MockMvc | Configurados |
| Frontend | React · TypeScript · Vite · React Router | Planificado |
| UI y datos remotos | Tailwind CSS · shadcn/ui · TanStack Query · `fetch` | Planificados |
| Persistencia | PostgreSQL · Spring Data JPA · Flyway | Planificada |
| Seguridad y validación | Spring Security · BCrypt · Bean Validation | Planificadas |
| Entorno y entrega | Docker Compose · GitHub Actions · Railway | Planificados |

La arquitectura prevista usa **una aplicación Spring Boot** para servir la API
y el frontend compilado, más **un servicio PostgreSQL**. Railway está sujeto a
aceptación del docente y aprobación explícita de costos.

## 💻 Desarrollo local

Estos pasos arrancan el backend mínimo. Todavía no necesitas PostgreSQL ni
dependencias frontend para ejecutarlo.

**Requisitos:** JDK 21 instalado y conexión a internet en la primera ejecución.
Maven Wrapper descarga Maven y las dependencias que falten; **no instala Java**
ni requiere Maven global.

### 1. Prepara la terminal

Desde la raíz del repositorio, entra una sola vez al backend:

```bash
cd backend
```

Todos los comandos siguientes se ejecutan desde `backend/`. Si ya estás allí,
no repitas `cd backend`.

<details>
<summary><strong>☕ Seleccionar Java 21 si tu terminal usa otra versión</strong></summary>

Configura `JAVA_HOME` y `PATH` solo en la terminal actual. No necesitas `sudo`,
enlaces simbólicos ni cambios en `.zshrc` o `.bashrc`.

**macOS con Apple Silicon y OpenJDK 21 instalado mediante Homebrew:**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

**Linux u otra ubicación del JDK:** sustituye la ruta por tu instalación real.

```bash
export JAVA_HOME=/ruta/a/tu/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

**Windows, Command Prompt:**

```bat
set "JAVA_HOME=C:\ruta\a\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
```

**Windows, PowerShell:**

```powershell
$env:JAVA_HOME = "C:\ruta\a\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

</details>

Comprueba que Java y Maven Wrapper reconozcan la versión 21:

```bash
java -version
./mvnw -v
```

En Windows, sustituye `./mvnw` por `mvnw.cmd` en Command Prompt o
`.\mvnw.cmd` en PowerShell.

### 2. Compila y verifica

Ejecuta las pruebas y genera el JAR del backend:

```bash
./mvnw clean verify
```

El artefacto se genera en `target/urleads-backend-0.0.1-SNAPSHOT.jar`.

### 3. Arranca el servidor

Para trabajar en desarrollo, inicia Spring Boot desde Maven:

```bash
./mvnw spring-boot:run
```

El puerto predeterminado es **8080**. Elige una sola forma de arranque y detén
el servidor antes de cambiar a otra.

<details>
<summary><strong>⚙️ Ejecutar el JAR o usar otro puerto</strong></summary>

Después de compilar, puedes ejecutar directamente el artefacto:

```bash
java -jar target/urleads-backend-0.0.1-SNAPSHOT.jar
```

Para cambiar el puerto, configura `PORT`. En Bash o Zsh:

```bash
PORT=8085 java -jar target/urleads-backend-0.0.1-SNAPSHOT.jar
```

También puedes usar `PORT=8085 ./mvnw spring-boot:run`. En Windows, configura
primero `set PORT=8085` (Command Prompt) o `$env:PORT = "8085"` (PowerShell)
y luego ejecuta Java o Maven Wrapper.

</details>

### 4. Comprueba health

Desde otra terminal, consulta el endpoint público:

```bash
curl -i http://localhost:8080/api/health
```

La respuesta esperada es **HTTP 200** con este cuerpo JSON:

```json
{"status":"UP"}
```

Si configuraste `PORT=8085`, consulta `http://localhost:8085/api/health`.
Health confirma que el servidor HTTP responde, **no** que PostgreSQL esté
conectado o que el producto completo esté disponible.

Para detener el servidor, presiona **Ctrl + C** en la terminal de arranque.

## 🗂️ Estructura

Cada carpeta tiene un límite claro para no mezclar el backend, la
infraestructura y la documentación del equipo.

```text
urleads/
├── backend/                 # Scaffold Spring Boot
│   ├── .mvn/                # Configuración de Maven Wrapper
│   ├── src/main/            # Aplicación y endpoint de health
│   ├── src/test/            # Pruebas del contexto y health
│   ├── mvnw                 # Wrapper para macOS y Linux
│   ├── mvnw.cmd             # Wrapper para Windows
│   └── pom.xml              # Dependencias y build
├── docs/                    # Producto, API, diseño y entrega académica
├── infra/                   # Guía; PostgreSQL local se añade en UL-03
├── AGENTS.md                # Convenciones y límites para agentes
└── README.md
```

## 📚 Documentación

Consulta el documento correspondiente antes de implementar o cambiar un
contrato. Estas guías mantienen las decisiones del producto y del equipo.

| Tema | Guías |
|---|---|
| Producto | [Alcance](docs/producto.md) · [Requisitos](docs/requisitos.md) |
| Backend | [Arquitectura y seguridad](docs/arquitectura.md) · [Contrato API](docs/api.md) |
| Interfaz | [Diseño UI](docs/diseno-ui.md) · [Prototipo HTML](docs/dashboard-preview.html) |
| Equipo | [Roles y calendario](docs/plan-equipo.md) · [Backlog](docs/backlog.md) |
| Calidad | [Plan de pruebas](docs/pruebas.md) |
| Entrega | [Despliegue](docs/despliegue.md) · [Entrega académica](docs/entrega-academica.md) |
| Entorno local | [Guía de infraestructura](infra/README.md) |

## 🤝 Trabajo en equipo

El equipo separa la planificación de tareas de la entrega de código:

- **Linear:** tareas, responsables, dependencias y estimaciones en
  [UrLeads en DSW2](https://linear.app/enmanuelprojects/project/urleads-b3c2085df458).
- **GitHub:** ramas cortas por tarea, pull requests pequeños, revisión y checks
  configurados antes de integrar. `main` se mantiene estable.
- **Privacidad:** no versionar contraseñas, tokens, cookies, archivos `.env` ni
  datos personales reales. Los ejemplos usan datos ficticios.
- **Archivos locales:** builds y directorios de datos reservados se excluyen
  mediante `.gitignore`. Las plantillas `.env.example` deben estar libres de
  credenciales.

## 🗓️ Próximos pasos

El equipo avanza desde la base técnica hacia el recorrido funcional del MVP.
Las dependencias y los responsables están en Linear.

| Hito previsto | Fecha objetivo |
|---|---|
| Versión candidata del MVP | 15 de octubre de 2026 |
| MVP validado | 16 de octubre de 2026 |
| Margen de correcciones | 17 y 18 de octubre de 2026 |
| Exposición, por confirmar con el docente | Entre el 19 y el 23 de octubre de 2026 |

Estas fechas son objetivos de planificación, no evidencia de una entrega
completada.

---

<p align="center"><sub>✨ UrLeads · Un contacto, una historia, un próximo paso.</sub></p>
