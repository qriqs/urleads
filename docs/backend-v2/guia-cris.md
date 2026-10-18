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

---

## 5. Implementación y verificación de C01 (Preparación y auditoría)

Esta sección documenta los hallazgos técnicos, cambios aplicados, estado de
validación y comandos reproducibles para la tarea C01.

### 5.1. Auditoría y unificación de etapas de leads

- **Etapas canónicas:** Se auditó el modelo y la base de datos confirmando que
  las únicas etapas permitidas son `NUEVO`, `EN_SEGUIMIENTO` y `CERRADO`.
- **Restricción de esquema existente:** La migración Flyway
  `V1__initial_schema.sql` restringe mediante la regla
  `chk_lead_cerrado_sin_seguimiento` que los leads en etapa `CERRADO` tengan
  `proximo_seguimiento IS NULL`. No se requirió una nueva migración de base
  de datos porque el esquema ya delimita las etapas canónicas; no se afirma
  que se hayan auditado registros de desarrollo o producción reales fuera del
  esquema.
- **Consultas JPQL en `LeadRepository`:** Se corrigieron los predicados en
  `findSeguimientosHoy` y `findSeguimientosAtrasados`, reemplazando la exclusión
  obsoleta `NOT IN ('GANADO', 'PERDIDO')` por `!= 'CERRADO'`, garantizando la
  exclusión correcta de leads cerrados y preservando condiciones de propietario,
  comparaciones de fecha y ordenamiento.
- **Fixtures y pruebas unitarias:** En `DashboardServiceTest`, se normalizaron
  los fixtures que utilizaban `"CONTACTADO"` y `"EN_PROCESO"` hacia etapas
  canónicas (`NUEVO`, `EN_SEGUIMIENTO`, `CERRADO`).
- **Pruebas negativas de etapas legadas:** En `LeadApiIT`, se preservó el caso
  negativo de `GANADO` y se añadió cobertura explícita para `PERDIDO`,
  verificando que ambas etapas legadas son rechazadas con HTTP 400.
- **Sincronización de nombres de prueba:** Se renombró `AuthControllerTest.java`
  a `AuthControllerIT.java` para alinearse con su clase `AuthControllerIT` y su
  pertenencia a la suite Failsafe (`**/*IT.java`), y `AuthWebValidationTest.java`
  a `AuthWebSecurityTest.java` para reflejar su clase `AuthWebSecurityTest`.

### 5.2. Configuración de Eureka Client, perfiles y estado de validación

- **Dependencia y BOM:** Se incorporó el BOM Spring Cloud `2023.0.6` junto con
  `spring-cloud-starter-netflix-eureka-client` en `backend/pom.xml`.
  *Nota sobre ciclo de vida:* La línea Spring Cloud 2023.0.x se encuentra en EOL,
  pero representa la versión oficialmente compatible con Spring Boot 3.3.13.
- **Identificador de servicio:** `spring.application.name=crm-service`
  configurado en `application.properties`.
- **Puerto configurable:** `server.port=${PORT:8080}` con soporte para la
  variable de entorno `PORT`.
- **Desactivación por defecto y perfil local:** En `application.properties` y
  `application-local.properties`, se define `eureka.client.enabled=false`. El
  servicio arranca de manera autónoma sin contactar ningún servidor discovery.
- **Perfil integrado (`integrated`):** En `application-integrated.properties`,
  se activan el registro y la consulta (`eureka.client.enabled=true`,
  `eureka.client.register-with-eureka=true`, `eureka.client.fetch-registry=true`)
  con URL por defecto `http://discovery:8761/eureka/` y soporte de sobreescritura
  para desarrollo local mediante `${EUREKA_SERVER_URL:http://discovery:8761/eureka/}`.
- **Aislamiento en pruebas automatizadas:** Surefire y Failsafe inyectan
  `eureka.client.enabled=false` a nivel de variables de sistema, evitando
  intentos de conexión durante las suites de test.
