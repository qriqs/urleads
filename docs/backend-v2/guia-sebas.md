# Guía de desarrollo: Servicio Comercial (Sebas)

Esta guía describe el entorno de trabajo, comandos rápidos y el índice de tareas
asignadas a Sebas para el microservicio Comercial en UrLeads Backend V2.

---

## 1. Información general del servicio

- **Responsable:** Sebas
- **Directorio de trabajo:** `services/comercial/`
- **Rama Git:** `feat/comercial`
- **Puerto HTTP local:** `8081`
- **Base de datos:** PostgreSQL local (base `urleads_comercial` o esquema `comercial`)
- **Contratos compartidos:** Consulta [docs/backend-v2/contratos.md](contratos.md)
- **Flujo de ramas:** Consulta [docs/backend-v2/flujo-ramas.md](flujo-ramas.md)

---

## 2. Inicio rápido y comandos locales

```bash
# Cambiar a la rama de trabajo
git switch feat/comercial

# Navegar a la carpeta del microservicio Comercial
cd services/comercial

# Ejecutar las pruebas automatizadas del servicio
./mvnw clean test

# Arrancar el servicio Comercial (puerto 8081)
./mvnw spring-boot:run
```

---

## 3. Matriz de tareas en Linear (15 tareas)

| Código | Tarea | Bloque | Estado | Depende de | Linear |
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
| **S14** | Swagger UI, Postman y suite de pruebas de Comercial | Integración | `Todo` | S01, S05, S06 | [DSW-69](https://linear.app/enmanuelprojects/issue/DSW-69) |
| **S15** | Integración real con CRM, RabbitMQ y Agenda | Integración | `Todo` | S05, S14, C03, C04 | [DSW-70](https://linear.app/enmanuelprojects/issue/DSW-70) |

---

## 4. Pautas clave de implementación

1. **Aislamiento del proyecto:** Trabaja exclusivamente dentro de
   `services/comercial/`. No modifiques `backend/` ni los scripts de build raíz
   del repositorio.
2. **Cálculos monetarios (S03):** Utiliza siempre `BigDecimal` con escala 2 y
   `RoundingMode.HALF_UP` para precios, subtotales y totales. Define siempre la
   moneda ISO 4217 (`PEN`, `USD`) y no mezcles monedas en una misma cotización.
3. **Consumo de CRM con Feign y WireMock (S04):** No necesitas que el CRM de
   Cris esté encendido para avanzar. En tus pruebas unitarias y de integración, usa
   `WireMock` para simular las respuestas de `GET /internal/v1/leads/{id}`.
4. **Publicación y outbox (S05):** La aceptación de la cotización y la inserción
   en la tabla `outbox` deben ocurrir en la misma transacción de base de datos.
   El payload del evento hacia RabbitMQ nunca debe incluir PII.
