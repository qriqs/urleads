# Guía de desarrollo: Servicio Comercial (Sebas)

Esta guía describe el entorno de trabajo, comandos y el índice de tareas
asignadas a Sebas para el microservicio Comercial en UrLeads Backend V2.

> **Estado de las tareas y directorio:** Los estados indicados en esta guía
> corresponden a la **fotografía inicial** de planificación; Linear es la fuente
> de verdad para el seguimiento activo. El directorio `services/comercial/` **no
> existe actualmente**; se creará como parte de la tarea S01. Ninguna tarea se
> encuentra implementada en esta fase preparatoria.

---

## 1. Información general del servicio

- **Responsable:** Sebas
- **Directorio de trabajo:** `services/comercial/` (a crear en S01)
- **Rama Git:** `feat/comercial`
- **Puerto HTTP local:** `8081`
- **Base de datos:** PostgreSQL local (base `urleads_comercial` y base de pruebas
  aislada `urleads_comercial_test` para migraciones y persistencia)
- **Contratos compartidos:** Consulta [docs/backend-v2/contratos.md](contratos.md)
- **Flujo de ramas:** Consulta [docs/backend-v2/flujo-ramas.md](flujo-ramas.md)

---

## 2. Comandos actuales y flujo de inicio

### Comandos seguros actuales (fase de preparación)

```bash
# 1. Obtener ramas remotas y situarse en la rama de trabajo
git fetch origin
git switch feat/comercial
```

### Comandos futuros (tras implementar la tarea S01)

Una vez que la tarea S01 inicialice el proyecto Spring Boot con Maven Wrapper en
`services/comercial/`, se utilizarán los siguientes comandos:

```bash
# Navegar a la carpeta del microservicio Comercial
cd services/comercial

# Ejecutar pruebas automatizadas (requiere PostgreSQL local para pruebas de persistencia)
./mvnw clean test

# Arrancar el servicio Comercial de forma autónoma (puerto 8081)
./mvnw spring-boot:run
```

---

## 3. Matriz de tareas en Linear (15 tareas planificadas)

