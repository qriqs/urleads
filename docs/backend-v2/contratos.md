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
| **CRM** | Cris | `backend/` | `feat/crm` | `8080` | `/api` (legacy MVP)<br>`/internal/v1` (inter-servicio)<br>`/api/auth/access-token`<br>`/ws/leads` (SOAP 1.1) |
| **Comercial** | Sebas | `services/comercial/` | `feat/comercial` | `8081` | `/api/v1/catalog`<br>`/api/v1/quotes`<br>`/api/v1/metrics` |
| **Agenda y Notificaciones** | Alex | `services/agenda/` | `feat/agenda-notificaciones` | `8082` | `/api/v1/notifications`<br>`/api/v1/tasks`<br>`/api/v1/appointments`<br>`/ws/notifications` (WebSocket RFC 6455) |
| **Descubrimiento (Eureka)** | Cris (C14) | `infra/discovery/` | `feat/crm` | `8761` | `/eureka` (dashboard y registry interno) |

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
   - *Nota de implementación (C02):* Implementado en CRM (`backend/`) mediante
     `AccessTokenController`, `JwtIssuanceService` y validación estricta en
     `/internal/**`. Consulta detalles en [guia-cris.md](guia-cris.md#6-implementacion-y-verificacion-de-c02-identidad-y-tokens-jwt).
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

> **Nota de implementación (C03):** Endpoint implementado en CRM (`backend/`) mediante
> `InternalLeadController`, `LeadService.obtenerInterno` y el DTO inmutable `InternalLeadResponse`.
> La consulta se encuentra acotada por propietario (`findByIdAndUsuarioId`) derivado del `sub` del
> JWT verificado, retornando exclusivamente `id`, `stage` y `nextFollowUp` (o `null`).
> *Dependencia de archivo:* La exclusión de leads archivados es un requisito de integración
> dependiente de la tarea C12 (DSW-52), ya que el modelo actual no contiene campos de archivo.
> Consulta detalles en [guia-cris.md](guia-cris.md#7-implementacion-y-verificacion-de-c03-consulta-interna-de-leads).

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

---

<a id="descubrimiento-de-servicios-con-eureka"></a>
## 8. Descubrimiento dinámico de servicios con Eureka (Obligatorio)

El ecosistema Backend V2 utiliza **Spring Cloud Netflix Eureka** para el
registro y descubrimiento dinámico de microservicios sin direcciones IP ni
puertos fijos en tiempo de ejecución.

### Topología y servidor de descubrimiento

- **Servidor independiente:** Se implementa un servicio standalone Spring Cloud
  Netflix Eureka Server (`@EnableEurekaServer`) en `infra/discovery/`, puerto `8761`.
- **Nombre de aplicación del servidor:** `discovery-service`.
- **Persistencia:** No utiliza base de datos.
- **Aislamiento arquitectónico:** El servidor Eureka es un componente exclusivo
  de infraestructura gestionado en la tarea C14. No se debe añadir la dependencia
  de servidor Eureka (`spring-cloud-starter-netflix-eureka-server`) a los tres
  microservicios de negocio.
- **Entorno perimetral:** El dashboard y registro de Eureka son para consumo
  exclusivo de la red interna local. Nunca deben exponerse a internet sin
  autenticación ni almacenar credenciales en el repositorio. Spring Cloud Gateway
  permanece como componente opcional y no sustituye el registro interno.

### Nombres canónicos de servicio (`spring.application.name`)

Cada cliente Eureka debe configurarse obligatoriamente con su identificador exacto:

| Servicio | Nombre exacto (`spring.application.name`) | Puerto HTTP | Tarea base |
|---|---|:---:|:---:|
| **CRM** | `crm-service` | `8080` | C01 |
| **Comercial** | `comercial-service` | `8081` | S01 |
| **Agenda** | `agenda-service` | `8082` | A01 |

### Convención de URLs de registro

- **Desarrollo local:** `http://localhost:8761/eureka/`
- **Docker Compose:** `http://discovery:8761/eureka/` (resolución mediante DNS del
  servicio Compose `discovery`; nunca utilizar `localhost` para comunicación
  entre contenedores).

### Perfiles de ejecución y avance desacoplado

- **Perfil local / pruebas aisladas:** Las suites de pruebas unitarias y el
  desarrollo aislado de cada servicio deben desactivar el cliente Eureka
  (`eureka.client.enabled=false`) o usar mocks locales. Esto garantiza que C01,
  S01 y A01 avancen sin estar bloqueados por el levantamiento de C14.
- **Perfil integrado / Compose:** El cliente Eureka es de registro obligatorio.
  Las tareas de integración S15, A15 y C15 validan el registro real en el
  servidor discovery.

### Consumo inter-servicio con OpenFeign y LoadBalancer (S04)

- Comercial consume CRM declarando el cliente Feign por nombre lógico:
  `@FeignClient(name = "crm-service")` asistido por Spring Cloud LoadBalancer.
- En el perfil integrado **se prohíbe configurar URLs HTTP fijas** que anulen el
  mecanismo de descubrimiento.
- Las pruebas aisladas de Comercial utilizan WireMock o sobreescritura de URL
  únicamente en perfil de test; WireMock no constituye evidencia de descubrimiento
  real en Compose.
- **Resiliencia ante caídas:** Eureka conserva leases temporales y cachés locales,
  por lo que no detecta caídas de servicio de forma instantánea. No se debe prometer
  failover inmediato. La protección contra caídas y latencias reside en los
  timeouts estrictos y el Circuit Breaker de Resilience4j en Comercial (S04),
  devolviendo HTTP 503 limpio ante indisponibilidad de CRM.

---

<a id="servicio-soap-getlead"></a>
## 9. Servicio SOAP GetLead y pruebas SoapUI (C13, Obligatorio)

Para cumplir con los requerimientos curriculares de interoperabilidad basada en
mensajería XML sobre HTTP, el servicio CRM expone un endpoint SOAP 1.1 formal con
enfoque contract-first utilizando Spring Web Services (`spring-boot-starter-web-services`).

### Alcance del servicio SOAP

El servicio SOAP se limita a la **consulta de leads (`GetLead`)**. La ingesta,
creación y modificación de leads permanecen exclusivamente en la API REST de CRM
para evitar duplicidades operativas innecesarias.

### Definición del contrato WSDL y XML Schema

- **Endpoint SOAP:** `POST /ws/leads`
- **WSDL publicado:** `GET /ws/leads.wsdl` (disponible localmente de forma explícita).
- **Target Namespace (TNS):** `urn:urleads:crm:leads:v1`
- **SOAPAction HTTP Header:** `urn:urleads:crm:leads:v1:GetLead`
- **Ubicación de artefactos:** El contrato XSD reside en
  `backend/src/main/resources/xsd/leads-v1.xsd` y la definición WSDL se expone
  dinámicamente en `/ws/leads.wsdl` mediante `DefaultWsdl11Definition`.

#### Estructura de elementos XML

- `GetLeadRequest`:
  - `leadId` (`xs:long`, obligatorio, entero positivo).
- `GetLeadResponse`:
  - `id` (`xs:long`, obligatorio).
  - `stage` (`xs:string`, enum: `NUEVO`, `EN_SEGUIMIENTO`, `CERRADO`).
  - `nextFollowUp` (`xs:date`, formato `YYYY-MM-DD`, opcional).

#### Muestra XML de solicitud (`GetLeadRequest`)

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:v1="urn:urleads:crm:leads:v1">
   <soapenv:Header/>
   <soapenv:Body>
      <v1:GetLeadRequest>
         <v1:leadId>105</v1:leadId>
      </v1:GetLeadRequest>
   </soapenv:Body>
</soapenv:Envelope>
```

#### Muestra XML de respuesta exitosa (`GetLeadResponse`)

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:v1="urn:urleads:crm:leads:v1">
   <soapenv:Header/>
   <soapenv:Body>
      <v1:GetLeadResponse>
         <v1:id>105</v1:id>
         <v1:stage>EN_SEGUIMIENTO</v1:stage>
         <v1:nextFollowUp>2026-10-25</v1:nextFollowUp>
      </v1:GetLeadResponse>
   </soapenv:Body>
</soapenv:Envelope>
```

### Autenticación y seguridad de la interfaz SOAP

1. **Autenticación HTTP Bearer JWT:**
   - La autenticación se realiza mediante la cabecera HTTP estándar:
     `Authorization: Bearer <token>`.
   - Se validan los mismos claims estrictos del token RS256: emisor `urleads-crm`,
     audiencia `crm-api`, scope `crm.leads.read` y sujeto `sub` como identificador
     decimal del usuario propietario.
   - **Prohibiciones estrictas:** No se permite autenticación básica (Basic Auth),
     tokens transmitidos dentro del cuerpo XML ni tokens en parámetros query de la URL.
   - El endpoint delega la consulta en el servicio de aplicación y lookup de
     propietario implementado en C03 sin realizar invocaciones HTTP contra sí mismo.

2. **Protección del parser XML:**
   - Se debe deshabilitar de forma explícita el procesamiento de entidades externas
     (XXE) y declaraciones DTD externas en el parser XML.
   - Se aplica validación estricta del esquema XSD sobre las solicitudes recibidas y
     límite máximo de 16 KB en el tamaño de carga XML mediante `SoapSizeLimitingFilter`
     a nivel HTTP antes del análisis XML para prevenir ataques de denegación de
     servicio (XML Bomb / Billion Laughs).

3. **Privacidad y ausencia de colisión de rutas:**
   - La respuesta XML **jamás debe incluir datos personales** (nombres, teléfonos,
     correos) ni notas de la bitácora interna.
   - La ruta `/ws/leads` del CRM es completamente independiente de `/ws/notifications`
     (WebSocket RFC 6455 en el servicio Agenda).

### Modelo de errores en dos niveles

Los errores se gestionan en dos capas claramente diferenciadas:

1. **Nivel HTTP / Filtro de seguridad (previo a Spring-WS):**
   - Token Bearer ausente o con firma/expiración inválida: **401 Unauthorized** (cabecera HTTP estándar).
   - Token válido pero sin scope `crm.leads.read`: **403 Forbidden** (cabecera HTTP estándar).
   - *(Estos errores son interceptados por el filtro de seguridad de Spring Security antes de alcanzar el MessageDispatcherServlet de Spring-WS).*

2. **Nivel SOAP / Spring Web Services (dentro del endpoint):**
   - Carga XML mal formada o que no cumple el esquema XSD: **SOAP Fault `Client` (`soap:Client`)**.
   - Lead inexistente o perteneciente a otro usuario: **SOAP Fault con código `LEAD_NOT_FOUND`**.
     Para cumplir la especificación SOAP 1.1, la respuesta viaja con código HTTP 500
     y el sobre `<soap:Fault>`. No se debe distinguir entre lead inexistente o ajeno
     para impedir la enumeración o fuga de datos.

#### Muestra XML de error (`SOAP Fault`)

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
   <soapenv:Body>
      <soapenv:Fault>
         <faultcode>soapenv:Client</faultcode>
         <faultstring>LEAD_NOT_FOUND</faultstring>
         <detail>
            <message>El lead solicitado no existe o no pertenece al usuario autenticado.</message>
         </detail>
      </soapenv:Fault>
   </soapenv:Body>
</soapenv:Envelope>
```

### Suite de pruebas automatizadas con SoapUI

Como evidencia obligatoria de la tarea C13 y del hito de integración C15, se debe
estructurar un proyecto SoapUI versionado en `backend/soapui/`:

- **Casos de prueba obligatorios:**
  1. Solicitud `GetLead` válida con token y scope correcto (retorna `200 OK` con `GetLeadResponse`).
  2. XML mal formado o no conforme al esquema XSD (retorna `SOAP Fault Client`).
  3. Petición sin cabecera `Authorization` o con token inválido (retorna `HTTP 401`).
  4. Petición con token carente del scope `crm.leads.read` (retorna `HTTP 403`).
  5. Lead inexistente y lead de otro propietario (ambos retornan el mismo fault `LEAD_NOT_FOUND` de forma indistinguible).
  6. Carga XML con vector de ataque XXE / DTD externa (rechazo inmediato y seguro).
- **Parametrización:** Las propiedades de URL de endpoint y token JWT se configuran
  como variables de entorno o parámetros del proyecto SoapUI, sin incluir credenciales
  ni secretos reales en los archivos exportados.
- Las colecciones Postman o Swagger no sustituyen la evidencia de ejecución en SoapUI.
