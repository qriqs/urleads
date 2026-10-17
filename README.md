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
> 🚀 **Plan de expansión Backend V2 en ejecución:** El equipo trabaja en una
> arquitectura desacoplada de tres microservicios (CRM, Comercial y Agenda).
> Consulta [docs/backend-v2/](docs/backend-v2/) y
> [docs/plan-tareas-backend.md](docs/plan-tareas-backend.md) para los contratos,
> guías por responsable y el flujo de ramas. La información previa del MVP
> monolítico se mantiene a continuación como línea base histórica.
>
> 🚧 **Estado MVP:** El repositorio cuenta con el backend en Spring Boot,
> PostgreSQL 16 con Docker Compose y migración inicial Flyway para el esquema de datos.
> La documentación describe el alcance del sistema.

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
| Backend | Spring Boot, Java 21, Maven Wrapper, JDBC y health público. | Login, CRUD, filtros, bitácora y dashboard. |
| Pruebas | Contexto básico, contrato de health, guard de pruebas y migración Flyway (`db-integration`). | Pruebas de repositorios (UL-07), seguridad y flujos de producto. |
| Interfaz | Prototipo HTML con datos ficticios. | Aplicación React responsive conectada a la API. |
| Datos | PostgreSQL 16 con Docker Compose y migración inicial Flyway (V1). | Entidades Spring Data JPA y repositorios CRUD (UL-07). |
| Entrega | Guías técnicas y planificación del equipo; CI en GitHub Actions para backend y frontend. | Build integrado y despliegue autorizado. |

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
| Persistencia | PostgreSQL · Spring Data JPA (planificado) · Spring JDBC · Flyway | Esquema V1 y Flyway implementados; JPA planificado para UL-07 |
| Seguridad y validación | Spring Security · BCrypt · Bean Validation | Planificadas |
| Entorno y entrega | Docker Compose · GitHub Actions · Railway | PostgreSQL local configurado; CI para backend y frontend configurado; nube planificada |

La arquitectura prevista usa **una aplicación Spring Boot** para servir la API
y el frontend compilado, más **un servicio PostgreSQL**. Railway está sujeto a
aceptación del docente y aprobación explícita de costos.

## Checks de integración continua

GitHub Actions ejecuta los checks en cada pull request hacia `main`, cada push
a `main` y cuando se inicia el workflow manualmente. El backend ejecuta
`./mvnw --batch-mode --no-transfer-progress clean verify -Pdb-integration` con
Java 21 y un PostgreSQL 16 efímero aislado. El frontend instala las dependencias
con `bun install --frozen-lockfile`, ejecuta `bun run lint` (TypeScript sin
emisión) y genera el build con `bun run build`, usando Node.js 22 y Bun 1.3.10.
No hay un script de tests frontend configurado actualmente. Para reproducir los
checks localmente, sigue las instrucciones de la
[guía del frontend](frontend/README.md) y las
[pruebas del backend](docs/pruebas.md).

## 💻 Desarrollo local

Estos pasos describen cómo compilar el frontend y backend en un único JAR,
ejecutar las pruebas y arrancar el servidor integrado.

**Requisitos:** JDK 21 instalado, Bun 1.3.10 y Docker/Compose para la base
local, además de conexión a internet en la primera ejecución. Maven Wrapper
descarga Maven y las dependencias que falten; **no instala Java** ni requiere
Maven global.

Para compilar y ejecutar las pruebas básicas del scaffold no requieres Docker;
para iniciar el servidor con persistencia o ejecutar las pruebas de integración
necesitas PostgreSQL activo (ver [infra/README.md](infra/README.md)).

### 1. Inicia PostgreSQL local (para desarrollo)

Si vas a ejecutar el servidor de desarrollo con persistencia, prepara el
contenedor local desde la raíz del repositorio:

```bash
# Copia la plantilla si aún no existe (no sobreescribe una existente)
[ ! -f infra/.env ] && cp infra/.env.example infra/.env

# Edita infra/.env con tu editor para definir POSTGRES_PASSWORD
nano infra/.env

# Levanta el servicio desde infra/
cd infra && docker compose up -d --wait && cd ..
```

