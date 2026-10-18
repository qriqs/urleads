# Contratos comunes y especificaciones de arquitectura Backend V2

Este documento establece las convenciones técnicas, contratos REST, eventos
asíncronos y decisiones de diseño compartidas para el desarrollo de los tres
microservicios de UrLeads Backend V2.

> **Nota:** Todos los contratos aquí descritos representan decisiones de diseño
> y especificaciones técnicas acordadas para guiar la implementación futura de
> forma desacoplada. Ningún servicio ni endpoint se considera implementado en
> esta etapa de preparación organizativa. Cada servicio se desarrollará de forma
> autónoma utilizando fixtures y mocks basados en estos esquemas.

---

## 1. Topología de servicios, puertos y rutas

Cada servicio es un proyecto Spring Boot independiente con su propia base de
datos relacional (base de datos PostgreSQL aislada con esquema propio) y
configuración local.

| Servicio | Responsable | Directorio | Rama Git | Puerto local | Prefijos de ruta |
|---|---|---|---|:---:|---|
| **CRM** | Cris | `backend/` | `feat/crm` | `8080` | `/api` (legacy MVP)<br>`/internal/v1` (inter-servicio)<br>`/api/auth/access-token` |
| **Comercial** | Sebas | `services/comercial/` | `feat/comercial` | `8081` | `/api/v1/catalog`<br>`/api/v1/quotes`<br>`/api/v1/metrics` |
| **Agenda y Notificaciones** | Alex | `services/agenda/` | `feat/agenda-notificaciones` | `8082` | `/api/v1/notifications`<br>`/api/v1/tasks`<br>`/api/v1/appointments`<br>`/ws/notifications` (WebSocket RFC 6455) |

---

<a id="autenticacion-y-seguridad-comun"></a>
## 2. Identidad, seguridad y convención de tokens JWT

1. **Algoritmo y firma criptográfica:**
   - Se utiliza **exclusivamente RS256** (claves asimétricas RSA con SHA-256).
   - El servicio CRM (`backend/`) custodia la clave privada y es el único emisor
     autorizado.
   - Los servicios consumidores (`comercial`, `agenda`) configuran únicamente la
     clave pública de verificación del emisor CRM.
   - En entornos locales de prueba, cada desarrollador genera pares de claves
     sintéticas de forma independiente sin cometer secretos reales al repositorio.

2. **Emisor y audiencias (`iss` y `aud`):**
   - **Emisor exacto (`iss`):** `urleads-crm`.
   - **Audiencias permitidas (`aud`):**
     - `crm-api`: Endpoints protegidos del CRM (incluyendo `/internal/v1`).
     - `comercial-api`: Endpoints del servicio Comercial.
     - `agenda-api`: Endpoints REST y WebSocket del servicio Agenda.
   - Cada servicio valida que su propio identificador figure en la lista de
     audiencias del token (`aud`). Para flujos demostrativos integrados, el token
     emitido puede declarar las tres audiencias: `["crm-api", "comercial-api", "agenda-api"]`.
   - Se prohíbe el reenvío a ciegas de tokens monocanal y no existe fallback de
     suplantación por cuentas de servicio (*impersonation*).

3. **Sujeto e identidad de usuario (`sub` y `ownerId`):**
   - El claim `sub` es una **cadena decimal que representa el `usuario.id` numérico
     del CRM** (ejemplo: `"42"`). Nunca se utilizan UUIDs de Linear ni cadenas
     arbitrarias.
   - El CRM mapea internamente el usuario autenticado en sesión hacia su `usuario.id`.
   - El `ownerId` en eventos firmados y entidades es siempre dicha cadena decimal.
   - Ningún endpoint acepta un `ownerId` o `userId` enviado por el cliente en el
     cuerpo o la URL.

4. **Alcances de autorización (`scope`):**
   - Declarados como cadena separada por espacios en el claim `scope`:
     - `crm.leads.read`: Consulta interna autorizada de leads.
     - `comercial.access`: Gestión de catálogo y cotizaciones.
     - `agenda.access`: Gestión de agenda y conexión WebSocket de notificaciones.

5. **Vigencia y validaciones:**
   - Tiempo de vida (TTL): **10 minutos (600 segundos)** con margen de reloj
     (*clock skew*) admisible de hasta 30 segundos.
   - Validaciones estrictas: firma válida con RS256, `iss == "urleads-crm"`,
     `aud` contiene el servicio receptor, `exp` vigente, `sub` presente y `scope`
     requerido para la operación.

