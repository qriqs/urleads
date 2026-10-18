# Guía de desarrollo: Servicio Agenda y Notificaciones (Alex)

Esta guía describe el entorno de trabajo, comandos y el índice de tareas
asignadas a Alex para el microservicio Agenda y Notificaciones en UrLeads
Backend V2.

> **Estado de las tareas y directorio:** Los estados indicados en esta guía
> corresponden a la **fotografía inicial** de planificación; Linear es la fuente
> de verdad para el seguimiento activo. El directorio `services/agenda/` **no
> existe actualmente**; se creará como parte de la tarea A01. Ninguna tarea se
> encuentra implementada en esta fase preparatoria.

---

## 1. Información general del servicio

- **Responsable:** Alex
- **Directorio de trabajo:** `services/agenda/` (a crear en A01)
- **Rama Git:** `feat/agenda-notificaciones`
- **Puerto HTTP local:** `8082`
- **Base de datos:** PostgreSQL local (base `urleads_agenda` y base de pruebas
  aislada `urleads_agenda_test` para migraciones y persistencia)
- **Contratos compartidos:** Consulta [docs/backend-v2/contratos.md](contratos.md)
- **Flujo de ramas:** Consulta [docs/backend-v2/flujo-ramas.md](flujo-ramas.md)

---

## 2. Comandos actuales y flujo de inicio

### Comandos seguros actuales (fase de preparación)

```bash
# 1. Obtener ramas remotas y situarse en la rama de trabajo
git fetch origin
git switch feat/agenda-notificaciones
```

### Comandos futuros (tras implementar la tarea A01)

Una vez que la tarea A01 inicialice el proyecto Spring Boot con Maven Wrapper en
`services/agenda/`, se utilizarán los siguientes comandos:

```bash
# Navegar a la carpeta del microservicio Agenda
cd services/agenda

# Ejecutar pruebas automatizadas (requiere PostgreSQL local para pruebas de persistencia)
./mvnw clean test

# Arrancar el servicio Agenda de forma autónoma (puerto 8082)
./mvnw spring-boot:run
```

---

## 3. Matriz de tareas en Linear (15 tareas planificadas)

| Código | Tarea | Bloque | Estado inicial | Depende de | Linear |
|:---:|---|:---:|:---:|:---:|:---:|
| **A01** | Configuración base de Agenda y migraciones independientes | Principal | `Todo` | - | [DSW-71](https://linear.app/enmanuelprojects/issue/DSW-71) |
| **A02** | API REST de notificaciones persistentes y paginadas | Principal | `Todo` | A01 | [DSW-72](https://linear.app/enmanuelprojects/issue/DSW-72) |
| **A03** | Consumidor fiable RabbitMQ de cotizaciones aceptadas | Principal | `Todo` | A01, A02 | [DSW-73](https://linear.app/enmanuelprojects/issue/DSW-73) |
| **A04** | Transporte WebSocket estándar autenticado para notificaciones | Principal | `Todo` | A01 | [DSW-74](https://linear.app/enmanuelprojects/issue/DSW-74) |
| **A05** | Flujo completo de notificación y pruebas de integración interna | Principal | `Todo` | A02, A03, A04 | [DSW-75](https://linear.app/enmanuelprojects/issue/DSW-75) |
| **A06** | CRUD de tareas vinculadas a lead autorizado | Ampliación | `Backlog` | A01 | [DSW-76](https://linear.app/enmanuelprojects/issue/DSW-76) |
| **A07** | Gestión de citas con control de zona horaria y estados | Ampliación | `Backlog` | A01, A06 | [DSW-77](https://linear.app/enmanuelprojects/issue/DSW-77) |
| **A08** | Consultas de agenda acotadas por rango de fechas | Ampliación | `Backlog` | A06, A07 | [DSW-78](https://linear.app/enmanuelprojects/issue/DSW-78) |
| **A09** | Programador durable de recordatorios con ocurrencias únicas | Ampliación | `Backlog` | A06, A07 | [DSW-79](https://linear.app/enmanuelprojects/issue/DSW-79) |
| **A10** | Acción de posponer (snooze) notificaciones con límites | Ampliación | `Backlog` | A02 | [DSW-80](https://linear.app/enmanuelprojects/issue/DSW-80) |
| **A11** | Consulta de resumen diario de pendientes de agenda | Ampliación | `Backlog` | A06, A07 | [DSW-81](https://linear.app/enmanuelprojects/issue/DSW-81) |
| **A12** | Registro de minutas y resultados estructurados de citas | Ampliación | `Backlog` | A07 | [DSW-82](https://linear.app/enmanuelprojects/issue/DSW-82) |
| **A13** | Preferencias de notificación y horario laboral por usuario | Ampliación | `Backlog` | A01 | [DSW-83](https://linear.app/enmanuelprojects/issue/DSW-83) |
| **A14** | Swagger UI, Postman y verificación aislada de WebSocket | Integración | `Todo` | A01, A05 | [DSW-84](https://linear.app/enmanuelprojects/issue/DSW-84) |
| **A15** | Integración real con CRM, Comercial y recuperación tras fallos | Integración | `Todo` | A05, A14, S05, C14 | [DSW-85](https://linear.app/enmanuelprojects/issue/DSW-85) |

---

## 4. Pautas clave de implementación

1. **Creación del proyecto y entregables (A01):** Inicializa el proyecto Spring Boot
   en `services/agenda/` con Java 21 y Maven Wrapper. Entrega el `Dockerfile` propio
   y receta de compilación para la integración en Compose de C14. Configura
   PostgreSQL con Flyway (las pruebas de integración usan una base PostgreSQL
   aislada `urleads_agenda_test`; no se utiliza H2 para pruebas de persistencia).
2. **Consumo fiable de RabbitMQ (A03):** Se suscribe a la cola durable
   `agenda.quotation-accepted.v1` en el exchange `urleads.events.v1` con la clave
   canónica `quotation.accepted.v1`. En la misma transacción SQL persiste la
   notificación y guarda el `eventId` en una tabla con restricción `UNIQUE`.
   Configura la Dead Letter Queue `agenda.quotation-accepted.dlq` con el exchange
   `urleads.dead.v1` y clave `agenda.quotation-accepted.failed.v1` tras 3 intentos.
3. **Transporte WebSocket estándar (A04):** Endpoint `/ws/notifications` (RFC 6455).
   No se utiliza Socket.IO ni STOMP/SockJS por defecto. La conexión queda pendiente
   hasta recibir el primer frame JSON `{ "type": "authenticate", "accessToken": "..." }`.
   Si no se autentica en 5 segundos o el token es inválido, se cierra con código 1008.
   Al emitir notificación, envía `{ "type": "notification.created", "data": { "id": 501, ... } }`.
4. **Respaldo persistente vía REST (A02/A05):** Toda notificación emitida en vivo
   se guarda previamente en PostgreSQL y se puede consultar en `GET /api/v1/notifications`.
   Los usuarios desconectados recuperan sus avisos pendientes vía REST sin pérdida.
5. **Scheduler durable (A09):** La cola de RabbitMQ no es un scheduler. Para
   recordatorios programados en base de datos, usa Spring Task Scheduler o Quartz
   con identificador único de ocurrencia (`occurrenceId`) para garantizar disparos
   idempotentes tras reinicios del servicio.
