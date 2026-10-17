# Plan de ejecución Backend V2 (45 tareas)

Este documento es el punto de entrada oficial para el desarrollo del Backend V2
de UrLeads. Sustituye la propuesta preliminar de 12 tareas por un plan de
ejecución estructurado de **45 tareas cerradas (15 por integrante)** en tres
microservicios desacoplados: CRM, Comercial y Agenda/Notificaciones.

El catálogo inicial de ideas de ampliación se preserva íntegro en
[docs/ideas-ampliacion.md](ideas-ampliacion.md).

---

## 1. Arquitectura y distribución de servicios

El trabajo se divide en tres microservicios autónomos en Spring Boot con Java 21,
cada uno desarrollado por un integrante en su propia rama y carpeta:

| Servicio | Responsable | Directorio | Rama Git | Puerto | Guía y tareas |
|---|---|---|---|:---:|---|
| **CRM** | Cris | `backend/` | `feat/crm` | `8080` | [Guía de Cris](backend-v2/guia-cris.md) (C01–C15) |
| **Comercial** | Sebas | `services/comercial/` | `feat/comercial` | `8081` | [Guía de Sebas](backend-v2/guia-sebas.md) (S01–S15) |
| **Agenda y Notificaciones** | Alex | `services/agenda/` | `feat/agenda-notificaciones` | `8082` | [Guía de Alex](backend-v2/guia-alex.md) (A01–A15) |

- **Especificación de contratos compartidos:** Consulta [docs/backend-v2/contratos.md](backend-v2/contratos.md).
- **Flujo de ramas y publicación:** Consulta [docs/backend-v2/flujo-ramas.md](backend-v2/flujo-ramas.md).
- **Integración conjunta:** Rama `integration/backend-v2`.

---

## 2. Estructura de las 45 tareas en Linear

Cada integrante tiene asignadas exactamente 15 tareas organizadas en tres bloques:
- **01–05: PRINCIPAL (Alta prioridad, estado `Todo`):** Núcleo funcional básico y mocks de contrato.
- **06–13: AMPLIACIÓN (Prioridad media, estado `Backlog`):** Funcionalidades de valor extendido.
- **14–15: INTEGRACIÓN (Alta prioridad, estado `Todo`):** Pruebas de resiliencia e integración real.

### Servicio CRM (Cris) — `backend/` (`feat/crm`)

