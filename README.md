# UrLeads

UrLeads es un CRM web privado para organizar clientes potenciales, registrar
notas, controlar su etapa y recordar próximos seguimientos. El MVP se enfoca en
un usuario y un flujo de trabajo sencillo, con una interfaz responsive y una
API REST respaldada por PostgreSQL.

Actualmente, este repositorio contiene la planificación del proyecto y un
scaffold inicial mínimo del backend (Spring Boot con endpoint público de salud).
La base de datos (PostgreSQL), la autenticación, las operaciones CRUD de leads,
el frontend y el despliegue en producción permanecen como trabajo planificado
para las siguientes etapas.

## Alcance del MVP

- Inicio y cierre de sesión para una cuenta preconfigurada.
- Alta, consulta, edición y eliminación de leads.
- Búsqueda y filtros sencillos.
- Bitácora privada de actualizaciones por lead, con apariencia de conversación.
- Etapas y fecha del próximo seguimiento.
- Dashboard con resumen y seguimientos pendientes.
- API REST Java/Spring, PostgreSQL y despliegue en Railway.

El módulo de proyectos, los usuarios múltiples, los mensajes automáticos y las
funciones de IA quedan fuera de esta primera versión.

## Documentación

El preview HTML es una referencia visual local con datos ficticios. No es parte
de la aplicación ni contiene backend o persistencia.

- [Vista previa del dashboard y bitácora](docs/dashboard-preview.html)

- [Producto y alcance](docs/producto.md)
- [Requisitos y criterios de aceptación](docs/requisitos.md)
- [Arquitectura y seguridad](docs/arquitectura.md)
- [Contrato inicial de la API](docs/api.md)
- [Diseño de interfaz](docs/diseno-ui.md)
- [Plan del equipo y calendario](docs/plan-equipo.md)
- [Backlog inicial](docs/backlog.md)
- [Plan de pruebas](docs/pruebas.md)
- [Despliegue en Railway](docs/despliegue.md)
- [Entrega académica](docs/entrega-academica.md)

## Estructura del repositorio

- `backend/`: API REST construida con Spring Boot 3 y Java 21, gestionada con Maven Wrapper (`./mvnw`).
- `infra/`: Configuraciones de infraestructura y servicios locales (Docker Compose para PostgreSQL se incorpora en UL-03).
- `docs/`: Documentación técnica, arquitectura, backlog de tareas y diseño visual del sistema.

## Guía de desarrollo (Backend)

Esta guía permite compilar y arrancar el backend mínimo con JDK 21.

### Requisitos previos

- **Java Development Kit (JDK):** Versión 21 (LTS). Es necesario contar con un
  JDK 21 instalado en el sistema. El wrapper de Maven no instala Java.
- **Maven:** No es necesario instalar Maven de forma global; el repositorio
  incluye **Maven Wrapper** (`mvnw` para macOS/Linux y `mvnw.cmd` para Windows).
  En la primera ejecución, el wrapper requiere conexión a internet para
  descargar Maven y las dependencias que no estén en la caché local.

### Directorio de trabajo

Todos los comandos del backend se ejecutan desde el directorio `backend/`.
Desde la raíz del repositorio, navega una sola vez:

```bash
cd backend
```

> **Importante:** Todos los comandos siguientes asumen que te encuentras dentro
> de `backend/`. No vuelvas a ejecutar `cd backend` si ya estás en dicho
> directorio. En Windows, utiliza `mvnw.cmd` en lugar de `./mvnw`
> (`.\mvnw.cmd` en PowerShell).

### Configuración de JDK 21 en la sesión de terminal

Si el sistema utiliza otra versión de Java por defecto (por ejemplo, Java 26 u
otra distribución instalada), selecciona JDK 21 únicamente en la terminal activa
usando las variables `JAVA_HOME` y `PATH`. No realices cambios globales con
`sudo`, no crees enlaces simbólicos ni modifiques archivos de inicio del shell
(como `.zshrc` o `.bashrc`).

