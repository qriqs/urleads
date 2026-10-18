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
| **C08** | Gestión de etiquetas y relaciones por propietario | Ampliación | `In Progress` | C01 | [DSW-48](https://linear.app/enmanuelprojects/issue/DSW-48) |
| **C09** | Historial de cambios y auditoría independiente de notas | Ampliación | `Backlog` | C01 | [DSW-49](https://linear.app/enmanuelprojects/issue/DSW-49) |
| **C10** | Importación acotada de leads desde Excel con plantilla fija | Ampliación | `Backlog` | C01, C06 | [DSW-50](https://linear.app/enmanuelprojects/issue/DSW-50) |
| **C11** | Registro y consulta de historial de importaciones | Ampliación | `Backlog` | C01, C10 | [DSW-51](https://linear.app/enmanuelprojects/issue/DSW-51) |
| **C12** | Flujo de archivar y restaurar leads sin borrado físico | Ampliación | `Backlog` | C01, C03 | [DSW-52](https://linear.app/enmanuelprojects/issue/DSW-52) |
| **C13** | Servicio SOAP/XML de consulta de lead y pruebas con SoapUI | Principal | `In Progress` | C02, C03 | [DSW-53](https://linear.app/enmanuelprojects/issue/DSW-53) |
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

---

<a id="6-implementacion-y-verificacion-de-c02-identidad-y-tokens-jwt"></a>
## 6. Implementación y verificación de C02 (Identidad y tokens JWT)

Esta sección documenta la arquitectura técnica, configuración de claves,
comportamiento en tiempo de ejecución y resultados de validación de la tarea
C02 (DSW-42) para el servicio CRM.

### 6.1. Arquitectura de seguridad, ciclo de vida y decisiones de diseño

1. **Algoritmo y formato de claves:**
   - Se utiliza criptografía asimétrica **RS256** (RSA con SHA-256) con un tamaño
     mínimo de clave de **2048 bits**. Claves de menor longitud son rechazadas
     al inicio (*fail-fast*).
   - Formatos compatibles en `JwtKeyLoader`: claves privadas en formato **PKCS#8 PEM**
     (`-----BEGIN PRIVATE KEY-----`) y claves públicas en formato **X.509 PEM**
     (`-----BEGIN PUBLIC KEY-----`).
   - El servicio CRM custodia la clave privada y es el único emisor autorizado.

2. **Claims y vigencia de tokens emitidos:**
   - Emisor (`iss`): exacto `urleads-crm` como cadena de texto (sin depender de
     descubrimiento de red ni URLs vivas).
   - Sujeto (`sub`): cadena decimal que representa el `usuario.id` numérico
     persistido en la base de datos (por ejemplo, `"42"`). La identidad se
     obtiene del contexto de sesión en el servidor y nunca de parámetros del cliente.
   - Audiencias (`aud`): lista canónica con los tres microservicios
     `["crm-api", "comercial-api", "agenda-api"]`.
   - Alcances (`scope`): cadena delimitada por espacios
     `"crm.leads.read comercial.access agenda.access"`.
   - Vigencia temporal: tiempo de vida (TTL) de **600 segundos (10 minutos)**
     con tolerancia de reloj (*clock skew*) de hasta **30 segundos** evaluada
     mediante el bean `Clock` inyectado.

3. **Ruta de emisión de tokens (`POST /api/auth/access-token`):**
   - Expuesta por `AccessTokenController` sobre la cadena legada de sesión.
   - Requiere sesión web activa (`JSESSIONID`) y cabecera CSRF válida.
   - No acepta cuerpo ni parámetros de consulta. Un token Bearer presentado en
     este endpoint no autoriza la emisión de nuevos tokens (retorna 401).
   - Retorna HTTP 200 con JSON exacto `{"access_token":"...","token_type":"Bearer","expires_in":600}`
     y cabeceras `Cache-Control: no-store` y `Pragma: no-cache`.
   - No implementa refresh tokens ni revocación distribuida. El cierre de sesión
     (`/logout`) invalida la sesión web; los JWT ya emitidos permanecen vigentes
     hasta su expiración técnica.

4. **Separación de cadenas de seguridad:**
   - **Cadena interna (`@Order(1)`, `/internal/**`):** Coincide con rutas
     inter-servicio. Es estrictamente sin estado (`STATELESS`, `NullSecurityContextRepository`),
     con CSRF desactivado, y requiere autenticación Bearer JWT con el alcance
     `SCOPE_crm.leads.read`. Una sesión web activa sin token o con token inválido
     recibe HTTP 401 sin fallback a la sesión. Si JWT está desactivado, la cadena
     falla de forma cerrada devolviendo HTTP 401.
   - **Cadena legada (`@Order(2)`):** Mantiene la autenticación basada en sesión,
     protección CSRF para mutaciones y control de acceso a `/api/**` y assets SPA.

5. **Resolución de propietario en `SecurityCurrentUserProvider`:**
   - Si la autenticación es una instancia de `JwtAuthenticationToken`, resuelve el
     identificador numérico a partir del claim `sub` validado sin consultar la base
     de datos.
   - Si la autenticación proviene de sesión web, conserva la resolución de
     `username` hacia `usuarioRepository` con retorno 401 si no existe.

### 6.2. Configuración de claves y variables de entorno

La configuración de JWT se encuentra deshabilitada por defecto para permitir que
el CRM arranque de manera autónoma en perfiles de desarrollo o pruebas sin requerir
claves en disco.

Propiedades configurables en `application.properties`:

| Propiedad | Variable de entorno | Valor por defecto | Descripción |
|---|---|:---:|---|
| `urleads.jwt.enabled` | `JWT_ENABLED` | `false` | Habilita componentes criptográficos y emisión JWT. |
| `urleads.jwt.issuer` | - | `urleads-crm` | Identificador de emisor esperado. |
| `urleads.jwt.private-key-location` | `JWT_PRIVATE_KEY_LOCATION` | - | Ruta o recurso de clave privada PKCS#8. |
| `urleads.jwt.public-key-location` | `JWT_PUBLIC_KEY_LOCATION` | - | Ruta o recurso de clave pública X.509. |

Cuando `urleads.jwt.enabled=true`, la ausencia de claves válidas o claves de tamaño
inferior a 2048 bits produce un fallo inmediato al inicio de la aplicación (*fail-fast*)
con una excepción descriptiva sanitizada que no expone secretos.

#### Ejemplo de generación de claves locales con OpenSSL (fuera del repositorio)

Para desarrollo o verificación local, genera un par de claves RSA de 2048 bits
en un directorio privado del usuario con permisos restrictivos:

```bash
# 1. Crear directorio seguro fuera del repositorio
mkdir -p ~/.urleads/keys && chmod 700 ~/.urleads/keys

# 2. Generar clave privada RSA en formato PKCS#8 PEM (2048 bits)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ~/.urleads/keys/crm-private.pem
chmod 600 ~/.urleads/keys/crm-private.pem

# 3. Extraer clave pública en formato X.509 PEM
openssl rsa -pubout -in ~/.urleads/keys/crm-private.pem -out ~/.urleads/keys/crm-public.pem
chmod 644 ~/.urleads/keys/crm-public.pem

# 4. Exportar variables para arranque local (ejemplo)
export JWT_ENABLED=true
export JWT_PRIVATE_KEY_LOCATION="file:${HOME}/.urleads/keys/crm-private.pem"
export JWT_PUBLIC_KEY_LOCATION="file:${HOME}/.urleads/keys/crm-public.pem"
```

### 6.3. Verificación automatizada y matriz de aceptación

La suite de pruebas automatizadas de C02 utiliza principalmente claves RSA sintéticas
de 2048 bits generadas en memoria durante la ejecución de las pruebas mediante
`JwtTestFixtureHelper`. Para la prueba de carga desde archivo en `JwtKeyLoaderTest`,
se crea un subdirectorio temporal aislado con permisos POSIX 0700 y clave privada
0600 (configurable mediante `-Durleads.test.temp-dir`), con eliminación determinista
de los archivos y subdirectorios creados en un bloque `finally`, sin dejar archivos
residuales en disco.

Para evaluar el acceso a `/internal/**` sin anticipar la lógica de negocio de leads
(correspondiente a C03), se implementó la sonda de prueba `C02InternalProbeController`
en `backend/src/test/java/com/urleads/controller/C02InternalProbeController.java` bajo
la ruta `/internal/v1/c02-probe`.

#### Matriz de resultados de aceptación

| ID | Escenario | Prueba(s) Automatizada(s) | Resultado verificado |
|:---:|---|---|:---:|
| **A01** | Login de sesión real, CSRF válido, emisión sin cuerpo | `CrmJwtSecurityWebTest#a01_EmisionConSesionValidaYCsrf_RetornaTokenYClaimsR2` | HTTP 200, DTO exacto `{access_token, token_type, expires_in}`, firma RS256 válida, claims R2 correctos (`sub: "42"`). |
| **A02** | Emisión sin sesión con CSRF | `CrmJwtSecurityWebTest#a02_EmisionSinSesion_Retorna401` | HTTP 401 Unauthorized sin token. |
| **A03** | Sesión activa, CSRF ausente o inválido | `CrmJwtSecurityWebTest#a03_EmisionSinCsrfOInvalido_Retorna403` | HTTP 403 Forbidden en ambos casos. |
| **A04** | Solo Bearer token en endpoint de emisión | `CrmJwtSecurityWebTest#a04_EmisionConBearerSolo_Retorna401` | HTTP 401 Unauthorized; no autoriza re-emisión. |
| **A05** | Parámetros cliente `ownerId`/`sub`/`aud`/`scope` en request | `CrmJwtSecurityWebTest#a05_ParametrosClienteManipulados_NoAfectanClaims` | Ignorados de forma segura; los claims emitidos reflejan solo la sesión del servidor. |
| **A06** | JWT válido en sonda interna `/internal/v1/c02-probe` | `CrmJwtSecurityWebTest#a06_TokenValidoEnSondaInterna_Retorna200JsonYPropietario` | HTTP 200 JSON con `ownerId: 42`; sin interferencia de rutas SPA HTML. |
| **A07** | Sin JWT, malformado, clave RSA ajena o firma alterada | `CrmJwtSecurityWebTest#a07_SinJwtMalformadoClaveAjenaFirmaAlterada_Retorna401` | HTTP 401 Unauthorized en cada caso. |
| **A08** | Emisor/audiencia ajena o ausente; audiencia múltiple con `crm-api` | `CrmJwtSecurityWebTest#a08_EmisorYAudiencia_Validaciones` y `CrmJwtValidatorTest` | HTTP 401 si no coincide; HTTP 200 si incluye `crm-api`. |
| **A09** | Expirado fuera de margen (>30s) o sin `exp`; dentro de margen (<=30s) | `CrmJwtSecurityWebTest#a09_ExpiracionYMargenDeReloj` y `CrmJwtValidatorTest` | HTTP 401 fuera de tolerancia o sin exp; HTTP 200 dentro de tolerancia. |
| **A10** | Sujeto inválido (texto, UUID, cero, negativo, overflow, ausente) | `CrmJwtSecurityWebTest#a10_ValidacionEstrictaDeSujeto` y `CrmJwtValidatorTest` | HTTP 401 en todos los casos negativos; HTTP 200 con cadena decimal positiva. |
| **A11** | JWT válido pero sin `scope` o solo con scopes de otros servicios | `CrmJwtSecurityWebTest#a11_ScopeInsuficiente_Retorna403` | HTTP 403 Forbidden (falla de autorización diferenciada de autenticación). |
| **A12** | Algoritmos `none` o simétrico `HS256` | `CrmJwtSecurityWebTest#a12_AlgoritmosNoneYHs256_Retornan401` | HTTP 401 Unauthorized (rechazo de confusión de algoritmo). |
| **A13** | Token con `nbf` en el futuro fuera de margen de reloj | `CrmJwtSecurityWebTest#a13_NbfEnElFuturo_Retorna401` y `CrmJwtValidatorTest` | HTTP 401 Unauthorized. |
| **A14** | Sesión sin token o sesión con token inválido en sonda interna | `CrmJwtSecurityWebTest#a14_SesionWebEnRutaInterna_SinFallback_Retorna401` | HTTP 401 Unauthorized; sin fallback a la sesión web. |
| **A15** | Sesión difiere de `sub` del JWT; parámetro `?ownerId` en query | `CrmJwtSecurityWebTest#a15_IdentidadDeterminadaExclusivamentePorJwtSub` | Solo el `sub` del JWT validado determina la identidad (retorna 88, no sesión ni query). |
| **A16** | Invocación interna con Bearer | `CrmJwtSecurityWebTest#a16_LlamadaInternaStateless_NoCreaSesion` | No crea ni persiste sesión HTTP/cookie; llamada posterior sin sesión da 401. |
| **A17** | JWT desactivado; claves faltantes o corruptas con JWT activado | `JwtConfigurationTest`, `JwtDisabledSecurityTest` y `JwtKeyLoaderTest` | Con JWT desactivado rutas internas fallan cerradas (401) y emisión da 503; con JWT activado claves erróneas provocan fallo rápido (*fail-fast*) al arranque del contexto Spring. |
| **A18** | Logout después de emisión | `CrmJwtSecurityWebTest#a18_LogoutInvalidaSesionPeroJwtConservaVigenciaTecnica` | Sesión invalidada en `/api/auth/me` (401); JWT emitido previamente continúa siendo técnicamente válido hasta su expiración técnica. |
| **REG1** | Pruebas de `SecurityCurrentUserProvider` | `SecurityCurrentUserProviderTest` (8 pruebas) | 8 pruebas unitarias exitosas (4 de sesión legadas y 4 de JWT añadidas). |
| **REG2** | Pruebas de seguridad web legadas (`AuthWebSecurityTest`) | `AuthWebSecurityTest` (3 pruebas) | 3 pruebas exitosas (CSRF, credenciales vacías, login). |
| **REG3** | Suite completa no-IT (`mvn verify`) | Suite Surefire completa no-IT | 112 pruebas ejecutadas con 0 fallos, 0 errores y 0 omisiones. |
| **DOC1** | Documentación alineada con contratos y código | Inspección cruzada de `guia-cris.md`, `contratos.md` y `api.md` | Verificado sin secretos, con enlaces validados y delimitación precisa. |

#### Comandos de validación ejecutados

```bash
# 1. Configuración de entorno Java 21
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 2. Pruebas focalizadas de seguridad, configuración y JWT (61 pruebas, 0 fallos)
cd backend
./mvnw --batch-mode --no-transfer-progress test \
  -Durleads.test.temp-dir=/private/var/folders/yy/mqdfwy8j0l9gz_rpfjxdcwf00000gn/T/opencode/manager-worker-dsw42-bprwjcuw \
  -Dtest=AuthWebSecurityTest,SecurityCurrentUserProviderTest,EurekaConfigurationTest,JwtIssuanceServiceTest,CrmJwtValidatorTest,JwtKeyLoaderTest,CrmJwtSecurityWebTest,JwtDisabledSecurityTest,JwtConfigurationTest

# 3. Verificación de suite completa no-IT y empaquetado JAR (112 pruebas, 0 fallos)
./mvnw --batch-mode --no-transfer-progress verify \
  -Durleads.test.temp-dir=/private/var/folders/yy/mqdfwy8j0l9gz_rpfjxdcwf00000gn/T/opencode/manager-worker-dsw42-bprwjcuw
cd ..
```

*Delimitación de pruebas no ejecutadas:* La suite de integración con base de datos real
(`*IT`), el servicio SOAP (C13), el endpoint de producción de leads (C03) y la
verificación de registro en vivo con Eureka (C14/C15) corresponden a entregables
posteriores y no fueron ejecutados en esta tarea.

---

<a id="7-implementacion-y-verificacion-de-c03-consulta-interna-de-leads"></a>
## 7. Implementation and verification of C03 (Internal lead lookup)

This section documents the technical implementation, security boundaries,
application service reuse, validation matrix, and candid operational limits
for task C03 (DSW-43) in the CRM service.

### 7.1. Design decisions, endpoint architecture, and privacy

1. **Operative internal endpoint:**
   - Exposed under `GET /internal/v1/leads/{id}` on CRM (port 8080).
   - Constructor-injected REST controller `InternalLeadController` handles
     the HTTP request without accepting client-provided owner parameters.
   - Evaluated under the dedicated `@Order(1)` security filter chain matching
     `/internal/**`.
   - Requires RS256 Bearer JWT with issuer `urleads-crm`, audience `crm-api`,
     and scope `crm.leads.read`.
   - Prerequisite: `urleads.jwt.enabled=true`. If JWT is disabled, the filter
     chain fails closed, returning HTTP 401.

2. **Strict owner scoping at query time:**
   - Identity is resolved strictly from the verified JWT `sub` claim via
     `CurrentUserProvider.getCurrentUserId()`. Client query parameters such as
     `?ownerId=` or `?userId=` are never accepted or parsed.
   - Lookup invokes `LeadRepository.findByIdAndUsuarioId(leadId, usuarioId)`.
     Unscoped database lookups followed by in-memory checks are strictly avoided.
   - Leads belonging to another owner return identical HTTP 404 responses
     (`{"status":404,"code":"NOT_FOUND","message":"Lead no encontrado"}`) as
     nonexistent leads, preventing enumeration and information leakage.

3. **Minimal non-PII response payload:**
   - Dedicated immutable record `InternalLeadResponse` with exactly three
     public JSON properties: `id` (`Long`), `stage` (`String`), and
     `nextFollowUp` (`LocalDate`).
   - Fabricated or sensitive customer fields (name, phone, email, service,
     notes, creation/update timestamps, internal owner references) are never
     exposed.
   - If the lead has no scheduled follow-up (such as in stage `CERRADO` or
     `NUEVO` without follow-up), `nextFollowUp` is explicitly serialized as
     `null`.
   - `LocalDate` values serialize strictly as ISO-8601 `YYYY-MM-DD` strings
     without time components or timezone offsets.

4. **Stateless request execution and content negotiation:**
   - Internal requests are stateless (`STATELESS`, `NullSecurityContextRepository`)
     with CSRF protection disabled only for `/internal/**`.
   - No HTTP session is created and no `Set-Cookie` header is returned. Web
     sessions provide no authorization or fallback.
   - Requests with `Accept: application/json` receive JSON. Requests specifying
     `Accept: text/html` return `406 Not Acceptable`; the controller never serves
     the SPA `index.html`. Non-numeric IDs return `400 Bad Request` without 500 errors.

5. **Reusable application service for SOAP C13:**
   - The lookup logic resides in `LeadService.obtenerInterno(Long leadId, Long usuarioId)`.
   - Annotated with `@Transactional(readOnly = true)`, it maps directly from the
     entity to `InternalLeadResponse` without constructing full legacy DTOs.
   - Future SOAP task C13 directly reuses this application service method,
     avoiding HTTP self-calls against the CRM service.

### 7.2. Dependency and runtime limitations

1. **Archive exclusion boundary (C12 dependency):**
   - The current data model and Flyway schema contain no archive column or flag.
   - Linear task DSW-52 (C12) owns the archive schema migration, archive/restore
     endpoints, active-list filtering, and archived-lead 404 integration test.
   - Closed leads (`CERRADO`) are valid owned leads returning HTTP 200 with
     `nextFollowUp: null`, not archived leads.
   - Archived-lead 404 exclusion is documented as a future C12 integration
     dependency and is not marked as implemented or passed in C03.

2. **Runtime port 8080 verification limitation:**
   - Default CRM server port remains `8080`.
   - In the target environment, pre-existing PID 95948 occupied port 8080 at
     preparation time and must remain preserved.
   - Verification was executed via complete MockMvc application context tests
     without disturbing external processes. Live port 8080 runtime verification
     remains pending until integrated environments are deployed.

### 7.3. Acceptance and verification matrix

| ID | Scenario | Automated Test | Outcome |
|:---:|---|---|:---:|
| **R1** | Valid signed token, subject 42, owned lead 105 | `InternalLeadSecurityWebTest#r1_r2_TokenValido_Retorna200DtoExactoSinPii` | HTTP 200 JSON with exact properties `id`, `stage`, `nextFollowUp`; `id` is a JSON number. |
| **R2** | Non-PII assertion against fabricated data | `InternalLeadSecurityWebTest#r1_r2_TokenValido_Retorna200DtoExactoSinPii` | Root JSON fields strictly limited to `Set.of("id", "stage", "nextFollowUp")`. Zero PII leak. |
| **R3** | Canonical stages and null follow-up | `InternalLeadSecurityWebTest#r3_LeadCerrado_RetornaNextFollowUpNull`, `#r3_LeadNuevoSinSeguimiento_RetornaNextFollowUpNull` | HTTP 200 with explicit JSON `null` for `nextFollowUp` on `CERRADO` and `NUEVO`. |
| **R4** | Existing follow-up date formatting | `InternalLeadSecurityWebTest#r4_FormatoFecha_StrictIsoYyyyMmDd` | Matches `YYYY-MM-DD` regex without time or timezone offset. |
| **R5** | Missing lead and other owner's lead | `InternalLeadSecurityWebTest#r5_LeadInexistenteYOtroPropietario_Retornan404Identico` | Identical HTTP 404 `NOT_FOUND` response; owner-scoped repository lookup verified. |
| **R6** | Missing, malformed, untrusted, or wrong audience token | `InternalLeadSecurityWebTest#r6_TokenInvalidoOIncorrecto_Retorna401SinInvocarRepositorio` | HTTP 401 Unauthorized; repository is never invoked. |
| **R7** | Valid token without required scope | `InternalLeadSecurityWebTest#r7_ScopeInsuficiente_Retorna403SinInvocarRepositorio` | HTTP 403 Forbidden; repository is never invoked. |
| **R8** | Multi-audience token containing `crm-api` | `InternalLeadSecurityWebTest#r8_AudienciaMultipleConCrmApi_Retorna200` | HTTP 200 OK; multi-audience support preserved. |
| **R9** | Wrong issuer, expired (>30s), invalid/overflow subject | `InternalLeadSecurityWebTest#r9_EmisorExpiracionSujetoInvalido_Retornan401` | HTTP 401 Unauthorized before repository query; within 30s skew accepted. |
| **R10** | Session-only or session plus invalid Bearer | `InternalLeadSecurityWebTest#r10_SesionWebSinTokenOConTokenInvalido_Retorna401` | HTTP 401 Unauthorized; no session fallback. |
| **R11** | JWT subject differs from session/query tampering | `InternalLeadSecurityWebTest#r11_SoloJwtSubDeterminaPropietarioEnConsulta` | Query is scoped strictly to JWT `sub` (88), returning 404 despite session/query claiming 42. |
| **R12** | JWT disabled | `JwtDisabledSecurityTest#a17_JwtDesactivado_RutasInternasFallanCerradasCon401` | Fails closed with HTTP 401 Unauthorized. |
| **R13** | Internal Bearer request is stateless | `InternalLeadSecurityWebTest#r13_InvocacionInternaStateless_SinCsrfSinSesion` | No CSRF needed, no `Set-Cookie`, no HTTP session created. |
| **R14** | Content negotiation and Accept header | `InternalLeadSecurityWebTest#r14_AcceptApplicationJson_RetornaJson`, `#r14_AcceptTextHtml_NoSirveSpaHtml` | Returns JSON for `application/json`; returns 406 for `text/html`. Never serves SPA HTML. |
| **R15** | Non-numeric path variable | `InternalLeadSecurityWebTest#rutaInvalida_IdNoNumerico_Retorna400BadRequestSin500` | Returns HTTP 400 Bad Request; no 500 error or stack trace. |
| **REG1** | Unit tests for `LeadService.obtenerInterno` | `LeadServiceImplTest` (4 new tests, 15 total) | All pass; verifies owner scoping, DTO mapping, and exception handling. |
| **REG2** | Full non-IT regression suite | Maven verify command | 131 tests executed with 0 failures, 0 errors, 0 skipped. |
| **DEP1** | Archived-lead 404 check | Implemented in task C12 (DSW-52) | Validated via `InternalLeadSecurityWebTest#c12_LeadArchivado_Retorna404Identico` and `LeadServiceImplTest`. |
| **RUN1** | Live port 8080 smoke test | Deferred due to pre-existing process | Documented limitation; PID 95948 preserved on host. |

### 7.4. Runnable validation commands

Execute the following commands from the `backend/` directory using Java 21:

```bash
# Set Java 21 environment
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 1. Focused test suite for C03 and security regression (60 tests, 0 failures)
./mvnw --batch-mode --no-transfer-progress test \
  -Durleads.test.temp-dir=/private/var/folders/yy/mqdfwy8j0l9gz_rpfjxdcwf00000gn/T/opencode/manager-worker-dsw43-qcrx0vnx \
  -Dtest=InternalLeadSecurityWebTest,LeadServiceImplTest,CrmJwtSecurityWebTest,SecurityCurrentUserProviderTest,JwtDisabledSecurityTest,AuthWebSecurityTest

# 2. Full non-IT regression suite and JAR packaging (131 tests, 0 failures)
./mvnw --batch-mode --no-transfer-progress verify \
  -Durleads.test.temp-dir=/private/var/folders/yy/mqdfwy8j0l9gz_rpfjxdcwf00000gn/T/opencode/manager-worker-dsw43-qcrx0vnx
```

---

## 8. Implementación y verificación de C04 (Transactional Outbox y eventos CRM)

### 8.1. Arquitectura de outbox, sobre canónico y desacoplamiento con RabbitMQ

1. **Patrón Transactional Outbox:**
   - La inserción en la tabla `outbox` se realiza en la misma transacción de base de datos (`@Transactional`) que la mutación del lead (`crear`, `actualizar`, `eliminar`) en `LeadServiceImpl`.
   - Si la transacción del lead falla o se revierte por validación o error de negocio, ningún evento queda en la tabla `outbox` (atomicidad estricta).
   - Las operaciones HTTP de leads (`POST /api/leads`, `PUT /api/leads/{id}`, `DELETE /api/leads/{id}`) nunca publican sincrónicamente ni dependen de la disponibilidad del broker RabbitMQ.

2. **Sobre estándar y privacidad (No-PII):**
   - Intercambio canónico: topic exchange durable `urleads.events.v1`.
   - Routing keys exactas: `lead.created.v1`, `lead.updated.v1`, `lead.deleted.v1`.
   - Sobre de evento: `eventId` (UUID), `eventType`, `version: 1`, `occurredAt` (UTC ISO-8601), `ownerId` (cadena decimal obtenida del propietario autenticado).
   - Data para creación/actualización: `{ "leadId": 105, "stage": "EN_SEGUIMIENTO", "nextFollowUp": "2026-10-28" }`. Si la fecha es nula, se serializa como `null` JSON explícito.
   - Data para eliminación: `{ "leadId": 105 }`.
   - Se excluye estrictamente cualquier dato personal o privado (nombres, correos, teléfonos, notas, servicios).

3. **Publicador asíncrono y fiabilidad:**
   - `OutboxPublisher` ejecuta de manera asíncrona periódica (`@Scheduled(fixedDelayString = "${urleads.outbox.publisher.interval-ms:5000}")`) seleccionando registros pendientes con `FOR UPDATE SKIP LOCKED` para evitar colisiones entre instancias concurrentes.
   - Publicación persistente (`MessageDeliveryMode.PERSISTENT`) con confirmaciones de publicador (*publisher confirms*, `spring.rabbitmq.publisher-confirm-type=correlated`).
   - Tras confirmación positiva (ack), el registro pasa a `status = 'DELIVERED'` con marca de tiempo `sent_at`.
   - En caso de nack, expiración de tiempo de espera, error de conexión o devolución por mensaje no enrutable (`basic.return`), el registro permanece en `PENDING` (o `FAILED` si supera `maxRetries`), registrando `last_error` y programando reintento con backoff exponencial, reutilizando idéntico `eventId` y `payload` para garantizar semántica at-least-once.

### 8.2. Esquema Flyway (V3__create_outbox_table.sql)

Migración versionada `V3__create_outbox_table.sql` con constraints de unicidad para `event_id`, estados permitidos (`PENDING`, `DELIVERED`, `FAILED`), y optimización mediante índices compuestos para búsqueda por estado y tiempo de reintento.

### 8.3. Matriz de aceptación y resultados C04

| ID | Escenario | Resultado esperado | Evidencia |
|---|---|---|---|
| **R1** | Creación, actualización y eliminación exitosa de lead | Evento outbox persistido atómicamente en la misma transacción | `LeadServiceImplOutboxTest`, `OutboxServiceImplTest` |
| **R1b** | Validación fallida o lead no encontrado | Operación aborta; no se encola ningún evento outbox | `LeadServiceImplOutboxTest` |
| **R2** | Estructura del evento y privacidad | Sobre canónico exacto, ownerId string, fecha UTC, data mínima sin PII | `LeadEventSerializationTest` |
| **R3** | RabbitMQ no disponible durante operación de lead | Operación HTTP persiste localmente sin bloqueo por el broker | `LeadServiceImplOutboxTest` |
| **R3b** | Publisher confirm positivo | Mensaje persistente enviado y outbox marcado DELIVERED | `OutboxPublisherTest` |
| **R4** | Confirmación negativa, retorno no enrutable, timeout o caída | Evento retenido para reintento con idéntico eventId y payload; reintentos acotados | `OutboxPublisherTest` |
| **REG1** | Regresión de endpoints y servicios existentes | Suite completa de 152 pruebas unitarias/slice en verde | `mvn verify` exitoso (0 fallas) |

### 8.4. Comandos reproducibles de verificación

```bash
# 1. Pruebas focalizadas de C04 y regresión de LeadServiceImpl (36 pruebas, 0 fallas)
./mvnw --batch-mode --no-transfer-progress test \
  -Dtest=LeadEventSerializationTest,OutboxServiceImplTest,LeadServiceImplOutboxTest,OutboxPublisherTest,LeadServiceImplTest

# 2. Verificación completa del backend y empaquetado JAR (152 pruebas, 0 fallas)
./mvnw --batch-mode --no-transfer-progress verify
```

---

<a id="9-implementacion-y-verificacion-de-c07-campos-de-prioridad-y-origen"></a>
## 9. Implementación y verificación de C07 (Campos de prioridad y origen con filtros avanzados)

Esta sección documenta la arquitectura técnica, migración Flyway V5, normalización de datos,
reglas de validación, filtrado combinado y resultados de verificación de la tarea C07 (DSW-47).

### 9.1. Decisiones de diseño y modelo de datos

1. **Campos y catálogo cerrado:**
   - `priority`: Valores válidos exactos `BAJA`, `MEDIA`, `ALTA`. Valor por defecto: `MEDIA`.
   - `origin`: Valores válidos exactos `WEB`, `REFERIDO`, `MANUAL`. Valor por defecto: `MANUAL`.
   - Persistidos en la entidad `Lead` y expuestos en `CreateLeadRequest`, `UpdateLeadRequest` y `LeadResponse`.

2. **Migración Flyway V5 (`V5__add_priority_and_origin_to_lead.sql`):**
   - Agrega columnas `priority VARCHAR(20) NOT NULL DEFAULT 'MEDIA'` y `origin VARCHAR(20) NOT NULL DEFAULT 'MANUAL'`.
   - Restricciones CHECK `chk_lead_priority` y `chk_lead_origin` a nivel de base de datos.
   - Índices enfocados `idx_lead_priority` e `idx_lead_origin` para optimizar consultas de filtrado.
   - El mecanismo DDL de PostgreSQL (`NOT NULL DEFAULT ...`) garantiza que filas preexistentes adquieran los valores por defecto automáticamente.

3. **Validación y normalización:**
   - Los campos son opcionales en creación y actualización.
   - Entradas no vacías se normalizan de forma insensible a mayúsculas/minúsculas usando `Locale.ROOT` (independiente de la configuración regional de la JVM) y se recortan espacios en blanco antes de persistir.
   - Cadenas en blanco (`""`, `"   "`), valores fuera del catálogo o valores explícitamente nulos suministrados son rechazados con HTTP 400 (`VALIDATION_ERROR`).

4. **Filtrado combinado y aislamiento de propietario:**
   - `GET /api/leads` acepta los parámetros de consulta opcionales `priority` y `origin`.
   - Si se omiten o vienen en blanco, no aplican predicado. Si contienen valores inválidos, responden HTTP 400.
   - Se combinan de forma conjuntiva (`AND`) con los filtros existentes (`stage`, `followUp`, `search`).
   - Aislamiento estricto por propietario garantizado a nivel de consulta SQL/JPQL (`l.usuario.id = :usuarioId`).

5. **Compatibilidad con C04 y contratos existentes:**
   - La estructura de eventos de dominio (`lead.created.v1`, `lead.updated.v1`, `lead.deleted.v1`) y el endpoint interno `GET /internal/v1/leads/{id}` se mantienen intactos y sin PII.

### 9.2. Matriz de aceptación y resultados C07

| ID | Escenario | Resultado esperado | Evidencia |
|---|---|---|---|
| **R1** | Aplicar V5 sobre esquema de BD | Versión 5 aplicada; columnas `priority`/`origin` añadidas con constraints CHECK e índices; defaults en inserción verificados | `DatabaseMigrationIT` (39 pruebas contra `urleads_c07_test`) *(Nota: `DatabaseMigrationIT` valida la cadena de migración completa y constraints; la migración de filas heredadas está garantizada por el DDL `NOT NULL DEFAULT` de PostgreSQL)* |
| **R2** | Creación y actualización con campos válidos | Entradas recortadas y convertidas a mayúsculas con `Locale.ROOT`; omitidos reciben defaults | `LeadServiceImplTest`, `LeadApiIT` |
| **R2-neg** | Prioridad u origen inválido, en blanco o nulo explícito | Rechazo con HTTP 400 / `LeadValidationException`; sin mutación | `LeadServiceImplTest`, `LeadApiIT` |
| **R3** | Filtrado combinado de prioridad y origen con etapa y seguimiento | Intersección conjuntiva correcta en paginación y conteos | `LeadRepositoryIT`, `LeadApiIT` |
| **R4** | Lead coincidente de otro propietario | Excluido de los resultados y de `totalElements` | `LeadRepositoryIT`, `LeadApiIT` |
| **R5** | Compatibilidad C04 y seguridad | Eventos outbox y respuesta interna permanecen sin cambios; regresiones en verde | `LeadServiceImplOutboxTest`, `OutboxServiceImplTest` |
| **R6** | Documentación de API | Catálogo, defaults, normalización `Locale.ROOT` y parámetros documentados en `docs/api.md` | `docs/api.md` |

### 9.3. Comandos de verificación ejecutados

```bash
# 1. Pruebas unitarias focalizadas y de regresión (62 pruebas, 0 fallas)
cd backend
./mvnw --batch-mode --no-transfer-progress test \
  -Dtest=LeadServiceImplTest,LeadControllerTest,LeadServiceImplOutboxTest,OutboxServiceImplTest

# 2. Pruebas de integración con base de datos real aislada urleads_c07_test (74 pruebas, 0 fallas)
TEST_DB_URL="jdbc:postgresql://127.0.0.1:5432/urleads_c07_test" \
TEST_DB_USERNAME="urleads_app" \
TEST_DB_PASSWORD="[PROTECTED]" \
./mvnw --batch-mode --no-transfer-progress test-compile failsafe:integration-test failsafe:verify \
  -Pdb-integration -Dit.test=LeadApiIT,LeadRepositoryIT,DatabaseMigrationIT
```

---

<a id="10-implementacion-y-verificacion-de-c05-documentacion-openapi-postman-y-regresion"></a>
## 10. Implementation and verification of C05 (OpenAPI docs, Postman collection, and security regression)

This section documents the technical implementation, security schemes, password privacy verification, Postman collection, and automated regression suite for task C05 (DSW-45) in the CRM service.

### 10.1. OpenAPI 3 and Swagger UI runtime architecture

1. **Dependency and runtime contract:**
   - Incorporates the human-approved dependency `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0` in `backend/pom.xml`.
   - Generates valid OpenAPI 3 specification at `GET /v3/api-docs` and interactive documentation at `GET /swagger-ui.html` (redirecting to `/swagger-ui/index.html`).
   - Prioritized over client-side SPA routing through `SwaggerUiResourceController` in `OpenApiConfig`, guaranteeing static Swagger UI assets are served without collision with SPA catch-all routes.
   - All documented REST paths reflect operative endpoints:
     - Public: `GET /api/health`, `GET /api/auth/csrf`, `POST /api/auth/login`.
     - Session & CSRF protected: `GET /api/auth/me`, `GET /api/auth/session`, `POST /api/auth/logout`, `GET/POST /api/leads`, `GET/PUT/DELETE /api/leads/{id}`, `GET/POST /api/leads/{id}/notes`, `GET /api/dashboard`.
     - Token issuance: `POST /api/auth/access-token` (requires session and CSRF).
     - Inter-service internal: `GET /internal/v1/leads/{id}` (requires Bearer JWT RS256).
   - No nonexistent or future routes (such as SOAP `/ws/leads`) are claimed or documented.

2. **Security schemes and authentication semantics:**
   - `cookieAuth`: ApiKey in cookie (`JSESSIONID`) representing session authentication for web endpoints.
   - `X-XSRF-TOKEN`: Parameter in header (`in: header`) required on all mutating endpoints (`POST`, `PUT`, `DELETE`). Mutating routes require session cookie `cookieAuth` AND the header parameter `X-XSRF-TOKEN`. `POST /api/auth/login` is unauthenticated (no session cookie) but requires the `X-XSRF-TOKEN` header parameter.
   - `bearerJwt`: HTTP Bearer JWT RS256 with issuer `urleads-crm`, audience `crm-api`, and scope `crm.leads.read` exclusively for `/internal/**`.
   - Swagger documentation endpoints (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) are permitted without authentication, while `/api/**` and `/internal/**` protections remain strictly enforced. Try-it-out does not bypass server-side security.

3. **Strict password privacy (R4):**
   - Verified that no user/auth response or OpenAPI response schema exposes `password`, `passwordHash`, or `password_hash`.
   - Internal entity `Usuario` is never exposed in response DTOs or OpenAPI schemas.
   - `UserResponse` exposes strictly `id` and `username`.
   - Automated test assertions explicitly verify absence of password fields in both OpenAPI schema trees and live authentication responses (`/api/auth/login`, `/api/auth/me`).

4. **Postman collection v2.1 (R3):**
   - Exported and versioned at `backend/postman/urleads-crm.postman_collection.json` with environment `backend/postman/urleads-crm.postman_environment.json` and `backend/postman/README.md`.
   - Validated syntax with `python3 -m json.tool`.
   - 8 folders covering all operative endpoints: Health, Auth (CSRF, Login, Me, Session, Logout), Token Issuance, Leads CRUD & filters, Notes, Dashboard, Internal lookup, and OpenAPI/Swagger.
   - All requests use safe variables (`{{baseUrl}}`, `{{username}}`, `{{password}}`, `{{csrfToken}}`, `{{accessToken}}`, `{{leadId}}`). No real credentials, cookies, tokens, or production URLs.

5. **Security and owner-scoping regression (R5):**
   - Automated tests in `LeadServiceImplTest` (3 dedicated cross-owner tests), `OpenApiSecurityWebTest`, `InternalLeadSecurityWebTest`, and `LeadControllerTest` prove that:
     - Unauthenticated requests receive 401.
     - Internal requests without valid JWT receive 401; without required scope receive 403.
     - Attempted read, update, or deletion of a lead belonging to another owner fails with `LeadNotFoundException` (404), with zero mutation (`never().save()`, `never().delete()`) and strictly owner-scoped queries (`never().findById()`).
     - Lookups for nonexistent leads and leads belonging to another owner return identical 404 responses.
     - Ownership is derived strictly on the server from the authenticated session or verified JWT `sub` claim. Client-supplied owner parameters are ignored.

### 10.2. Acceptance and verification matrix

| ID | Scenario | Automated Test / Check | Outcome |
|:---:|---|---|:---:|
| **R1a** | OpenAPI 3 spec at `/v3/api-docs` | `OpenApiSecurityWebTest#openApiDocsReturnsValidSpecificationWithAllExpectedRoutes` | HTTP 200, valid OpenAPI 3.0.1 JSON containing all 12 operative routes, required `X-XSRF-TOKEN` headers, and accurate security requirements. |
| **R1b** | Swagger UI at `/swagger-ui.html` | `OpenApiSecurityWebTest#swaggerUiIsAccessible` | HTTP 302 redirecting to `/swagger-ui/index.html` which returns HTTP 200 HTML. |
| **R2** | OpenAPI is public without weakening API | `OpenApiSecurityWebTest#swaggerIsPublicWithoutWeakeningApiOrInternalSecurity` | `/v3/api-docs` and Swagger UI return 200; `/api/leads` and `/internal/v1/leads/42` remain protected (401). |
| **R3** | Postman collection v2.1 JSON | `python3 -m json.tool` on collection and environment | Valid JSON; covers all CRM operations with safe variables and non-executable test scripts. |
| **R4** | Password privacy in schemas and responses | `OpenApiSecurityWebTest#passwordPrivacyInOpenApiSchemas`, `#passwordNotExposedInAuthResponses` | No password/hash exposed in schemas or auth responses. |
| **R5** | Owner scoping & internal JWT regression | `LeadServiceImplTest` (3 tests), `OpenApiSecurityWebTest`, `InternalLeadSecurityWebTest` | Identical 404 for missing/other owner; no mutation of foreign records; identity derived strictly from session or JWT `sub`. |
| **REG1** | Full non-IT regression suite | Maven wrapper `test` / `verify` | 140 tests executed with 0 failures, 0 errors, 0 skipped (corrected from 137 in A1 baseline + 3 new cross-owner unit tests). |
| **LIVE1** | Live port 8080 smoke check | Runtime curl on port 8080 | Verified if port 8080 is free; executed and reported truthfully. |

### 10.3. Runnable validation commands

Execute the following commands from the `backend/` directory:

```bash
# 1. Focused test suite for OpenAPI and security regression (6 tests, 0 failures)
./mvnw --batch-mode --no-transfer-progress test -Dtest=OpenApiSecurityWebTest

# 2. Combined security regression suite (39 tests, 0 failures)
./mvnw --batch-mode --no-transfer-progress test -Dtest=OpenApiSecurityWebTest,InternalLeadSecurityWebTest,LeadServiceImplTest

# 3. Full non-IT regression suite (140 tests, 0 failures)
./mvnw --batch-mode --no-transfer-progress verify

# 4. Postman collection JSON validation
python3 -m json.tool postman/urleads-crm.postman_collection.json > /dev/null
python3 -m json.tool postman/urleads-crm.postman_environment.json > /dev/null
## 11. Implementación y verificación de C06 (Detección de duplicados por contacto normalizado)

Esta sección documenta la arquitectura técnica, normalización determinista,
migración Flyway V4, restricciones de base de datos PostgreSQL, manejo seguro de errores
y matriz de validación de la tarea C06 (DSW-46) para el servicio CRM.

### 9.1. Decisiones de diseño y reglas de normalización

1. **Normalización de correo electrónico (R1):**
   - Implementado en `LeadNormalizer.normalizeEmail(String email)`.
   - Recorta espacios en blanco al inicio y al final (`trim()`).
   - Convierte a minúsculas canónicas (`Locale.ROOT`).
   - Valores nulos, cadenas vacías o solo espacios se consideran ausentes (`null`).
   - El almacenamiento crudo en `lead.correo` y la representación API se preservan intactos sin exponer campos internos.

2. **Normalización de número telefónico (R2):**
   - Implementado en `LeadNormalizer.normalizePhone(String phone)`.
   - Extrae estrictamente dígitos ASCII (`'0'` a `'9'`). No impone formatos E.164 ni supuestos de país.
   - Puntuación, espacios, símbolos y dígitos no ASCII (ej. árabes o fullwidth) son descartados.
   - Valores nulos, cadenas vacías, solo espacios o cadenas sin dígitos ASCII (`"---"`, `"+ - ()"`) se consideran ausentes (`null`).

3. **Reglas de detección y alcance por propietario (R3):**
   - En creación (`POST /api/leads`) y actualización (`PUT /api/leads/{id}`), se rechaza una solicitud si su correo normalizado no vacío O su teléfono normalizado no vacío coincide con otro lead perteneciente al mismo propietario autenticado.
   - En actualización, la entidad actual se auto-excluye de la validación (`id != leadId`).
   - Medios de contacto nulos o ausentes no colisionan.
   - Mismos valores de contacto están permitidos entre distintos propietarios.

4. **Restricciones de unicidad en PostgreSQL (R4 / Flyway V4):**
   - Migración `V4__owner_normalized_contact_uniqueness.sql` añade columnas generadas almacenadas:
     `telefono_norm TEXT GENERATED ALWAYS AS (NULLIF(regexp_replace(telefono, '[^0-9]', '', 'g'), '')) STORED`
     `correo_norm TEXT GENERATED ALWAYS AS (NULLIF(lower(btrim(correo, E' \t\n\r\013\f\x01\x02\x03\x04\x05\x06\x07\x08\x0e\x0f\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f')), '')) STORED`
   - Restricciones de tabla únicas:
     `CONSTRAINT uq_lead_owner_telefono_norm UNIQUE (usuario_id, telefono_norm)`
     `CONSTRAINT uq_lead_owner_correo_norm UNIQUE (usuario_id, correo_norm)`
   - La unicidad en PostgreSQL permite múltiples valores NULL para un mismo propietario sin violar la restricción.
   - Colisiones preexistentes provocan un fallo fail-fast en la migración sin reparación ni borrado automático.

5. **Manejo seguro de errores y ausencia de fugas (R5):**
   - Tanto los conflictos detectados a nivel de servicio (`LeadValidationException`) como las violaciones de integridad concurrentes en BD (`DataIntegrityViolationException`) se mapean a HTTP 400 (`VALIDATION_ERROR`).
   - No se filtran trazas, nombres de restricciones SQL, valores crudos ni datos de otros propietarios.

6. **Transaccionalidad y outbox (R6):**
   - La detección de duplicados previene mutaciones en base de datos y la inserción de eventos en la tabla `outbox`. En caso de conflicto concurrente, el rollback transaccional revierte cualquier inserción pendiente.

### 9.2. Matriz de aceptación y resultados C06

| ID | Escenario | Resultado esperado | Evidencia |
|---|---|---|---|
| **R1** | Normalización de correo | Minúsculas, trim, null/blanco ausente | `LeadNormalizerTest` |
| **R2** | Normalización de teléfono | Dígitos ASCII puros, ignora no-dígitos y no-ASCII, ausente si sin dígitos | `LeadNormalizerTest` |
| **R3** | Rechazo de duplicados en create/update | Reitera colisión por correo o teléfono en mismo owner, auto-excluye en update, admite entre owners | `LeadServiceImplTest`, `LeadApiIT` |
| **R4** | Unicidad en PostgreSQL (V4) | Restricciones uq_lead_owner_* en BD, fail-fast ante colisión | `DatabaseMigrationIT`, `LeadRepositoryIT` |
| **R5** | Contrato seguro de error | HTTP 400 VALIDATION_ERROR sin fuga de SQL ni PII | `LeadControllerTest`, `LeadApiIT` |
| **R6** | Integridad transaccional de outbox | Fallo de duplicado nunca persiste evento en outbox | `LeadServiceImplOutboxTest` |
| **R7** | Cobertura integral | Pruebas unitarias, slice y de integración DB en verde | 178 Surefire + 95 Failsafe exitosas |
## 12. Guía de implementación tarea C08: Gestión de etiquetas y relaciones por propietario (DSW-48)

### 9.1. Decisiones arquitectónicas y de diseño

1. **Migración Flyway V6 (`V6__create_tags_and_lead_tags.sql`):**
   - Tabla `tag`: Clave primaria autoincremental `id`, `usuario_id` con FK a `usuario(id) ON DELETE CASCADE`, `nombre VARCHAR(100)` con restricción `chk_tag_nombre` (no vacío y entre 1 y 100 caracteres tras trim) y `creado_en`.
   - Restricción de unicidad: Índice único `uq_tag_usuario_nombre_lower ON tag (usuario_id, lower(trim(nombre)))` que garantiza unicidad case-insensitive por propietario permitiendo nombres idénticos entre propietarios distintos.
   - Tabla asociativa `lead_tag`: `(lead_id, tag_id)` con FKs en cascada a `lead(id)` y `tag(id)`.
   - Disparador de integridad `trg_check_lead_tag_owner`: Función PL/pgSQL `check_lead_tag_owner()` que verifica que `lead.usuario_id == tag.usuario_id` en cada inserción/actualización de `lead_tag`, impidiendo a nivel de base de datos asociaciones cruzadas entre propietarios distintos.
   - Cascada segura: Eliminar un lead remueve sus notas y sus filas en `lead_tag` pero conserva intactas las etiquetas. Eliminar una etiqueta remueve sus filas en `lead_tag` sin alterar los leads ni sus notas asociadas.

2. **Endpoints CRUD de etiquetas (`/api/tags`):**
   - Rutas: `GET /api/tags`, `GET /api/tags/{id}`, `POST /api/tags`, `PUT /api/tags/{id}`, `DELETE /api/tags/{id}`.
   - Seguridad: Requieren sesión activa y protección CSRF para mutaciones; el `usuario_id` se resuelve exclusivamente desde `CurrentUserProvider` en el servidor, sin aceptar identificadores de usuario en parámetros ni cuerpo.
   - Normalización y códigos de error: Nombres recortados (`trim`); nombres vacíos devuelven `400 Bad Request` (`VALIDATION_ERROR`). Conflictos por nombre duplicado devuelven `409 Conflict` (`CONFLICT`). Etiquetas inexistentes o de otros propietarios devuelven `404 Not Found` (`NOT_FOUND`) sin revelar la existencia del recurso ajeno.

3. **Reemplazo atómico de etiquetas en leads (`PUT /api/leads/{id}/tags`):**
   - Recibe `{"tagIds": [1, 2]}`. Reemplaza el conjunto completo de etiquetas asignadas al lead propio de forma atómica.
   - Lista vacía `[]` desasocia todas las etiquetas. IDs duplicados en la petición no generan filas duplicadas en la base de datos.
   - Si el lead o alguna de las etiquetas indicadas no pertenece al usuario autenticado o no existe, la petición aborta con `404 Not Found` sin aplicar modificaciones parciales.

4. **Filtro de leads por etiquetas (`GET /api/leads?tagIds=1&tagIds=2`):**
   - Parámetro `tagIds` repetible con semántica **OR** (coincide si el lead posee al menos una de las etiquetas indicadas).
   - Consulta eficiente con subconsulta `EXISTS (SELECT 1 FROM l.tags t WHERE t.id IN :tagIds AND t.usuario.id = :usuarioId)` para garantizar que cada lead se liste exactamente una vez sin duplicar filas ni alterar la paginación.
   - Prevención de N+1: La consulta paginada recupera en lote las etiquetas de los leads de la página actual mediante `findAllWithTagsByIdIn(leadIds)`.
   - Etiquetas ajenas o inexistentes en el filtro devuelven 0 coincidencias de forma segura sin revelar información de otros propietarios.

### 9.2. Matriz de aceptación y resultados C08

| ID | Escenario | Resultado esperado | Evidencia |
|---|---|---|---|
| **R1a** | Aplicación de migraciones V1 a V6 en PostgreSQL | Migración exitosa; tablas `tag`, `lead_tag`, índices y disparador creados | `DatabaseMigrationIT` |
| **R1b** | Cascada al eliminar lead con notas y tags | Notas y asociaciones `lead_tag` eliminadas en cascada; etiquetas y usuario conservados | `DatabaseMigrationIT`, `TagApiIT` |
| **R2a** | CRUD y listado de etiquetas por propietario | Cada propietario accede solo a sus etiquetas; mismo nombre permitido entre usuarios | `TagServiceTest`, `TagControllerTest`, `TagRepositoryIT`, `TagApiIT` |
| **R2b** | Normalización y validación de nombres de etiquetas | Trim de espacios, rechazo de blancos (400), rechazo de duplicados case-insensitive (409) | `TagServiceTest`, `TagControllerTest`, `TagApiIT` |
| **R2c** | Acceso a etiqueta ajena (GET/PUT/DELETE) | Retorna 404 indistinguible de inexistente sin mutar datos | `TagServiceTest`, `TagControllerTest`, `TagRepositoryIT`, `TagApiIT` |
| **R3a** | Reemplazo atómico de etiquetas en lead propio | Conjunto reemplazado, `[]` limpia, repetidos no duplican filas | `LeadServiceImplTagsTest`, `LeadControllerTest`, `TagApiIT` |
| **R3b** | Asignación de tag ajeno o en lead ajeno | Retorna 404 atómico sin mutaciones parciales | `LeadServiceImplTagsTest`, `TagApiIT` |
| **R4a** | Filtro de leads con múltiples etiquetas | Semántica OR; sin leads duplicados; orden y paginación estables | `LeadServiceImplTagsTest`, `LeadControllerTest`, `TagApiIT` |
| **R4b** | Filtro con etiquetas ajenas o inexistentes | Retorna 0 coincidencias sin exponer existencia ajena | `TagApiIT` |
| **REG1** | Regresión completa no-IT (`mvn verify`) | 185 pruebas unitarias/slice en verde sin fallas | `mvn verify` exitoso (0 fallas) |
| **REG2** | Seguridad web y CSRF en etiquetas | Sesión obligatoria (401), CSRF requerido en mutaciones (403) | `TagControllerTest`, `TagApiIT` |

### 11.3. Comandos reproducibles de verificación

```bash
# 1. Pruebas unitarias focalizadas C06 (68 pruebas, 0 fallas)
./mvnw --batch-mode --no-transfer-progress test \
  -Dtest=LeadNormalizerTest,LeadServiceImplTest,LeadServiceImplOutboxTest,LeadControllerTest

# 2. Verificación completa del backend no-IT y empaquetado JAR (178 pruebas, 0 fallas)
./mvnw --batch-mode --no-transfer-progress verify

# 3. Verificación con base de datos real aislada (95 pruebas Failsafe, 0 fallas)
TEST_DB_URL="jdbc:postgresql://127.0.0.1:5432/urleads_c06_test" \
TEST_DB_USERNAME="urleads_app" \
TEST_DB_PASSWORD="..." \
./mvnw --batch-mode --no-transfer-progress verify -Pdb-integration

# 1. Pruebas unitarias focalizadas de C08 y regresión de servicios (67 pruebas, 0 fallas)
./mvnw --batch-mode --no-transfer-progress test \
  -Dtest=TagServiceTest,LeadServiceImplTagsTest,LeadServiceImplTest,TagControllerTest,LeadControllerTest

# 2. Verificación estándar completa del backend y empaquetado (185 pruebas, 0 fallas)
./mvnw --batch-mode --no-transfer-progress verify

# 3. Integración con PostgreSQL aislada (perfil db-integration en puerto 5433)
TEST_DB_URL=jdbc:postgresql://127.0.0.1:5433/urleads_c08_test \
TEST_DB_USERNAME=testuser \
TEST_DB_PASSWORD=testpass \
./mvnw --batch-mode --no-transfer-progress verify -Pdb-integration \
  -Dit.test=DatabaseMigrationIT,TagRepositoryIT,TagApiIT

# 4. Regresión de auditoría C09; ejecutar contra una base de datos aislada autorizada
./mvnw --batch-mode --no-transfer-progress verify -Pdb-integration \
  -Dit.test=DatabaseMigrationIT,LeadAuditSchemaTest,LeadApiIT
```

## 13. Implementación y verificación de C09: auditoría de cambios de lead

C09 registra los cambios de campos editables en una tabla independiente de las
notas y expone el historial del lead bajo el aislamiento del propietario.

- La migración Flyway V7 crea `lead_audit_log` con propietario, lead, campo,
  valores anterior/nuevo y timestamp UTC.
- `PUT /api/leads/{id}` persiste deltas en la misma transacción que la edición;
  los cambios sin diferencias no generan auditoría.
- `GET /api/leads/{id}/audit` pagina a tamaño fijo 20 y requiere que el lead
  pertenezca al usuario autenticado. Un lead ajeno o inexistente responde 404.
- La auditoría no contiene notas privadas ni reemplaza el registro conversacional.

La suite estándar `./mvnw --batch-mode --no-transfer-progress verify` valida
las pruebas unitarias y web. Las pruebas de migración y PostgreSQL requieren una
base aislada configurada y autorizada mediante `TEST_DB_URL`, `TEST_DB_USERNAME`
y `TEST_DB_PASSWORD`.

---

## 14. Implementación y verificación de C12: archivo y restauración lógica de leads (DSW-52)

Esta sección documenta la arquitectura técnica, modelo de datos, exclusión activa, operaciones de archivo/restauración y resultados de verificación de la tarea C12 (DSW-52).

### 14.1. Decisiones de diseño y modelo de datos

1. **Columna y migración Flyway V9 (`V9__add_archived_at_to_lead.sql`):**
   - Agrega `archived_at TIMESTAMP WITH TIME ZONE NULL` a la tabla `lead`.
   - Crea el índice compuesto `idx_lead_usuario_archived_at` sobre `(usuario_id, archived_at)`.
   - `NULL` representa un lead activo (valor por defecto para filas heredadas y nuevas inserciones).
   - Un valor `TIMESTAMPTZ` no nulo marca el lead como archivado con marca de tiempo UTC.

2. **Diferenciación de la eliminación física (DELETE):**
   - El archivo lógico es una operación independiente de `DELETE`. No invoca borrado físico ni cascada sobre notas (`nota`), auditoría (`lead_audit_log`), etiquetas (`lead_tag`) ni eventos de outbox históricos.
   - El archivo y la restauración no emiten eventos outbox de dominio (se preserva estrictamente el contrato existente de outbox `created`, `updated`, `deleted`).
   - La operación legacy `DELETE /api/leads/{id}` mantiene inalterada su semántica física de eliminación y emisión de evento outbox.

3. **Exclusión estricta en consultas activas:**
   - Todas las consultas activas filtran explícitamente `l.archivedAt IS NULL`:
     - Detalle `GET /api/leads/{id}` -> 404 Not Found si está archivado.
     - Edición `PUT /api/leads/{id}` -> 404 Not Found si está archivado.
     - Reemplazo de etiquetas `PUT /api/leads/{id}/tags` -> 404 Not Found si está archivado.
     - Bitácora de notas `GET/POST /api/leads/{id}/notes` -> 404 Not Found si está archivado.
     - Historial de auditoría `GET /api/leads/{id}/audit` -> 404 Not Found si está archivado.
     - Eliminación `DELETE /api/leads/{id}` -> 404 Not Found si está archivado.
     - Consulta interna inter-servicios `GET /internal/v1/leads/{id}` -> 404 Not Found indistinguible.
     - Dashboard `GET /api/dashboard` -> métricas totales, distribución por etapa, seguimientos de hoy y atrasados excluyen filas archivadas.
     - Listado y búsqueda activa `GET /api/leads` -> excluye filas archivadas.

4. **Operaciones dedicadas de archivo y restauración:**
   - `POST /api/leads/{id}/archive`: Requiere sesión web activa y cabecera `X-XSRF-TOKEN`. Transiciona un lead propio activo asignando `archived_at = now()`. Responde 200 OK con `LeadResponse` conteniendo `archivedAt`. Si el lead no existe, es ajeno o ya está archivado, responde 404 (semántica de no-existencia).
   - `POST /api/leads/{id}/restore`: Requiere sesión web activa y cabecera `X-XSRF-TOKEN`. Transiciona un lead propio archivado reactivándolo (`archived_at = null`). Responde 200 OK con `LeadResponse` conteniendo `archivedAt: null`. Si el lead no existe, es ajeno o está activo, responde 404.
   - `GET /api/leads/archived`: Retorna listado paginado `PageResponse<LeadResponse>` conteniendo exclusivamente leads archivados del usuario autenticado, ordenados por `createdAt DESC, id DESC`.

5. **Preservación de unicidad de contactos normalizados C06:**
   - Las restricciones de unicidad de `telefono_norm` y `correo_norm` aplican sobre la tabla completa. Los contactos de leads archivados permanecen reservados y restaurar un lead no evade las reglas de unicidad.

### 14.2. Matriz de aceptación y resultados C12

| ID | Escenario | Resultado esperado | Evidencia |
|---|---|---|---|
| **R1** | Migración Flyway V9 | Agrega columna nullable `archived_at` e índice en PostgreSQL | `V9__add_archived_at_to_lead.sql` |
| **R2** | Archivar lead activo propio | Asigna timestamp UTC, responde 200 OK; archivo repetido responde 404 | `LeadControllerTest`, `LeadServiceImplTest` |
| **R3** | Restaurar lead archivado propio | Limpia timestamp (`archived_at = null`), responde 200 OK; restauración repetida responde 404 | `LeadControllerTest`, `LeadServiceImplTest` |
| **R4** | Listado de archivados (`GET /api/leads/archived`) | Retorna exclusivamente archivados del usuario con paginación y orden `createdAt DESC, id DESC` | `LeadControllerTest`, `LeadServiceImplTest` |
| **R5** | Exclusión en consultas y mutaciones estándar | Detalle, edición, notas, auditoría, tags, DELETE y dashboard excluyen archivados (404) | `LeadServiceImplTest`, `NotaServiceTest`, `LeadRepository` |
| **R6** | Exclusión en consulta interna (`GET /internal/v1/leads/{id}`) | Lead archivado responde 404 indistinguible de inexistente o ajeno | `InternalLeadSecurityWebTest#c12_LeadArchivado_Retorna404Identico`, `LeadServiceImplTest` |
| **R7** | Seguridad y CSRF | `POST /archive` y `POST /restore` requieren sesión y CSRF (401/403); `GET /archived` requiere sesión (401); ignora ownerId del cliente | `LeadControllerTest`, `OpenApiSecurityWebTest` |
| **R8** | Sin borrado en cascada ni eventos outbox inventados | Notas, auditoría, tags y outbox persisten; outbox no emite eventos en archive/restore | `LeadServiceImplTest` |
| **R9** | Preservación de unicidad de contactos | Unicidad C06 de correo y teléfono normalizados se mantiene sobre filas archivadas | `LeadRepository`, `LeadServiceImpl` |
## 15. Implementación y verificación de C13 (Servicio SOAP/XML GetLead y suite SoapUI)

Esta sección documenta las decisiones de diseño, dependencias aprobadas,
arquitectura de seguridad, suite SoapUI y validación automatizada de la tarea **C13**.

### 15.1. Decisiones de diseño y contrato SOAP 1.1

Para cumplir los requerimientos curriculares de interoperabilidad basada en XML sobre
HTTP, el servicio CRM expone un endpoint SOAP 1.1 contract-first para la consulta de
leads (`GetLead`). La mutación e ingesta permanecen exclusivamente en la API REST de CRM.

1. **Contrato XML Schema y WSDL:**
   - Contrato XSD: `backend/src/main/resources/xsd/leads-v1.xsd`.
   - Target Namespace (TNS): `urn:urleads:crm:leads:v1`.
   - SOAPAction: `urn:urleads:crm:leads:v1:GetLead`.
   - Endpoint SOAP: `POST /ws/leads`.
   - Contrato WSDL publicado: `GET /ws/leads.wsdl` (generado dinámicamente mediante
     `DefaultWsdl11Definition` y el servlet `MessageDispatcherServlet`).

2. **Estructura mínima de datos sin datos personales (PII):**
   - Solicitud `GetLeadRequest`: contiene únicamente `leadId` (`xs:long`, con restricción
     `minInclusive value="1"`).
   - Respuesta `GetLeadResponse`: contiene únicamente `id` (`xs:long`), `stage`
     (enumeración canónica: `NUEVO`, `EN_SEGUIMIENTO`, `CERRADO`) y `nextFollowUp`
     (`xs:date`, formato `YYYY-MM-DD`, opcional/nillable).
   - No expone nombres, teléfonos, correos, notas privadas ni metadatos de auditoría.

3. **Reutilización directa de servicio de aplicación (C03):**
   - El endpoint delega directamente en `LeadService.obtenerInterno(leadId, usuarioId)`.
   - El identificador del usuario propietario (`usuarioId`) se extrae exclusivamente del
     claim `sub` del token JWT validado criptográficamente por la infraestructura de seguridad.
   - No se realizan llamadas HTTP contra el propio CRM ni se utiliza autenticación por sesión.

4. **Modelo de fallos en dos capas:**
   - **Capa HTTP (pre-dispatch):** Token ausente o inválido retorna `HTTP 401 Unauthorized`
     con cabecera `WWW-Authenticate: Bearer`. Token válido sin scope `crm.leads.read`
     retorna `HTTP 403 Forbidden`.
   - **Capa SOAP (en endpoint):** Lead inexistente o ajeno produce un sobre `SOAP Fault`
     con código `Client`, `faultstring` `LEAD_NOT_FOUND`, código de respuesta `HTTP 500`
     y detalle explicativo. Ambas condiciones son estrictamente indistinguibles para
     evitar la enumeración de leads. Solicitudes con XML mal formado o no conforme a XSD
     retornan un `SOAP Fault Client`.

### 15.2. Dependencias incorporadas y compatibilidad

Conforme a la aprobación explícita de dependencias otorgada por el usuario para C13,
se incorporaron las siguientes dependencias en `backend/pom.xml`, gestionadas por el
parent de Spring Boot 3.3.13 sin versiones huérfanas:

- `org.springframework.boot:spring-boot-starter-web-services` (versión 3.3.13, provee
  `spring-ws-core 4.0.15` y Jakarta XML Web Services).
- `wsdl4j:wsdl4j` (versión 1.6.3, requerida por `DefaultWsdl11Definition` para la
  generación y serialización WSDL).
- `org.springframework.ws:spring-ws-test` (versión 4.0.15, ámbito test, para utilidades
  y verificación de esquemas SOAP).

### 15.3. Arquitectura de seguridad (/ws/**) y protección XML

1. **Cadena de filtros de seguridad dedicada (`wsFilterChain`):**
   - Configurada en `SecurityConfig.java` con orden `@Order(2)` previo a la cadena web de sesión.
   - Sesiones configuradas como `SessionCreationPolicy.STATELESS` y CSRF deshabilitado.
   - `GET /ws/leads.wsdl`, `GET /ws/*.wsdl` y `GET /ws/*.xsd` tienen acceso público (`permitAll()`).
   - `POST /ws/leads` exige token Bearer con `SCOPE_crm.leads.read`.
   - `DefaultBearerTokenResolver` configurado con `setAllowUriQueryParameter(false)` para
     rechazar explícitamente tokens en parámetros de consulta.
   - Se ignoran cookies de sesión web (`JSESSIONID`) y credenciales de autenticación básica.

2. **Límite de tamaño XML (`SoapSizeLimitingFilter`):**
   - Filtro antepuesto a la cadena de seguridad que acota las cargas entrantes a un máximo
     de 16 KB (16,384 bytes) tanto por cabecera `Content-Length` como por flujo acotado
     de lectura (`BoundedServletRequestWrapper`).
   - Cargas sobredimensionadas son rechazadas inmediatamente a nivel HTTP con `413 Payload Too Large`
     antes de alcanzar el analizador XML, mitigando ataques de denegación de servicio (XML Bomb / Billion Laughs).

3. **Endurecimiento explícito del analizador XML (`SecureSaajSoapMessageFactory`):**
   - La fábrica de mensajes SAAJ (`SecureSaajSoapMessageFactory`) antepone un analizador SAX
     estrictamente endurecido antes de la construcción del sobre SOAP, con las siguientes características:
     - `http://apache.org/xml/features/disallow-doctype-decl` = `true` (deshabilita completamente declaraciones DOCTYPE).
     - `http://xml.org/sax/features/external-general-entities` = `false` (deshabilita entidades generales externas).
     - `http://xml.org/sax/features/external-parameter-entities` = `false` (deshabilita entidades de parámetro externas).
     - `http://apache.org/xml/features/nonvalidating/load-external-dtd` = `false` (deshabilita carga de DTD externa).
     - `XMLConstants.FEATURE_SECURE_PROCESSING` = `true`.
     - `XMLConstants.ACCESS_EXTERNAL_DTD` = `""` y `XMLConstants.ACCESS_EXTERNAL_SCHEMA` = `""`.
     - `EntityResolver` configurado para retornar flujos vacíos, impidiendo cualquier resolución de recursos externos.
   - Cualquier intento de inclusión de DOCTYPE o entidades externas falla cerrado inmediatamente lanzando
     `InvalidXmlException` sin resolver recursos locales ni externos y sin invocar la capa de servicio.

### 15.4. Suite de pruebas automatizadas SoapUI

En el directorio `backend/soapui/` se estructuró y versionó el proyecto XML de SoapUI:

- **Proyecto:** `urleads-crm-soapui-project.xml` (suite `GetLeadTestSuite`).
- **Casos de prueba obligatorios:**
  1. `TC01 - Valid GetLead Request` (HTTP 200 OK, validación XSD, respuesta mínima sin PII).
  2. `TC02 - Schema Invalid XML Request` (HTTP 500, SOAP Fault Client).
  3. `TC03 - Missing or Invalid Bearer Token` (HTTP 401 Unauthorized, cabecera Bearer).
  4. `TC04 - Token Lacking Required Scope` (HTTP 403 Forbidden).
  5. `TC05 - Indistinguishable Missing and Foreign Lead` (HTTP 500, SOAP Fault `LEAD_NOT_FOUND`).
  6. `TC06 - XXE and External DTD Injection Rejection` (Rechazo seguro sin fuga de secretos).
- **Parametrización:** Propiedades de URL (`crm_endpoint`) y tokens JWT (`jwt_token_*`)
  parametrizadas sin almacenar credenciales reales ni secretos en control de versiones.
- **Documentación y estado de ejecución:** `backend/soapui/README.md` describe la estructura,
  propiedades y comandos de ejecución. En la fase de validación en tiempo de ejecución (intento A4),
  se ejecutó de forma automatizada mediante el ejecutor `testrunner.sh` de SoapUI 5.7.2 Open Source
  contra la instancia temporal de CRM levantada en `http://127.0.0.1:8113/ws/leads`, validando
  exitosamente los 6 casos de prueba (18 aserciones válidas, 0 fallos).

### 15.5. Matriz de aceptación y resultados C13

| Requisito | Descripción | Prueba automatizada | Resultado |
| :--- | :--- | :--- | :--- |
| **R1** | SOAP 1.1 GetLead contract-first y WSDL en `/ws/leads.wsdl` | `LeadSoapSecurityWebTest.wsdl_PublicAccess_Returns200WithWsdlContent`<br>`LeadSoapEndpointWebTest.getLead_Success_Returns200WithMinimalDto`<br>SoapUI `TC01` | Superado (HTTP 200, WSDL válido, DTO exacto sin PII) |
| **R2** | Autenticación Bearer JWT obligatoria, 401/403 pre-dispatch, sin sesión | `LeadSoapSecurityWebTest`<br>SoapUI `TC03`, `TC04` | Superado (HTTP 401/403 consistentes) |
| **R3** | Reutilización de `LeadService.obtenerInterno` con `sub` validado | `LeadSoapEndpointWebTest.getLead_Success_Returns200WithMinimalDto`<br>SoapUI `TC01` | Superado (invocación verificada con `usuarioId=42`) |
| **R4** | Mapeo de fallos SOAP y paridad estricta inexistente/ajeno | `LeadSoapEndpointWebTest.getLead_NotFound_ReturnsSoapClientFault500`<br>`LeadSoapEndpointWebTest.getLead_ForeignLead_ReturnsIdenticalSoapFault`<br>SoapUI `TC05` | Superado (ambos retornan `LEAD_NOT_FOUND` y HTTP 500) |
| **R5** | Validación XSD, protección XXE explícita y límite de tamaño 16 KB | `LeadSoapSecurityWebTest.post_OversizedPayload_RejectedAtHttpIngressWith413`<br>`LeadSoapEndpointWebTest.getLead_XxeExternalEntityWithSentinelFile_RejectedSafelyAndServiceNeverInvoked`<br>`SecureSaajSoapMessageFactoryTest`<br>SoapUI `TC02`, `TC06` | Superado (413 en sobrecarga, centinelas observables no resueltos, servicio no invocado) |
| **R6** | Proyecto SoapUI versionado y ejecutado con 6 casos obligatorios | `backend/soapui/urleads-crm-soapui-project.xml`<br>`backend/soapui/README.md`<br>Ejecución CLI `testrunner.sh` | Superado (6 casos ejecutados, 0 fallos, 18 aserciones válidas) |
| **R7** | Sin migraciones Flyway ni alteración de esquemas DB | Inspección de migraciones `db/migration/` (V1–V7 intactas) | Superado (0 cambios de esquema) |
| **R8** | Documentación técnica y reporte de tareas | `docs/backend-v2/guia-cris.md`, `contratos.md` | Superado |

### 15.6. Comandos reproducibles de verificación

```bash
# 1. Pruebas unitarias y de integración de la interfaz SOAP, seguridad y parser endurecido (24 pruebas, 0 fallas en Java 21)
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH \
./mvnw --batch-mode --no-transfer-progress test \
  -Dtest=LeadSoapSecurityWebTest,LeadSoapEndpointWebTest,SecureSaajSoapMessageFactoryTest

# 2. Verificación de formato y ausencia de conflictos de estilo Git
git diff --check
```

> **Nota sobre `./mvnw verify` completo:** La ejecución de `mvn verify` sin perfil de base de datos
> ejecuta 294 pruebas unitarias/slice. De ellas, 292 pruebas pasan exitosamente; únicamente 2 pruebas
> en `SpaControllerTest` fallan debido a la ausencia del directorio compilado `frontend/dist` en un entorno
> limpio de backend, condición ajena al alcance del servicio SOAP C13.
