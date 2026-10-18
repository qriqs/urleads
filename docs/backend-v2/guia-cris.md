# Guía de desarrollo: Servicio CRM (Cris)

Esta guía describe el entorno de trabajo, comandos verificados y el índice de
tareas asignadas a Cris para el microservicio CRM en UrLeads Backend V2.

> **Estado de las tareas:** Los estados indicados en esta guía corresponden a la
> **fotografía inicial** de planificación. El estado dinámico y la asignación en
> vivo se gestionan exclusivamente en [Linear](https://linear.app/enmanuelprojects/project/urleads-b3c2085df458).
> Ninguna de estas tareas se encuentra implementada en la fase de preparación.

---

## 1. Información general del servicio

- **Responsable:** Cris
- **Directorio de trabajo principal:** `backend/` (además de `infra/`, `scripts/` y `.github/` para integración C14/C15)
- **Rama Git:** `feat/crm`
- **Puerto HTTP local:** `8080`
- **Base de datos:** PostgreSQL local (base `urleads` o esquema `crm`, y base aislada `urleads_test` para suites de integración)
- **Contratos compartidos:** Consulta [docs/backend-v2/contratos.md](contratos.md)
- **Flujo de ramas:** Consulta [docs/backend-v2/flujo-ramas.md](flujo-ramas.md)

---

## 2. Comandos locales y prerrequisitos verificados

El servicio CRM reutiliza el backend monolítico modular existente en `backend/`.
Para ejecutar pruebas y desarrollo local se deben seguir los prerrequisitos
documentados en `README.md` e `infra/README.md`:

```bash
# 1. Cambiar a la rama de trabajo
git fetch origin
git switch feat/crm

# 2. Configurar y levantar la base de datos PostgreSQL local
# Requiere que exista infra/.env (copiar de infra/.env.example y configurar POSTGRES_PASSWORD)
cd infra && docker compose up -d --wait && cd ..

# 3. Ejecución de pruebas automatizadas
# Nota: La verificación completa que incluye controladores web integrados con la SPA
# requiere compilar previamente el frontend o usar el script verificado del repositorio:
./scripts/build.sh -Pdb-integration

# Para pruebas unitarias rápidas del backend sin base de datos ni assets de frontend:
cd backend
./mvnw test -Dtest=LeadServiceImplTest,DashboardServiceTest,SecurityCurrentUserProviderTest
```

---

## 3. Matriz de tareas en Linear (15 tareas planificadas)

| Código | Tarea | Bloque | Estado inicial | Depende de | Linear |
|:---:|---|:---:|:---:|:---:|:---:|
| **C01** | Preparar CRM independiente y auditar etapas | Principal | `Todo` | - | [DSW-41](https://linear.app/enmanuelprojects/issue/DSW-41) |
| **C02** | Identidad estándar y validación de tokens JWT | Principal | `Todo` | C01 | [DSW-42](https://linear.app/enmanuelprojects/issue/DSW-42) |
| **C03** | Endpoint interno de consulta de lead para Comercial | Principal | `Todo` | C01, C02 | [DSW-43](https://linear.app/enmanuelprojects/issue/DSW-43) |
| **C04** | Publicación fiable de eventos de dominio CRM con outbox | Principal | `Todo` | C01 | [DSW-44](https://linear.app/enmanuelprojects/issue/DSW-44) |
| **C05** | Documentación OpenAPI, Postman y regresión de seguridad CRM | Principal | `Todo` | C01, C02, C03 | [DSW-45](https://linear.app/enmanuelprojects/issue/DSW-45) |
| **C06** | Detección de duplicados por correo y teléfono normalizados | Ampliación | `Backlog` | C01 | [DSW-46](https://linear.app/enmanuelprojects/issue/DSW-46) |
| **C07** | Campos de prioridad y origen con filtros avanzados | Ampliación | `Backlog` | C01 | [DSW-47](https://linear.app/enmanuelprojects/issue/DSW-47) |
| **C08** | Gestión de etiquetas y relaciones por propietario | Ampliación | `Backlog` | C01 | [DSW-48](https://linear.app/enmanuelprojects/issue/DSW-48) |
| **C09** | Historial de cambios y auditoría independiente de notas | Ampliación | `Backlog` | C01 | [DSW-49](https://linear.app/enmanuelprojects/issue/DSW-49) |
| **C10** | Importación acotada de leads desde Excel con plantilla fija | Ampliación | `Backlog` | C01, C06 | [DSW-50](https://linear.app/enmanuelprojects/issue/DSW-50) |
| **C11** | Registro y consulta de historial de importaciones | Ampliación | `Backlog` | C01, C10 | [DSW-51](https://linear.app/enmanuelprojects/issue/DSW-51) |
| **C12** | Flujo de archivar y restaurar leads sin borrado físico | Ampliación | `Backlog` | C01, C03 | [DSW-52](https://linear.app/enmanuelprojects/issue/DSW-52) |
| **C13** | Servicio SOAP/XML de consulta de lead y pruebas con SoapUI | Principal | `Todo` | C02, C03 | [DSW-53](https://linear.app/enmanuelprojects/issue/DSW-53) |
| **C14** | Configuración de Docker Compose y pipeline de integración local | Integración | `Todo` | C01, S01, A01 | [DSW-54](https://linear.app/enmanuelprojects/issue/DSW-54) |
| **C15** | Verificación integral end-to-end, resiliencia y regresión | Integración | `Todo` | C04, C05, C13, C14, S15, A15 | [DSW-55](https://linear.app/enmanuelprojects/issue/DSW-55) |

---

## 4. Pautas clave de implementación

1. **Reutilización, auditoría y cliente Eureka (C01):** Reutiliza la base de código
   probada en `backend/`. Audita y unifica las etapas (`NUEVO`, `EN_SEGUIMIENTO`, `CERRADO`).
   Configura Eureka Client con el nombre canónico exacto `crm-service` para el
   perfil integrado (`http://discovery:8761/eureka/`); mantenlo deshabilitado en
   pruebas unitarias y perfil local (`eureka.client.enabled=false`) para avanzar sin
   depender de que el servidor esté levantado.
2. **Emisión de tokens en tiempo de ejecución (C02):** Implementa el endpoint
   `POST /api/auth/access-token` sobre la sesión autenticada con CSRF. Firma con
   RS256 usando la clave privada de CRM. Emite claims: `iss: "urleads-crm"`,
   `sub: "<usuario.id>"`, `aud: ["crm-api", "comercial-api", "agenda-api"]` y
   scopes correspondientes.
3. **Endpoint interno sin PII (C03):** Exponer `GET /internal/v1/leads/{id}` con
   audiencia `crm-api` y scope `crm.leads.read`. Retorna `{ id, stage, nextFollowUp }`.
   Retorna 401 si el token es inválido o de audiencia ajena, 403 si falta scope y
   404 si el lead no existe o pertenece a otro usuario.
4. **Eventos CRM (C04):** Publica eventos `lead.created.v1`, `lead.updated.v1`,
   `lead.deleted.v1` hacia `urleads.events.v1` con outbox y publisher confirms.
   El payload mínimo es `{ leadId, stage, nextFollowUp }` (o `{ leadId }` al eliminar).
5. **Servidor Eureka e integración Compose (C14):** Estructurado en dos hitos
   claros: 1) Hito temprano de implementación del servidor independiente Spring
   Cloud Netflix Eureka Server (`discovery-service`, puerto 8761, sin base de datos)
   en `infra/discovery/` con su Dockerfile; 2) Hito final con `docker-compose.yml`
   raíz integrando los tres microservicios reales (`crm-service:8080`,
   `comercial-service:8081`, `agenda-service:8082`), Eureka (`discovery:8761`) y
   RabbitMQ (`rabbitmq:5672`). C14 depende de C01, S01 y A01 para su aceptación
   completa con los Dockerfiles de cada servicio, sin generar ciclos.
6. **Servicio SOAP GetLead y suite SoapUI (C13):** Implementa Spring Web Services
   contract-first para la operación `GetLead` en `/ws/leads` con WSDL documentado en
   `/ws/leads.wsdl` (namespace `urn:urleads:crm:leads:v1`). Protegido obligatoriamente
   con cabecera HTTP `Authorization: Bearer <token>` validando RS256, emisor
   `urleads-crm`, audiencia `crm-api` y scope `crm.leads.read`. Reutiliza el servicio
   de consulta por propietario de C03 sin autoconsumo HTTP. Protege el parser contra
   XXE y schemas validados. Mapea 401/403 a nivel HTTP, y SOAP Fault Client o
   `LEAD_NOT_FOUND` a nivel SOAP (HTTP 500). Construye y versiona la suite de
   pruebas parametrizadas de SoapUI en `backend/soapui/`.
7. **Verificación integral, SoapUI y respaldo (C15):** Valida el ecosistema completo
   levantado en Compose. Verifica el flujo comercial con Feign resuelto por Eureka
   (`crm-service`). Ejecuta la suite de pruebas SoapUI contra CRM registrando la
   evidencia obligatoria de los resultados. Comprueba resiliencia ante reinicios y
   caída simulada de CRM (verificando Circuit Breaker y expiración de leases en Eureka).
   Incluye prueba explícita de **restauración de copia de seguridad (backup restore)**
   en una base PostgreSQL aislada (`_test`), demostrando que reiniciar un contenedor
   no sustituye un procedimiento de recuperación ante desastres.