#### Ejemplo en macOS (Homebrew)

Si instalaste OpenJDK 21 vía Homebrew en macOS con Apple Silicon, exporta las
variables en la sesión actual:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

#### Ejemplo genérico (Linux u otras rutas)

Exporta la ruta absoluta de la instalación de JDK 21 en la terminal actual:

```bash
export JAVA_HOME=/ruta/a/tu/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

En Windows, configura `JAVA_HOME` y antepone su carpeta `bin` a `PATH` en
la sesión actual. En Command Prompt:

```bat
set "JAVA_HOME=C:\ruta\a\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
```

En PowerShell:

```powershell
$env:JAVA_HOME = "C:\ruta\a\jdk-21"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

#### Comprobación de la versión activa

Comprueba que tanto Java como Maven Wrapper reconozcan la versión 21:

```bash
java -version
./mvnw -v
```

*(En Windows, ejecuta `mvnw.cmd -v` en lugar de `./mvnw -v`).*

### Comandos de desarrollo

Primero compila con el comando 1. Luego elige una de las formas de arranque
(2, 3 o 4), no las ejecutes simultáneamente. Detén el servidor con `Ctrl + C`
antes de cambiar de forma de arranque. Los ejemplos usan Bash o Zsh; en Windows
configura `PORT` con `set PORT=8085` (Command Prompt) o
`$env:PORT = "8085"` (PowerShell) antes de ejecutar Java o Maven Wrapper.

1. **Compilar, verificar y ejecutar pruebas automatizadas:**
   Ejecuta el ciclo de verificación completo (compilación, pruebas unitarias y
   empaquetado del artefacto):
   ```bash
   ./mvnw clean verify
   ```

2. **Iniciar el servidor en modo desarrollo:**
   Inicia la aplicación Spring Boot directamente desde Maven:
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Ejecutar el archivo JAR empaquetado:**
   Ejecuta el archivo JAR generado por `clean verify` (o `clean package`) en
   `target/`:
   ```bash
   java -jar target/urleads-backend-0.0.1-SNAPSHOT.jar
   ```

4. **Configuración de puerto alternativo:**
   Por defecto, la aplicación escucha en el puerto `8080`. Puedes indicar otro
   puerto mediante la variable de entorno `PORT`:
   ```bash
   PORT=8085 java -jar target/urleads-backend-0.0.1-SNAPSHOT.jar
   ```
   *(También se puede usar con `PORT=8085 ./mvnw spring-boot:run`).*

5. **Comprobación de salud (Health Check):**
   Con la aplicación en ejecución, comprueba la respuesta HTTP desde otra
   terminal:
   ```bash
   curl http://localhost:8080/api/health
   # Respuesta esperada: {"status":"UP"}
   ```
   *(Si iniciaste en el puerto 8085, consulta `http://localhost:8085/api/health`).*

6. **Detener el servidor:**
   Para detener la aplicación (iniciada con `spring-boot:run` o `java -jar`),
   presiona `Ctrl + C` en la terminal donde se está ejecutando.

## Convenciones de trabajo

- **Gestión de tareas:** Se utiliza [Linear (UrLeads en DSW2)](https://linear.app/enmanuelprojects/project/urleads-b3c2085df458) como única fuente de verdad para el backlog, responsables, dependencias y estimaciones.
- **Control de versiones y código:** Se utiliza GitHub con ramas cortas temáticas asociadas a cada tarea de Linear. Todo cambio se integra mediante Pull Requests revisados.
- **Seguridad:** No versionar secretos, contraseñas, archivos `.env` ni credenciales reales en el repositorio.

## Fecha objetivo

Tener una versión candidata del MVP el 15 de octubre de 2026 y el producto
validado para el 16 de octubre. Se reserva el 17 y 18 para correcciones, antes
de la exposición prevista entre el 19 y el 23 de octubre. Confirmen la fecha
exacta de exposición con el docente.
