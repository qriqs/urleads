# Guía de desarrollo: Servicio Agenda y Notificaciones (Alex)

Esta guía describe el entorno de trabajo, comandos rápidos y el índice de tareas
asignadas a Alex para el microservicio Agenda y Notificaciones en UrLeads
Backend V2.

---

## 1. Información general del servicio

- **Responsable:** Alex
- **Directorio de trabajo:** `services/agenda/`
- **Rama Git:** `feat/agenda-notificaciones`
- **Puerto HTTP local:** `8082`
- **Base de datos:** PostgreSQL local (base `urleads_agenda` o esquema `agenda`)
- **Contratos compartidos:** Consulta [docs/backend-v2/contratos.md](contratos.md)
- **Flujo de ramas:** Consulta [docs/backend-v2/flujo-ramas.md](flujo-ramas.md)

---

## 2. Inicio rápido y comandos locales

```bash
# Cambiar a la rama de trabajo
git switch feat/agenda-notificaciones

# Navegar a la carpeta del microservicio Agenda
cd services/agenda

# Ejecutar las pruebas automatizadas del servicio
./mvnw clean test

# Arrancar el servicio Agenda (puerto 8082)
./mvnw spring-boot:run
```

---

## 3. Matriz de tareas en Linear (15 tareas)

| Código | Tarea | Bloque | Estado | Depende de | Linear |
|:---:|---|:---:|:---:|:---:|:---:|
| **A01** | Configuración base de Agenda y migraciones independientes | Principal | `Todo` | - | [DSW-71](https://linear.app/enmanuelprojects/issue/DSW-71) |
| **A02** | API REST de notificaciones persistentes y paginadas | Principal | `Todo` | A01 | [DSW-72](https://linear.app/enmanuelprojects/issue/DSW-72) |
| **A03** | Consumidor fiable RabbitMQ de cotizaciones aceptadas | Principal | `Todo` | A01 | [DSW-73](https://linear.app/enmanuelprojects/issue/DSW-73) |
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
| **A15** | Integración real con CRM, Comercial y recuperación tras fallos | Integración | `Todo` | A05, A14, S05, C04 | [DSW-85](https://linear.app/enmanuelprojects/issue/DSW-85) |

---

## 4. Pautas clave de implementación

1. **Aislamiento del proyecto:** Trabaja exclusivamente dentro de `services/agenda/`.
   No modifiques `backend/` ni `services/comercial/`.
2. **Consumo de RabbitMQ con fixtures (A03):** No necesitas que Sebas esté
   publicando eventos para probar tu consumidor. Inyecta mensajes sintéticos con
   el formato acordado de `quote.accepted.v1` directamente en tus tests de Spring
   AMQP.
3. **Transporte WebSocket (A04):** Implementa WebSocket estándar según RFC 6455
   bajo `/ws/notifications`. No utilices la librería Socket.IO ni STOMP/SockJS
   por defecto. Asegura que la autenticación ocurra mediante el primer frame o
   ticket efímero sin poner el token en la query string.
4. **Respaldo persistente (A02/A05):** Todas las notificaciones deben guardarse
   en base de datos PostgreSQL antes de ser emitidas o confirmadas. Si el cliente
   se desconecta del WebSocket, debe poder ver las notificaciones pendientes vía
   `GET /api/v1/notifications`.
5. **Scheduler durable (A09):** La cola de RabbitMQ es para transporte de eventos,
   no un scheduler temporal. Usa Spring Task Scheduler o Quartz con ocurrencias
   únicas (`occurrenceId`) para gestionar recordatorios programados en base de datos.