- **Validación sin servidor en vivo:** La clase `EurekaConfigurationTest`
  valida la carga de propiedades y el enlace hacia `EurekaClientConfigBean` en
  todos los perfiles sin emitir tráfico de red. La verificación con registro en
  vivo contra el servidor Eureka se realizará en C14/C15, por lo que no ha sido
  ejecutada en esta fase.
- **Estado real de validación de la tarea:**
  - *Pruebas automatizadas:* Verificación ejecutada independientemente con
    `./mvnw --batch-mode --no-transfer-progress verify -Pdb-integration` en
    `backend/` con Java 21 sobre PostgreSQL disposable aislado (`urleads_test`),
    obteniendo 62 pruebas de Surefire y 81 de Failsafe exitosas sin fallos,
    errores ni omisiones.
  - *Smoke test local en runtime:* Ejecutado y validado en el puerto libre
    `8089` confirmando HTTP 200 en `/api/health` (`{"status":"UP"}`), HTTP 401
    en `/api/leads` privado sin autenticación, y arranque correcto contra base
    de datos real sin llamadas a Eureka.
  - *Verificación pendiente en puerto 8080:* La validación de ejecución en el
    puerto 8080 permanece pendiente debido a la presencia de un proceso
    preexistente (PID 95948) que ocupa dicho puerto y que fue preservado sin
    terminar por directivas de seguridad. Por consiguiente, la tarea C01 no se
    declara completamente cerrada ni validada en 8080 hasta gestionar dicho
    proceso.

### 5.3. Comandos reproducibles de verificación

Todos los comandos parten del directorio raíz del repositorio
(`/Users/cristopherarias/Documents/Dev/urleads`):

```bash
# 1. Configuración de entorno Java 21 (desde la raíz del repositorio)
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. Pruebas unitarias y de configuración de Eureka (Surefire)
cd backend
./mvnw test -Dtest=LeadServiceImplTest,DashboardServiceTest,SecurityCurrentUserProviderTest,EurekaConfigurationTest
cd ..

# 3. Verificación completa con base de datos real (Failsafe)
# Requiere PostgreSQL aislado (TEST_DB_*) según infra/README.md
# Configura TEST_DB_PASSWORD en tu entorno privado (sin exponer credenciales en la terminal)
export TEST_DB_URL="jdbc:postgresql://127.0.0.1:5433/urleads_test"
export TEST_DB_USERNAME="urleads_test_user"
# export TEST_DB_PASSWORD="${TEST_DB_PASSWORD}"
./scripts/build.sh -Pdb-integration

# 4. Arranque local aislado (smoke test sobre puerto libre)
# Requiere configurar DB_PASSWORD previamente a través de tu entorno privado (ver infra/README.md)
PORT=8089 \
SPRING_PROFILES_ACTIVE=local \
DB_URL="jdbc:postgresql://127.0.0.1:5432/urleads" \
DB_USERNAME="urleads_app" \
DB_PASSWORD="${DB_PASSWORD}" \
java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar

# 5. Modo integrado con URL Compose por defecto (http://discovery:8761/eureka/)
# Nota: La verificación con registro en vivo contra el servidor Eureka se realizará en C14/C15.
PORT=8089 \
SPRING_PROFILES_ACTIVE=integrated \
DB_URL="jdbc:postgresql://127.0.0.1:5432/urleads" \
DB_USERNAME="urleads_app" \
DB_PASSWORD="${DB_PASSWORD}" \
java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar

# 6. Modo integrado con sobreescritura host-local de URL Eureka
PORT=8089 \
SPRING_PROFILES_ACTIVE=integrated \
EUREKA_SERVER_URL="http://localhost:8761/eureka/" \
DB_URL="jdbc:postgresql://127.0.0.1:5432/urleads" \
DB_USERNAME="urleads_app" \
DB_PASSWORD="${DB_PASSWORD}" \
java -jar backend/target/urleads-backend-0.0.1-SNAPSHOT.jar
```