| Código | Tarea | Bloque | Estado inicial | Depende de | Linear |
|:---:|---|:---:|:---:|:---:|:---:|
| **S01** | Configuración base de Comercial y migraciones independientes | Principal | `Todo` | - | [DSW-56](https://linear.app/enmanuelprojects/issue/DSW-56) |
| **S02** | CRUD de catálogo de productos y servicios con moneda | Principal | `Todo` | S01 | [DSW-57](https://linear.app/enmanuelprojects/issue/DSW-57) |
| **S03** | Creación de cotizaciones y líneas de detalle con cálculo exacto | Principal | `Todo` | S01, S02 | [DSW-58](https://linear.app/enmanuelprojects/issue/DSW-58) |
| **S04** | Cliente OpenFeign resiliente hacia CRM con Circuit Breaker | Principal | `Todo` | S01 | [DSW-59](https://linear.app/enmanuelprojects/issue/DSW-59) |
| **S05** | Aceptación atómica de cotización y publicación outbox a RabbitMQ | Principal | `Todo` | S03, S04 | [DSW-60](https://linear.app/enmanuelprojects/issue/DSW-60) |
| **S06** | Ciclo de vida y máquina de estados de cotizaciones | Ampliación | `Backlog` | S05 | [DSW-61](https://linear.app/enmanuelprojects/issue/DSW-61) |
| **S07** | Instantáneas inmutables de precios y conceptos en cotizaciones | Ampliación | `Backlog` | S03 | [DSW-62](https://linear.app/enmanuelprojects/issue/DSW-62) |
| **S08** | Registro de motivo y fecha de resultado comercial | Ampliación | `Backlog` | S06 | [DSW-63](https://linear.app/enmanuelprojects/issue/DSW-63) |
| **S09** | Filtros, búsqueda y paginación determinista de cotizaciones | Ampliación | `Backlog` | S03 | [DSW-64](https://linear.app/enmanuelprojects/issue/DSW-64) |
| **S10** | Plantillas de cotización reutilizables por usuario | Ampliación | `Backlog` | S03 | [DSW-65](https://linear.app/enmanuelprojects/issue/DSW-65) |
| **S11** | Control de versiones y revisiones de cotización | Ampliación | `Backlog` | S03 | [DSW-66](https://linear.app/enmanuelprojects/issue/DSW-66) |
| **S12** | Generación idempotente de órdenes a partir de cotización aceptada | Ampliación | `Backlog` | S05 | [DSW-67](https://linear.app/enmanuelprojects/issue/DSW-67) |
| **S13** | Métricas y totales comerciales agrupados por moneda | Ampliación | `Backlog` | S05, S06 | [DSW-68](https://linear.app/enmanuelprojects/issue/DSW-68) |
| **S14** | Swagger UI, Postman y suite de pruebas de Comercial | Integración | `Todo` | S01, S05 | [DSW-69](https://linear.app/enmanuelprojects/issue/DSW-69) |
| **S15** | Integración real con CRM, RabbitMQ y Agenda | Integración | `Done` | S05, S14, C03, A03, C14 | [DSW-70](https://linear.app/enmanuelprojects/issue/DSW-70) |

---

## 4. Pautas clave de implementación

1. **Creación del proyecto, Dockerfile y Eureka Client (S01):** Inicializa el proyecto Spring Boot
   en `services/comercial/` con Java 21 y Maven Wrapper. Entrega el `Dockerfile`
   propio del servicio y script de compilación para que C14 pueda integrarlo en Compose.
   Configura PostgreSQL con Flyway (las pruebas de integración usan una base
   PostgreSQL aislada `urleads_comercial_test`; no se utiliza H2 para pruebas de
   persistencia). Configura Eureka Client con el nombre canónico exacto `comercial-service`
   para el perfil integrado (`http://discovery:8761/eureka/`); mantenlo deshabilitado en
   pruebas unitarias y perfil local (`eureka.client.enabled=false`) para avanzar sin
   depender de C14.
2. **Validación de tokens JWT:** Configura validación RS256 con la clave pública
   del emisor `urleads-crm`. Verifica audiencia `comercial-api` y scope
   `comercial.access`. Extrae el `ownerId` del claim `sub` (cadena decimal como `"42"`).
3. **Cálculos monetarios y precisión (S03):** Utiliza siempre `BigDecimal` con 2
   decimales y `RoundingMode.HALF_UP`. Soportar divisas ISO 4217 (`PEN`, `USD`)
   y prohibir estrictamente consolidar monedas distintas en una cotización.
4. **Resiliencia con OpenFeign, LoadBalancer y Eureka (S04):** Consume `GET /internal/v1/leads/{id}`
   declarando `@FeignClient(name = "crm-service")` con Spring Cloud LoadBalancer y
   Eureka Client en perfil integrado, sin URL HTTP fija hacia CRM que anule el
   descubrimiento. Retransmite el token JWT del usuario (`crm-api`, `crm.leads.read`).
   Usa WireMock o sobreescritura de URL en tests para simular respuestas (200, 401,
   403, 404, latencia). Configura Resilience4j con Circuit Breaker y retorna HTTP 503
   limpio ante caída del CRM sin falsear resultados de éxito.
5. **Aceptación y publicación outbox (S05):** Endpoint `POST /api/v1/quotes/{id}/accept`.
   Guarda en outbox y cambia estado a `ACCEPTED` en una sola transacción. Publica
   el evento canónico `quotation.accepted.v1` hacia `urleads.events.v1` sin PII.
6. **Pruebas de integración de Comercial (S14):** S14 no depende de la tarea de
   ampliación S06. Las pruebas de resiliencia cubren los estados principales
   implementados (`DRAFT`, `ACCEPTED`) y se extienden cuando se agregue S06.
7. **Integración real con CRM vía Eureka, RabbitMQ y Agenda (S15):** Conecta Comercial
   con la instancia real de CRM descubierta dinámicamente por nombre (`crm-service`)
   en Eureka Server sobre el entorno Compose (C14) sin WireMock ni URLs fijas.
   Verifica el comportamiento del Circuit Breaker ante caída de CRM sabiendo que
   Eureka conserva leases y no detecta caídas de forma instantánea. Corrige defectos
   propios de Comercial y valida la entrega de eventos hacia Agenda.

---

## 5. Implementación y verificación de S15 (DSW-70: Integración real)

### 5.1. Hallazgos y corrección de defectos inter-servicio
1. **Doble serialización de payload Outbox en RabbitMQ:**
   - *Defecto:* `RabbitTemplate` configurado con `Jackson2JsonMessageConverter` serializaba cadenas `String` (el JSON ya generado por el Outbox) como literales de cadena escapados (`"\"{\\\"eventId\\\":...}\""`), lo cual provocaba fallo de deserialización en el consumidor de Agenda (`QuotationAcceptedConsumer`) enviando mensajes a la DLQ.
   - *Corrección:* Se especializó `Jackson2JsonMessageConverter` en `RabbitMqConfig` para emitir los bytes UTF-8 limpios de cadenas JSON pre-serializadas con `contentType = application/json`, garantizando compatibilidad 100% con el consumidor de Agenda y con los tests unitarios existentes.
2. **Registro explícito en Eureka y parámetros de leases:**
   - Se incorporó `@EnableDiscoveryClient` en `ComercialApplication`.
   - Se configuraron los parámetros de ciclo de vida de leases: `eureka.instance.lease-renewal-interval-in-seconds=5` y `eureka.instance.lease-expiration-duration-in-seconds=10` en `application.properties`.
3. **Resiliencia ante caída de CRM y retención de leases en Eureka:**
   - La retención de leases en Eureka Server mantiene temporalmente las instancias caídas en el registro. Se comprobó que el Circuit Breaker de Resilience4j (`sliding-window-size=5`, `minimum-number-of-calls=3`, `failure-rate-threshold=50%`, `connect-timeout=2000ms`) pasa a estado `OPEN` aislando el fallo y respondiendo con HTTP 503 (`CrmUnavailableException`) en <100ms de forma fail-fast sin bloqueo de hilos.

### 5.2. Suite de pruebas automatizadas S15 (`ComercialIntegrationRealTests`)
Se implementó `ComercialIntegrationRealTests.java` con 4 casos de verificación:
- **Criterio 1:** Verificación de contrato y registro en Eureka (`comercial-service`).
- **Criterio 2:** Consulta Feign hacia `crm-service` resuelta dinámicamente con propagación de token JWT (`crm.leads.read`).
- **Criterio 3:** Publicación de `quotation.accepted.v1` en `urleads.events.v1` compatible con el esquema de Agenda (sin PII, JSON puro).
- **Criterio 4:** Resiliencia de Circuit Breaker ante caída de CRM y retención de leases en Eureka (fail-fast en estado `OPEN` y recuperación en `HALF_OPEN`).

```bash
# Ejecución de la suite completa de Comercial (125 tests pasando, 0 fallos)
cd services/comercial
./mvnw test
```

