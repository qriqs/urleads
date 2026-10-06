# UrLeads

UrLeads es un CRM web privado para organizar clientes potenciales, registrar
notas, controlar su etapa y recordar próximos seguimientos. El MVP se enfoca en
un usuario y un flujo de trabajo sencillo, con una interfaz responsive y una
API REST respaldada por PostgreSQL.

Este repositorio contiene el plan del producto y la documentación académica y
técnica inicial. La documentación describe el trabajo acordado, no afirma que
la aplicación ya esté implementada.

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

### Requisitos previos

- **Java Development Kit (JDK):** Versión 21 (LTS).
- **Maven:** No es necesario instalar Maven globalmente; el repositorio incluye **Maven Wrapper** en `backend/mvnw`.

### Comandos de desarrollo

Todos los comandos del backend se ejecutan desde el directorio `backend/`:

1. **Compilar y ejecutar pruebas automatizadas:**
   ```bash
   cd backend
   ./mvnw clean test
   ```

2. **Empaquetar la aplicación (generar archivo JAR):**
   ```bash
   cd backend
   ./mvnw clean package
   ```

3. **Iniciar el servidor en modo desarrollo:**
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

4. **Ejecutar el JAR compilado:**
   ```bash
   java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar
   ```

5. **Configuración de puerto:**
   Por defecto la aplicación inicia en el puerto `8080`. Se puede configurar mediante la variable de entorno `PORT`:
   ```bash
   PORT=8085 java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar
   ```

6. **Comprobación de salud (Health Check):**
   ```bash
   curl http://localhost:8080/api/health
   # Respuesta: {"status":"UP"}
   ```

## Convenciones de trabajo

- **Gestión de tareas:** Se utiliza [Linear (UrLeads en DSW2)](https://linear.app/enmanuelprojects/project/urleads-b3c2085df458) como única fuente de verdad para el backlog, responsables, dependencias y estimaciones.
- **Control de versiones y código:** Se utiliza GitHub con ramas cortas temáticas asociadas a cada tarea de Linear. Todo cambio se integra mediante Pull Requests revisados.
- **Seguridad:** No versionar secretos, contraseñas, archivos `.env` ni credenciales reales en el repositorio.

## Fecha objetivo

Tener una versión candidata del MVP el 15 de octubre de 2026 y el producto
validado para el 16 de octubre. Se reserva el 17 y 18 para correcciones, antes
de la exposición prevista entre el 19 y el 23 de octubre. Confirmen la fecha
exacta de exposición con el docente.
