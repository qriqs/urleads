# Contratos comunes y especificaciones de arquitectura Backend V2

Este documento establece las convenciones técnicas, contratos REST, eventos
asíncronos y decisiones de diseño compartidas para el desarrollo de los tres
microservicios de UrLeads Backend V2.

> **Nota:** Todos los contratos aquí descritos representan especificaciones
> técnicas acordadas para guiar la implementación desacoplada. Cada servicio
> se desarrolla de forma autónoma utilizando fixtures y mocks basados en estos
> esquemas.

---

## 1. Topología de servicios, puertos y rutas

Cada servicio es un proyecto Spring Boot independiente con su propia base de
datos relacional (esquema o base PostgreSQL aislada) y configuración local.

| Servicio | Responsable | Directorio | Rama Git | Puerto local | Prefijos de ruta |
|---|---|---|---|:---:|---|
| **CRM** | Cris | `backend/` | `feat/crm` | `8080` | `/api` (legacy MVP)<br>`/internal/v1` (inter-servicio) |
| **Comercial** | Sebas | `services/comercial/` | `feat/comercial` | `8081` | `/api/v1/catalog`<br>`/api/v1/quotes`<br>`/api/v1/metrics` |
| **Agenda y Notificaciones** | Alex | `services/agenda/` | `feat/agenda-notificaciones` | `8082` | `/api/v1/notifications`<br>`/api/v1/tasks`<br>`/api/v1/appointments`<br>`/ws/notifications` (WebSocket) |

---

## 2. Identidad, seguridad y propagación de tokens

1. **Autenticación del usuario:**
   - La interfaz web legacy del CRM conserva el inicio de sesión basado en sesión
     y cookie `HttpOnly` con protección CSRF.
   - Para las APIs V2 y la comunicación entre servicios se utiliza tokens JWT
     estándar con algoritmo de firma asimétrica (RSA/ECDSA) o clave HMAC secreta
     gestionada en variables de entorno.
   - Cada servicio valida criptográficamente: `iss` (emisor), `aud` (audiencia
     del servicio receptor), `exp` (expiración) y `sub` (identificador del usuario).

2. **Aislamiento por propietario (`ownerId`):**
   - El identificador del propietario se obtiene **exclusivamente del subject (`sub`)
     del token JWT validado**.
   - Ningún endpoint confía en un `userId` o `ownerId` proporcionado como parámetro
     por el cliente en el cuerpo o la URL.
   - No se permite suplantación arbitraria de identidades entre servicios.

3. **Propagación segura (Token Relay):**
   - Las llamadas HTTP entre servicios (ej. Comercial invocando CRM vía OpenFeign)
     propagan la cabecera `Authorization: Bearer <token>` recibida de la solicitud
     del usuario.

---

## 3. Contrato de consulta interna de leads (CRM -> Comercial / Agenda)

Exclusivo para la validación interna de existencia y pertenencia de un lead.

- **Método y ruta:** `GET /internal/v1/leads/{id}`
- **Cabeceras obligatorias:** `Authorization: Bearer <token>`
- **Identificador de lead:** `Long` (se conserva el tipo numérico del CRM).

### Respuestas HTTP

- **200 OK:** Lead encontrado y perteneciente al usuario del token. Retorna
  únicamente datos no sensibles (sin PII de contacto ni notas privadas):
  ```json
  {
    "id": 105,
    "stage": "EN_SEGUIMIENTO",
    "nextFollowUp": "2026-10-25"
  }
  ```
- **401 Unauthorized:** Token ausente, firma inválida o expirado.
- **403 Forbidden:** Token válido pero audiencia no autorizada.
- **404 Not Found:** El lead no existe o pertenece a otro usuario (no se revela
  la existencia de leads ajenos) o se encuentra archivado.

---

## 4. Convenciones REST, errores y paginación

### Formato estándar de errores (RFC 7807 compatible)

```json
{
  "timestamp": "2026-10-17T23:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "El precio unitario debe ser un valor decimal positivo.",
  "path": "/api/v1/catalog"
}
```

### Formato estándar de paginación

