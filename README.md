 <div align="center">

# 🎯 UrLeads

**Tus leads. Su historia. El próximo paso.**

Un CRM privado para organizar contactos, registrar notas  
y mantener cada seguimiento a la vista.

![Java 21 LTS](https://img.shields.io/badge/Java-21_LTS-4f46e5?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot 3.3.5](https://img.shields.io/badge/Spring_Boot-3.3.5-4f46e5?style=flat-square&logo=springboot&logoColor=white)
![Maven Wrapper](https://img.shields.io/badge/Maven_Wrapper-3.10.0-334155?style=flat-square&logo=apachemaven&logoColor=white)

🔒 Privado · 📝 Notas internas · ⏰ Seguimientos

<sub>🎓 Proyecto académico · Desarrollo de Sistemas Web II</sub>

</div>

---

> [!NOTE]
> 🚧 **En construcción.** El repositorio cuenta con el backend mínimo en Spring Boot, PostgreSQL 16 con Docker Compose, migración inicial Flyway, autenticación mediante sesión y protección CSRF.
> El frontend, CRUD de leads y despliegue en la nube siguen en desarrollo.
> La documentación describe el MVP, no una aplicación terminada.

## 🧭 El producto

UrLeads propone un espacio sencillo para gestionar clientes potenciales, saber en qué etapa se encuentran y recordar cuándo volver a contactarlos. El MVP se enfoca en **una cuenta preconfigurada**, un flujo privado y una interfaz responsive.

| 📇 Organiza | 📝 Registra | ⏰ Da seguimiento |
|---|---|---|
| Crea, busca, filtra y edita leads. | Conserva notas internas por contacto. | Consulta fechas, etapas y pendientes. |

Las etapas previstas son `NUEVO`, `EN_SEGUIMIENTO` y `CERRADO`. **Historial y notas** es una bitácora privada con apariencia de conversación: no envía mensajes al cliente ni cambia su etapa o fecha de seguimiento.

Fuera del MVP: proyectos, colaboración multiusuario, registro público, IA, archivos adjuntos, integraciones WhatsApp/email y mensajes automáticos.

## 🚧 Estado del proyecto

La base técnica existe; las funciones de producto se implementan en las siguientes tareas del [backlog](docs/backlog.md).

| Área | Disponible hoy | Trabajo pendiente |
|---|---|---|
| Backend | Spring Boot 3.3, Java 21, Maven Wrapper, Flyway V1, Health, Login con sesión/CSRF (PR #14) y notas privadas. | CRUD de leads, filtros y métricas del dashboard. |
| Pruebas | 12 tests estándar + 54 tests de integración en PostgreSQL aislado (`db-integration`) cubriendo Flyway y Auth (PR #14). | Pruebas de repositorios/leads (UL-07) y dashboard. |
| Interfaz | Prototipo HTML con datos ficticios. | Aplicación React responsive conectada a la API. |
| Datos | PostgreSQL 16 con Docker Compose y migración inicial Flyway (V1). | Entidades Spring Data JPA y repositorios CRUD (UL-07). |
| Entrega | Guías técnicas y planificación del equipo; CI en GitHub Actions para backend y frontend. | Build integrado y despliegue autorizado. |

## 🎨 Diseño del MVP

El [prototipo de dashboard y bitácora](docs/dashboard-preview.html) es la referencia visual del equipo. Descarga o abre ese archivo localmente en tu navegador para explorar el diseño.

> [!IMPORTANT]
> El prototipo no es la aplicación. Sus datos son ficticios, sus cambios viven en memoria y desaparecen al recargar. No implementa persistencia ni mensajería.

## 🛠️ Stack

El stack distingue las herramientas del scaffold actual de las elecciones acordadas para construir el MVP.

| Capa | Tecnologías | Estado |
|---|---|---|
| API | Java 21 · Spring Boot 3.3.5 · Spring MVC | Scaffold implementado |
| Build y tests | Maven Wrapper · JUnit · Spring Boot Test · MockMvc | Configurados |
| Frontend | React · TypeScript · Vite · React Router | Planificado |
| UI y datos remotos | Tailwind CSS · shadcn/ui · TanStack Query · `fetch` | Planificados |
| Persistencia | PostgreSQL · Spring Data JPA (planificado) · Spring JDBC · Flyway | Esquema V1 y Flyway implementados; JPA planificado para UL-07 |
| Seguridad y validación | Spring Security · BCrypt · Bean Validation | Autenticación, sesión y CSRF implementados; validación pendiente según el alcance funcional |
| Entorno y entrega | Docker Compose · GitHub Actions · Railway | PostgreSQL local configurado; CI para backend y frontend configurado; nube planificada |

La arquitectura prevista usa **una aplicación Spring Boot** para servir la API y el frontend compilado, más **un servicio PostgreSQL**. Railway está sujeto a aceptación del docente y aprobación explícita de costos.

## Checks de integración continua

GitHub Actions ejecuta los checks en cada pull request hacia `main`, cada push a `main` y cuando se inicia el workflow manualmente. El backend ejecuta `./mvnw --batch-mode --no-transfer-progress clean verify -Pdb-integration` con Java 21 y un PostgreSQL 16 efímero aislado. El frontend instala las dependencias con `bun install --frozen-lockfile`, ejecuta `bun run lint` (TypeScript sin emisión) y genera el build con `bun run build`, usando Node.js 22 y Bun 1.3.10.

No hay un script de tests frontend configurado actualmente. Para reproducir los checks localmente, sigue las instrucciones de la [guía del frontend](frontend/README.md) y las [pruebas del backend](docs/pruebas.md).

## 💻 Desarrollo local

Estos pasos describen cómo compilar el backend, ejecutar las pruebas y arrancar el servidor en desarrollo.

**Requisitos:** JDK 21 instalado, Docker y Docker Compose para la base de datos local, y conexión a internet en la primera ejecución. Maven Wrapper descarga Maven y las dependencias que falten; **no instala Java** ni requiere Maven global.

Para compilar y ejecutar las pruebas básicas del scaffold no requieres Docker; para iniciar el servidor con persistencia o ejecutar las pruebas de integración necesitas PostgreSQL activo (ver [infra/README.md](infra/README.md)).

### 1. Inicia PostgreSQL local (para desarrollo)

Si vas a ejecutar el servidor de desarrollo con persistencia, prepara el contenedor local desde la raíz del repositorio:

```bash
# Copia la plantilla si aún no existe (no sobreescribe una existente)
[ ! -f infra/.env ] && cp infra/.env.example infra/.env

# Edita infra/.env con tu editor para definir POSTGRES_PASSWORD
nano infra/.env

# Levanta el servicio desde infra/
cd infra && docker compose up -d --wait && cd ..
```

Consulta [infra/README.md](infra/README.md) para más opciones de gestión, reanudación de contenedores y alternativas sin Docker.

### 2. Prepara la terminal del backend

Sitúate en el directorio `backend/`:

```bash
cd backend
```

Todos los comandos siguientes se ejecutan desde `backend/`.

<details>
<summary><strong>☕ Seleccionar Java 21 si tu terminal usa otra versión</strong></summary>

Configura `JAVA_HOME` y `PATH` solo en la terminal actual. No necesitas `sudo`, enlaces simbólicos ni cambios en `.zshrc` o `.bashrc`.

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

En Windows, sustituye `./mvnw` por `mvnw.cmd` en Command Prompt o `.\mvnw.cmd` en PowerShell.

### 3. Compila y verifica el scaffold

Ejecuta las pruebas unitarias del scaffold y genera el JAR:

```bash
./mvnw clean verify
```

El artefacto se genera en `target/urleads-backend-0.0.1-SNAPSHOT.jar`.

> **Pruebas de integración con base de datos:** El backend cuenta con una suite de pruebas de migración e integridad (`DatabaseMigrationIT`) bajo el perfil `db-integration`. Estas pruebas **nunca** se ejecutan contra la base de datos de desarrollo, sino contra una instancia de pruebas aislada con nombre terminado en `_test` y variables `TEST_DB_*` (ver guía detallada en [infra/README.md](infra/README.md)).

### 4. Arranca el servidor

Para iniciar el servidor con conexión a tu base de datos de desarrollo:

1. Exporta las variables de conexión en la terminal donde ejecutarás el backend:

   ```bash
   export DB_URL="jdbc:postgresql://localhost:5432/urleads"
   export DB_USERNAME="urleads_app"
   export COOKIE_SECURE="false"

   # Lee la contraseña de forma segura para no dejarla en el historial:
   printf 'Introduce DB_PASSWORD: '
   read -r -s DB_PASSWORD
   printf '\n'
   export DB_PASSWORD
   ```

2. Inicia Spring Boot desde Maven:

   ```bash
   ./mvnw spring-boot:run
   ```

El puerto predeterminado es **8080**. Elige una sola forma de arranque y detén el servidor antes de cambiar a otra. Flyway aplicará automáticamente las migraciones pendientes al iniciar.

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

También puedes usar `PORT=8085 ./mvnw spring-boot:run`. En Windows, configura primero `set PORT=8085` (Command Prompt) o `$env:PORT = "8085"` (PowerShell) y luego ejecuta Java o Maven Wrapper.

</details>

### 5. Comprueba health y login

Desde otra terminal, confirma que el servicio responda:

```bash
curl -i http://localhost:8080/api/health
```

La respuesta esperada es **HTTP 200** con este cuerpo JSON:

```json
{"status":"UP"}
```

Si configuraste `PORT=8085`, consulta `http://localhost:8085/api/health`.

Health confirma que el servidor HTTP responde, **no** que PostgreSQL esté conectado o que el producto completo esté disponible.

Para verificar el endpoint de autenticación con la cuenta aprovisionada, primero solicita la cookie y el token CSRF:

```bash
curl -i -c cookies.txt http://localhost:8080/api/auth/csrf
```

Copia el token CSRF devuelto por el endpoint y úsalo en la petición de inicio de sesión:

```bash
curl -i -b cookies.txt -c cookies.txt \
  -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: <TOKEN_CSRF_OBTENIDO>" \
  -d '{"username": "tu_usuario", "password": "tu_password"}'
```

Sustituye `<TOKEN_CSRF_OBTENIDO>` por el token real y utiliza las credenciales de la cuenta aprovisionada. La respuesta debe coincidir con el contrato de autenticación documentado en [docs/arquitectura.md](docs/arquitectura.md).

No subas `cookies.txt` al repositorio: contiene información de sesión de la prueba local.

Para detener el servidor, presiona **Ctrl + C** en la terminal de arranque.

## 🗂️ Estructura

Cada carpeta tiene un límite claro para no mezclar el backend, la infraestructura y la documentación del equipo.

```text
urleads/
├── backend/                 # Backend Spring Boot
│   ├── .mvn/                # Configuración de Maven Wrapper
│   ├── src/main/            # Aplicación y endpoints
│   ├── src/test/            # Pruebas unitarias e integración
│   ├── mvnw                 # Wrapper para macOS y Linux
│   ├── mvnw.cmd             # Wrapper para Windows
│   └── pom.xml              # Dependencias y build
├── frontend/                # Aplicación React planificada
├── docs/                    # Producto, API, diseño y entrega académica
├── infra/                   # Docker Compose y variables de entorno para PostgreSQL local
├── AGENTS.md                # Convenciones y límites para agentes
└── README.md
```

## 📚 Documentación

Consulta el documento correspondiente antes de implementar o cambiar un contrato. Estas guías mantienen las decisiones del producto y del equipo.

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

- **Linear:** tareas, responsables, dependencias y estimaciones en [UrLeads en DSW2](https://linear.app/enmanuelprojects/project/urleads-b3c2085df458).
- **GitHub:** ramas cortas por tarea, pull requests pequeños, revisión y checks configurados antes de integrar. `main` se mantiene estable.
- **Privacidad:** no versionar contraseñas, tokens, cookies, archivos `.env` ni datos personales reales. Los ejemplos usan datos ficticios.
- **Archivos locales:** builds y directorios de datos reservados se excluyen mediante `.gitignore`. Las plantillas `.env.example` deben estar libres de credenciales.

## 🗓️ Próximos pasos

El equipo avanza desde la base técnica hacia el recorrido funcional del MVP. Las dependencias y los responsables están en Linear.

| Hito previsto | Fecha objetivo |
|---|---|
| Versión candidata del MVP | 15 de octubre de 2026 |
| MVP validado | 16 de octubre de 2026 |
| Margen de correcciones | 17 y 18 de octubre de 2026 |
| Exposición, por confirmar con el docente | Entre el 19 y el 23 de octubre de 2026 |

Estas fechas son objetivos de planificación, no evidencia de una entrega completada.

---

<p align="center"><sub>✨ UrLeads · Un contacto, una historia, un próximo paso.</sub></p>