6. **Ruta de emisión en tiempo de ejecución (Tarea C02):**
   - `POST /api/auth/access-token`: Endpoint expuesto por CRM sobre la sesión
     Spring Security existente con cookie `HttpOnly` y validación CSRF.
   - No recibe parámetros de usuario; deriva la identidad y scopes de la sesión.
   - Retorna:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 600
}
```
   - Si no hay sesión válida retorna **401 Unauthorized**; si falla el token CSRF
     retorna **403 Forbidden**.
   - No se implementa refresh token; mientras la sesión del navegador siga activa,
     el cliente solicita un nuevo token. El cierre de sesión (*logout*) invalida
     la sesión web; los JWT ya emitidos permanecen vigentes hasta su expiración
     técnica de 10 minutos (no se implementa revocación distribuida).

7. **Mapeo de códigos de error de seguridad:**
   - Token ausente, firma inválida, expirado, emisor desconocido o audiencia
     incorrecta: **401 Unauthorized**.
   - Token válido y vigente pero carente del `scope` requerido: **403 Forbidden**.
   - Token y scope válidos pero el recurso solicitado no existe o pertenece a otro
     propietario: **404 Not Found** (no se filtra existencia de datos ajenos).

---

<a id="consulta-interna-de-leads"></a>
## 3. Contrato de consulta interna de leads (CRM -> Comercial / Agenda)

Endpoint expuesto por CRM para verificar la existencia y estado de un lead antes
de cotizar o agendar actividades.

- **Método y ruta:** `GET /internal/v1/leads/{id}`
- **Cabeceras obligatorias:** `Authorization: Bearer <token>`
- **Identificador de lead (`id`):** Numérico (`Long`), conservando el modelo del CRM.
- **Audiencia requerida:** `crm-api`
- **Scope requerido:** `crm.leads.read`

### Respuestas HTTP

- **200 OK:** Lead encontrado y perteneciente al usuario del token (`sub`).
  Retorna payload mínimo sin PII (sin nombres, teléfonos, correos ni notas):
```json
  {
    "id": 105,
    "stage": "EN_SEGUIMIENTO",
    "nextFollowUp": "2026-10-25"
  }
```
- **401 Unauthorized:** Token ausente, firma inválida, expirado o audiencia distinta de `crm-api`.
- **403 Forbidden:** Token válido pero sin scope `crm.leads.read`.
- **404 Not Found:** El lead no existe, pertenece a otro usuario o está archivado.

---

<a id="paginacion-y-errores-estandar"></a>
## 4. Convenciones REST, errores y paginación

### Formato de errores acordado V2

Estructura consistente en todos los microservicios:

```json
{
  "status": 400,
  "code": "INVALID_PRICE",
  "message": "El precio unitario debe ser un valor decimal positivo.",
  "fieldErrors": [
    {
      "field": "unitPrice",
      "message": "Debe ser mayor a 0"
    }
  ]
}
```

### Formato estándar de paginación

Listados paginados con ordenamiento estable (`createdAt DESC, id DESC`):

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "last": true
}
```

Ejemplo con registros:

```json
{
  "content": [
    {
      "id": 101,
      "code": "SRV-01",
      "name": "Consultoria Inicial",
      "currency": "USD",
      "unitPrice": 150.00,
      "active": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

<a id="fechas-y-zonas-horarias"></a>
### Manejo de fechas y monedas

- **Timestamps:** Formato UTC ISO-8601 estricto con sufijo `Z` (`2026-10-17T23:30:00Z`).
- **Fechas locales (agenda / seguimiento):** Formato `YYYY-MM-DD` interpretadas
  en la zona horaria del usuario (`America/Lima`).
- **Valores monetarios:** Siempre en `BigDecimal` con precisión decimal (2 decimales)
  y redondeo `RoundingMode.HALF_UP`. Se debe registrar el código de moneda ISO 4217
  (`PEN`, `USD`). Prohibido consolidar o sumar montos en monedas distintas sin tipo
  de cambio explícito.

---

<a id="convenciones-rest-comercial"></a>
<a id="cotizaciones"></a>
## 5. Catálogo y ciclo de vida de cotizaciones (Comercial)

- **Rutas base:** `/api/v1/catalog` y `/api/v1/quotes`.
- **Estados de cotización:** `DRAFT`, `SENT`, `ACCEPTED`, `REJECTED`.
- **Regla de inmutabilidad:** Al pasar a `ACCEPTED` o `REJECTED`, la cotización
  queda bloqueada para edición directa. Modificaciones posteriores requieren
  crear una nueva versión o revisión inmutable (`v2`).
- **Instantáneas inmutables de líneas:** Cada línea de cotización almacena una copia
  fiel e independiente del nombre, descripción y precio unitario del catálogo al
  momento de crearla. Ediciones futuras en el catálogo no alteran cotizaciones
  históricas.

---

<a id="eventos-rabbitmq"></a>
## 6. Mensajería asíncrona con RabbitMQ

Se utiliza RabbitMQ mediante protocolo AMQP para la publicación y consumo de
eventos de dominio desacoplados y fiables.

### Convención única de nombres y claves de enrutamiento

- **Nombre canónico único para cotización aceptada:** `quotation.accepted.v1`
  *(sin alias alternativos para evitar pérdidas silenciosas de enrutamiento)*.
- **Exchange principal:** `urleads.events.v1` (Tipo: `topic`, Durable).
- **Routing key principal:** `quotation.accepted.v1`.
- **Cola principal de Agenda:** `agenda.quotation-accepted.v1` (Durable).

### Topología de Dead Letter (errores y reintentos)

- **Exchange de descarte (DLX):** `urleads.dead.v1` (Tipo: `topic`, Durable).
- **Routing key de error:** `agenda.quotation-accepted.failed.v1`.
- **Cola de mensajes fallidos (DLQ):** `agenda.quotation-accepted.dlq` (Durable,
  suscrita al exchange `urleads.dead.v1`).
- **Política de reintentos:** Máximo 3 intentos de procesamiento acotados con
  retraso antes del rechazo explícito sin requeue (`basic.reject(requeue=false)`),
  provocando el enrutamiento automático hacia la DLQ para inspección y evitando
  bucles infinitos.

### Eventos de dominio CRM (Tarea C04)

Definidos para que el publicador CRM disponga de esquema fijo sin depender de
consumidores inmediatos:
- `lead.created.v1` (Routing key: `lead.created.v1`): data `{ "leadId": 105, "stage": "NUEVO", "nextFollowUp": "2026-10-25" }`.
- `lead.updated.v1` (Routing key: `lead.updated.v1`): data `{ "leadId": 105, "stage": "EN_SEGUIMIENTO", "nextFollowUp": "2026-10-28" }`.
- `lead.deleted.v1` (Routing key: `lead.deleted.v1`): data `{ "leadId": 105 }`.

### Sobre estándar de evento (`Event Envelope`)

Todos los eventos publicados en `urleads.events.v1` cumplen la siguiente estructura:

```json
{
  "eventId": "a7b3c1d9-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "eventType": "quotation.accepted.v1",
  "version": 1,
  "occurredAt": "2026-10-17T23:30:00Z",
  "ownerId": "42",
  "data": {
    "quoteId": 204,
    "leadId": 105,
    "currency": "USD",
    "total": 1250.00
  }
}
```

> **Aislamiento y privacidad:** `ownerId` es una cadena decimal con el `usuario.id`
> del CRM (ejemplo: `"42"`). `quoteId` y `leadId` son números JSON enteros. `data`
> contiene únicamente identificadores numéricos y montos; **nunca contiene PII**
> (nombres, correos, teléfonos) ni notas privadas.

### Reglas de fiabilidad y consistencia

1. **Patrón Transactional Outbox (Comercial / CRM):** El evento se persiste en la
   tabla `outbox` en la misma transacción SQL que el cambio de estado de la
   cotización o lead. Un worker asíncrono despacha hacia RabbitMQ con *publisher confirms*.
   Las confirmaciones aseguran entrega at-least-once (no exactly-once); los mensajes
   no enrutables (*mandatory/unroutable*) se detectan y registran como error.
2. **Consumo idempotente (Agenda):** La cola procesa eventos identificados por
   `eventId`. En la misma transacción SQL se persiste la notificación y se registra
   el `eventId` en una tabla de eventos procesados con restricción `UNIQUE`.
3. **Confirmación al broker:** La confirmación (`basic.ack`) se emite **únicamente
   después de confirmar el commit en base de datos**. Si un mensaje repetido llega
   al consumidor y ya está registrado en la tabla de deduplicación, se emite `ack`
   inmediato sin duplicar la notificación.

---

<a id="notificaciones"></a>
<a id="transporte-websocket"></a>
## 7. Notificaciones y transporte WebSocket (Agenda)

- **Protocolo y ruta:** WebSocket estándar (RFC 6455) sobre `/ws/notifications`
  (`wss://` en entornos de producción).
- **Requisito de transporte:** WebSocket estándar es la opción por defecto. No se
  emplea Socket.IO, STOMP ni SockJS salvo requerimiento curricular explícito que
  motive una decisión separada. No se asume cabecera `Authorization` personalizada
  en navegadores para el handshake.
- **Autenticación mediante primer frame:**
  1. El cliente inicia la conexión WebSocket estándar.
  2. La conexión queda en estado pendiente de autenticación (sin transmitir datos).
  3. Dentro de un plazo límite de **5 segundos**, el cliente debe enviar el primer
     frame de texto JSON con el token de acceso:
```json
     {
       "type": "authenticate",
       "accessToken": "eyJhbGciOiJSUzI1NiIs..."
     }
```
  4. El servidor valida la firma RS256, emisor `urleads-crm`, audiencia `agenda-api`
     y scope `agenda.access`. Si es válido, responde:
```json
     {
       "type": "authenticated"
     }
```
  5. Si el token es inválido, expiró o transcurren más de 5 segundos sin autenticar,
     el servidor cierra la conexión con código **1008 (Policy Violation)**.
- **Emisión en vivo de notificaciones:**
  Al procesarse un evento, el servidor emite al canal del usuario autenticado:
```json
  {
    "type": "notification.created",
    "data": {
      "id": 501,
      "sourceEventId": "a7b3c1d9-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
      "createdAt": "2026-10-17T23:30:00Z",
      "read": false
    }
  }
```
- **Respaldo persistente vía REST:** Todas las notificaciones emitidas por
  WebSocket son almacenadas previamente en la base de datos PostgreSQL de Agenda.
  Los clientes desconectados consultan las notificaciones no leídas en
  `GET /api/v1/notifications`. La expiración del token durante una sesión activa
  requiere que el cliente reconecte con un nuevo token fresco; la conexión no
  renueva tokens silenciosamente.