Las consultas de listados paginados retornan una estructura determinista con
orden estable (`createdAt DESC, id DESC`):

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 48,
  "totalPages": 3,
  "last": false
}
```

### Manejo de fechas y monedas

- **Timestamps:** En formato UTC ISO-8601 con sufijo `Z`
  (`2026-10-17T23:30:00Z`).
- **Fechas sin hora (agenda/seguimiento local):** En formato `YYYY-MM-DD`
  asociadas a la zona horaria del usuario (`America/Lima`).
- **Valores monetarios:** Siempre en tipo `BigDecimal` con 2 decimales y redondeo
  `RoundingMode.HALF_UP`. Se debe especificar el código de divisa ISO 4217
  (`PEN`, `USD`). Queda estrictamente prohibido sumar montos de monedas distintas.

---

## 5. Ciclo de vida de cotizaciones (Servicio Comercial)

- **Estados:** `DRAFT` (Borrador), `SENT` (Enviada), `ACCEPTED` (Aceptada),
  `REJECTED` (Rechazada).
- **Regla de inmutabilidad:** Al pasar a `ACCEPTED` o `REJECTED`, la cotización
  queda bloqueada. Cualquier edición posterior requiere generar una nueva versión
  o revisión (`v2`).
- **Instantánea de conceptos:** Las líneas de cotización guardan copia inmutable
  del nombre y precio unitario del ítem al crearse; cambios en el catálogo no
  alteran cotizaciones existentes.

---

## 6. Mensajería asíncrona con RabbitMQ

Se utiliza RabbitMQ mediante AMQP para publicar eventos de dominio de forma
desacoplada y tolerante a fallos.

### Topología del broker

- **Exchange:** `urleads.events.v1` (Tipo: `topic`, Durable).
- **Routing Key de cotización aceptada:** `quotation.accepted.v1`
  (alias admitido: `quote.accepted.v1`).
- **Cola de Agenda:** `agenda.quotation-accepted.v1` (Durable).
- **Cola de errores (Dead Letter):** `agenda.quotation-accepted.dlq`.

### Sobre estándar de evento (`Event Envelope`)

Todos los eventos publicados en el broker respetan la siguiente estructura:

```json
{
  "eventId": "a7b3c1d9-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "eventType": "quotation.accepted.v1",
  "version": 1,
  "occurredAt": "2026-10-17T23:30:00Z",
  "ownerId": "3d0e2bd8-a006-4473-8cfc-f8fa62209c50",
  "data": {
    "quoteId": 204,
    "leadId": 105,
    "currency": "USD",
    "total": 1250.00
  }
}
```

> **Privacidad:** El payload `data` contiene únicamente referencias numéricas y
> totales. Nunca contiene PII (nombres, correos, teléfonos) ni notas privadas.

### Reglas de fiabilidad

- **Publicador (Transactional Outbox):** El servicio Comercial persiste el evento
  en una tabla `outbox` en la misma transacción SQL que la aceptación de la
  cotización. Un worker en segundo plano publica al exchange con *publisher confirms*.
- **Consumidor idempotente:** El servicio Agenda registra los `eventId` procesados
  en una tabla con restricción `UNIQUE`. Mensajes duplicados son descartados
  emitiendo `ack` inmediato.
- **Persistencia antes de ACK:** Agenda persiste la notificación en base de datos
  antes de confirmar la recepción al broker.

---

## 7. Notificaciones y transporte WebSocket (Servicio Agenda)

- **Protocolo predeterminado:** WebSocket estándar (RFC 6455) en `/ws/notifications`.
  No se utiliza Socket.IO ni librerías propietarias no interoperables.
- **Autenticación de conexión:** Se realiza mediante mensaje inicial de
  autenticación con el token JWT o ticket efímero seguro. No se transmiten tokens
  en la URL de conexión ni se imprimen en logs.
- **Canalización por usuario:** Cada conexión se asocia al `sub` del usuario y
  recibe únicamente los eventos de su propiedad.
- **Respaldo REST:** Todas las notificaciones emitidas por WebSocket son
  persistidas en base de datos y se pueden consultar en `GET /api/v1/notifications`.
  Una desconexión temporal nunca provoca pérdida de información.

---

## 8. Fiabilidad, Circuit Breaker y Mocks de desarrollo

- **OpenFeign + Resilience4j:** El cliente Feign hacia CRM tiene timeouts
  configurados (conexión 2s, lectura 3s) y Circuit Breaker. Ante caída de CRM,
  retorna un error HTTP 503 limpio.
- **Desarrollo independiente con WireMock:** Durante el desarrollo en
  `feat/comercial` y `feat/agenda-notificaciones`, cada desarrollador utiliza
  WireMock o fixtures locales simulando las respuestas acordadas de CRM sin
  requerir que el otro servicio esté corriendo en tiempo real.