| Código | Tarea | Bloque | Estado | Issue Linear |
|:---:|---|:---:|:---:|:---:|
| **C01** | Preparar CRM independiente y auditar etapas | Principal | `Todo` | [DSW-41](https://linear.app/enmanuelprojects/issue/DSW-41) |
| **C02** | Identidad estándar y validación de tokens JWT | Principal | `Todo` | [DSW-42](https://linear.app/enmanuelprojects/issue/DSW-42) |
| **C03** | Endpoint interno de consulta de lead para Comercial | Principal | `Todo` | [DSW-43](https://linear.app/enmanuelprojects/issue/DSW-43) |
| **C04** | Publicación fiable de eventos de dominio CRM con outbox | Principal | `Todo` | [DSW-44](https://linear.app/enmanuelprojects/issue/DSW-44) |
| **C05** | Documentación OpenAPI, Postman y regresión de seguridad CRM | Principal | `Todo` | [DSW-45](https://linear.app/enmanuelprojects/issue/DSW-45) |
| **C06** | Detección de duplicados por correo y teléfono normalizados | Ampliación | `Backlog` | [DSW-46](https://linear.app/enmanuelprojects/issue/DSW-46) |
| **C07** | Campos de prioridad y origen con filtros avanzados | Ampliación | `Backlog` | [DSW-47](https://linear.app/enmanuelprojects/issue/DSW-47) |
| **C08** | Gestión de etiquetas y relaciones por propietario | Ampliación | `Backlog` | [DSW-48](https://linear.app/enmanuelprojects/issue/DSW-48) |
| **C09** | Historial de cambios y auditoría independiente de notas | Ampliación | `Backlog` | [DSW-49](https://linear.app/enmanuelprojects/issue/DSW-49) |
| **C10** | Importación acotada de leads desde Excel con plantilla fija | Ampliación | `Backlog` | [DSW-50](https://linear.app/enmanuelprojects/issue/DSW-50) |
| **C11** | Registro y consulta de historial de importaciones | Ampliación | `Backlog` | [DSW-51](https://linear.app/enmanuelprojects/issue/DSW-51) |
| **C12** | Flujo de archivar y restaurar leads sin borrado físico | Ampliación | `Backlog` | [DSW-52](https://linear.app/enmanuelprojects/issue/DSW-52) |
| **C13** | Servicio SOAP/XML mínimo para consulta e ingesta de leads | Ampliación | `Backlog` | [DSW-53](https://linear.app/enmanuelprojects/issue/DSW-53) |
| **C14** | Configuración de Docker Compose y pipeline de integración local | Integración | `Todo` | [DSW-54](https://linear.app/enmanuelprojects/issue/DSW-54) |
| **C15** | Verificación integral end-to-end, resiliencia y regresión | Integración | `Todo` | [DSW-55](https://linear.app/enmanuelprojects/issue/DSW-55) |

### Servicio Comercial (Sebas) — `services/comercial/` (`feat/comercial`)

| Código | Tarea | Bloque | Estado | Issue Linear |
|:---:|---|:---:|:---:|:---:|
| **S01** | Configuración base de Comercial y migraciones independientes | Principal | `Todo` | [DSW-56](https://linear.app/enmanuelprojects/issue/DSW-56) |
| **S02** | CRUD de catálogo de productos y servicios con moneda | Principal | `Todo` | [DSW-57](https://linear.app/enmanuelprojects/issue/DSW-57) |
| **S03** | Creación de cotizaciones y líneas de detalle con cálculo exacto | Principal | `Todo` | [DSW-58](https://linear.app/enmanuelprojects/issue/DSW-58) |
| **S04** | Cliente OpenFeign resiliente hacia CRM con Circuit Breaker | Principal | `Todo` | [DSW-59](https://linear.app/enmanuelprojects/issue/DSW-59) |
| **S05** | Aceptación atómica de cotización y publicación outbox a RabbitMQ | Principal | `Todo` | [DSW-60](https://linear.app/enmanuelprojects/issue/DSW-60) |
| **S06** | Ciclo de vida y máquina de estados de cotizaciones | Ampliación | `Backlog` | [DSW-61](https://linear.app/enmanuelprojects/issue/DSW-61) |
| **S07** | Instantáneas inmutables de precios y conceptos en cotizaciones | Ampliación | `Backlog` | [DSW-62](https://linear.app/enmanuelprojects/issue/DSW-62) |
| **S08** | Registro de motivo y fecha de resultado comercial | Ampliación | `Backlog` | [DSW-63](https://linear.app/enmanuelprojects/issue/DSW-63) |
| **S09** | Filtros, búsqueda y paginación determinista de cotizaciones | Ampliación | `Backlog` | [DSW-64](https://linear.app/enmanuelprojects/issue/DSW-64) |
| **S10** | Plantillas de cotización reutilizables por usuario | Ampliación | `Backlog` | [DSW-65](https://linear.app/enmanuelprojects/issue/DSW-65) |
| **S11** | Control de versiones y revisiones de cotización | Ampliación | `Backlog` | [DSW-66](https://linear.app/enmanuelprojects/issue/DSW-66) |
| **S12** | Generación idempotente de órdenes a partir de cotización aceptada | Ampliación | `Backlog` | [DSW-67](https://linear.app/enmanuelprojects/issue/DSW-67) |
| **S13** | Métricas y totales comerciales agrupados por moneda | Ampliación | `Backlog` | [DSW-68](https://linear.app/enmanuelprojects/issue/DSW-68) |
| **S14** | Swagger UI, Postman y suite de pruebas de Comercial | Integración | `Todo` | [DSW-69](https://linear.app/enmanuelprojects/issue/DSW-69) |
| **S15** | Integración real con CRM, RabbitMQ y Agenda | Integración | `Todo` | [DSW-70](https://linear.app/enmanuelprojects/issue/DSW-70) |

### Servicio Agenda y Notificaciones (Alex) — `services/agenda/` (`feat/agenda-notificaciones`)

| Código | Tarea | Bloque | Estado | Issue Linear |
|:---:|---|:---:|:---:|:---:|
| **A01** | Configuración base de Agenda y migraciones independientes | Principal | `Todo` | [DSW-71](https://linear.app/enmanuelprojects/issue/DSW-71) |
| **A02** | API REST de notificaciones persistentes y paginadas | Principal | `Todo` | [DSW-72](https://linear.app/enmanuelprojects/issue/DSW-72) |
| **A03** | Consumidor fiable RabbitMQ de cotizaciones aceptadas | Principal | `Todo` | [DSW-73](https://linear.app/enmanuelprojects/issue/DSW-73) |
| **A04** | Transporte WebSocket estándar autenticado para notificaciones | Principal | `Todo` | [DSW-74](https://linear.app/enmanuelprojects/issue/DSW-74) |
| **A05** | Flujo completo de notificación y pruebas de integración interna | Principal | `Todo` | [DSW-75](https://linear.app/enmanuelprojects/issue/DSW-75) |
| **A06** | CRUD de tareas vinculadas a lead autorizado | Ampliación | `Backlog` | [DSW-76](https://linear.app/enmanuelprojects/issue/DSW-76) |
| **A07** | Gestión de citas con control de zona horaria y estados | Ampliación | `Backlog` | [DSW-77](https://linear.app/enmanuelprojects/issue/DSW-77) |
| **A08** | Consultas de agenda acotadas por rango de fechas | Ampliación | `Backlog` | [DSW-78](https://linear.app/enmanuelprojects/issue/DSW-78) |
| **A09** | Programador durable de recordatorios con ocurrencias únicas | Ampliación | `Backlog` | [DSW-79](https://linear.app/enmanuelprojects/issue/DSW-79) |
| **A10** | Acción de posponer (snooze) notificaciones con límites | Ampliación | `Backlog` | [DSW-80](https://linear.app/enmanuelprojects/issue/DSW-80) |
| **A11** | Consulta de resumen diario de pendientes de agenda | Ampliación | `Backlog` | [DSW-81](https://linear.app/enmanuelprojects/issue/DSW-81) |
| **A12** | Registro de minutas y resultados estructurados de citas | Ampliación | `Backlog` | [DSW-82](https://linear.app/enmanuelprojects/issue/DSW-82) |
| **A13** | Preferencias de notificación y horario laboral por usuario | Ampliación | `Backlog` | [DSW-83](https://linear.app/enmanuelprojects/issue/DSW-83) |
| **A14** | Swagger UI, Postman y verificación aislada de WebSocket | Integración | `Todo` | [DSW-84](https://linear.app/enmanuelprojects/issue/DSW-84) |
| **A15** | Integración real con CRM, Comercial y recuperación tras fallos | Integración | `Todo` | [DSW-85](https://linear.app/enmanuelprojects/issue/DSW-85) |

---

## 3. Principios de trabajo asíncrono y fiabilidad

1. **Avanzar sin esperar (Mocks y fixtures):** Cada desarrollador puede avanzar en
   sus tareas principales de forma autónoma. Sebas utiliza WireMock para simular
   la respuesta de CRM en Feign (S04), y Alex utiliza fixtures sintéticos para
   probar el consumidor RabbitMQ (A03) sin requerir que los otros servicios estén
   desplegados en vivo.
2. **Scoping estricto por usuario:** Toda entidad y operación está aislada por el
   `sub` del token JWT autenticado. No se aceptan parámetros de propietario
   arbitrarios desde el cliente.
3. **Persistencia antes de ACK:** Los eventos asíncronos recibidos por RabbitMQ se
   persisten en base de datos antes de confirmar el mensaje al broker.
4. **Respaldo REST para WebSocket:** La entrega en tiempo real por WebSocket es
   un canal de conveniencia en vivo; toda notificación es persistida en base de
   datos y consultable en `GET /api/v1/notifications`.