Consulta [infra/README.md](infra/README.md) para más opciones de gestión,
reanudación de contenedores y alternativas sin Docker.

### 2. Instala las dependencias del frontend y selecciona Java 21

Desde `frontend/`, instala las dependencias exactas del lockfile. Luego
selecciona Java 21 en la terminal que usarás para compilar y arrancar:

```bash
cd frontend
bun install --frozen-lockfile
cd ..
```

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
./backend/mvnw -f backend/pom.xml -v
```

En Windows, desde la raíz, usa `backend\mvnw.cmd -f backend\pom.xml -v` en
Command Prompt o `./backend/mvnw.cmd -f backend/pom.xml -v` en PowerShell.

### 3. Compila y verifica el artefacto integrado

Desde la raíz del repositorio, ejecuta el script reproducible. El script verifica
tipos, compila Vite y empaqueta los assets generados en el JAR Spring Boot:

```bash
./scripts/build.sh
```

El artefacto se genera en `backend/target/urleads-backend-0.0.1-SNAPSHOT.jar`.
Los archivos generados en `frontend/dist/` y `backend/target/` no se versionan.

> **Pruebas de integración con base de datos:** El backend cuenta con una suite
> de pruebas de migración e integridad (`DatabaseMigrationIT`) bajo el perfil
> `db-integration`. Estas pruebas **nunca** se ejecutan contra la base de datos de
> desarrollo, sino contra una instancia de pruebas aislada con nombre terminado
> en `_test` y variables `TEST_DB_*` (ver guía detallada en
> [infra/README.md](infra/README.md)).

### 4. Arranca el servidor integrado

El mismo JAR sirve la interfaz y la API; no requiere un servidor Node en
ejecución. Para iniciar el servidor con conexión a tu base de datos de desarrollo:

1. Exporta las variables de conexión desde la raíz del repositorio, en la
   terminal donde ejecutarás el backend:

   ```bash
   export DB_URL="jdbc:postgresql://localhost:5432/urleads"
   export DB_USERNAME="urleads_app"
   # Lee la contraseña de forma segura para no dejarla en el historial:
   printf 'Introduce DB_PASSWORD: '
   read -r -s DB_PASSWORD
   printf '\n'
   export DB_PASSWORD
   ```

2. Inicia el JAR generado desde la raíz del repositorio:

```bash
java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar
```

El puerto predeterminado es **8080**. Elige una sola forma de arranque y detén
el servidor antes de cambiar a otra. Flyway aplicará automáticamente las
migraciones pendientes al iniciar.

<details>
<summary><strong>⚙️ Ejecutar el JAR o usar otro puerto</strong></summary>

Desde la raíz del repositorio, puedes ejecutar directamente el artefacto:

```bash
java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar
```

Para cambiar el puerto, configura `PORT`. En Bash o Zsh:

```bash
PORT=8085 java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar
```

También puedes usar `PORT=8085 ./backend/mvnw -f backend/pom.xml spring-boot:run`.
En Windows, configura primero `PORT` (Command Prompt) o `$env:PORT = "8085"`
(PowerShell) y luego ejecuta Java o el wrapper `backend\mvnw.cmd -f
backend\pom.xml spring-boot:run` desde la raíz.

</details>

### 5. Comprueba health

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

La raíz (`/`) y las rutas frontend sin extensión sirven la aplicación React.
Las rutas `/api/**` conservan el comportamiento de seguridad de la API, y los
recursos estáticos inexistentes responden 404 en vez de recibir el HTML de SPA.
La UI y API comparten origen, por lo que producción no requiere CORS. Mantén
CSRF habilitado; en HTTPS de producción configura `COOKIE_SECURE=true`. El valor
predeterminado `false` permite desarrollo HTTP local.

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
├── infra/                   # Docker Compose y variables de entorno para PostgreSQL local
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
