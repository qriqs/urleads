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
