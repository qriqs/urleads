# Contrato inicial de la API

La API usa JSON y el prefijo `/api`.

Actualmente, el único endpoint implementado en el backend es la comprobación
pública de salud `GET /api/health` (responde HTTP 200 con `{"status":"UP"}`).
El backend cuenta con la configuración de PostgreSQL 16 y el esquema inicial
versionado mediante Flyway (`V1__initial_schema.sql`). Los endpoints de
autenticación, gestión de leads, bitácora interna y dashboard descritos en este
documento corresponden al contrato acordado del MVP y se implementarán en las
siguientes tareas del backlog. Las rutas de leads, bitácora y dashboard
requerirán sesión. La obtención inicial del token CSRF y el login no requieren
una sesión autenticada previa.

## Autenticación

Endpoints planificados para la autenticación y el control de sesiones:

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/auth/csrf` | Cookie/token CSRF para cliente web o Postman. |
| POST | `/api/auth/login` | Iniciar sesión y establecer cookie. |
| GET | `/api/auth/me` | Obtener usuario actual o 401. |
| POST | `/api/auth/logout` | Invalidar sesión. |
| POST | `/api/auth/access-token` | Emitir token de acceso JWT (Backend V2 C02). Requiere sesión y CSRF. |

Para login, el cliente obtiene primero el token CSRF. Luego envía el token y
credenciales:

```json
{
  "username": "demo",
  "password": "valor-local-no-real"
}
```

El usuario inicial se configurará de manera segura para el entorno; las
credenciales reales no se incluirán en documentación, fixtures ni repositorio.

### Emisión de tokens de acceso (`POST /api/auth/access-token`)

Endpoint de tiempo de ejecución incorporado en Backend V2 (Tarea C02) para emitir
credenciales de acceso inter-servicio protegidas por la sesión web existente:

- **Autenticación requerida:** Sesión web activa (cookie `JSESSIONID`) y cabecera
  CSRF válida (`X-XSRF-TOKEN`). Un token Bearer por sí solo no autoriza este endpoint.
- **Entrada:** No acepta cuerpo ni parámetros de usuario, audiencia o alcance. La
  identidad se resuelve exclusivamente en el servidor a partir del usuario en sesión.
- **Cabeceras de respuesta:** `Cache-Control: no-store` y `Pragma: no-cache`.
- **Respuesta 200 OK:**
```json
{
  "access_token": "<jwt-firmado-rs256>",
  "token_type": "Bearer",
  "expires_in": 600
}
```
- **Códigos de error:**
  - `401 Unauthorized`: Solicitud sin sesión web activa o usuario no encontrado.
  - `403 Forbidden`: Token CSRF ausente o inválido.
  - `503 Service Unavailable`: Servicio de emisión no configurado (`urleads.jwt.enabled=false`).
- **Vigencia y ciclo de vida:** TTL de 600 segundos (10 minutos) firmado con RS256.
  No implementa refresh tokens ni revocación distribuida. El cierre de sesión (`/logout`)
  invalida la sesión web, mientras que los JWT emitidos conservan su vigencia técnica
  remanente hasta su expiración.

## Leads

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/leads` | Lista filtrable y paginada. |
| GET | `/api/leads/{id}` | Detalle de un lead propio. |
| POST | `/api/leads` | Crear lead; responde 201 y representación. |
| PUT | `/api/leads/{id}` | Reemplazar campos editables; responde 200. |
| DELETE | `/api/leads/{id}` | Eliminar lead y notas; responde 204. |
| PUT | `/api/leads/{id}/tags` | Reemplazar conjunto de etiquetas del lead; responde 200. |
| GET | `/api/leads/{id}/audit` | Consultar historial paginado de cambios del lead. |
| GET | `/api/leads/{id}/notes` | Listar entradas internas de bitácora cronológicamente. |
| POST | `/api/leads/{id}/notes` | Registrar entrada interna; responde 201. |
| POST | `/api/leads/{id}/archive` | Archivar lead propio activo; responde 200 y representación con archivedAt. |
| POST | `/api/leads/{id}/restore` | Restaurar lead propio archivado; responde 200 y representación reactivada. |
| GET | `/api/leads/archived` | Listar leads archivados del usuario de forma paginada. |
| GET | `/api/dashboard` | Resumen y pendientes del usuario. |
| POST | `/api/leads/import/preview` | Vista previa de importación desde archivo Excel (.xlsx) con diagnóstico fila por fila; responde 200. |
| POST | `/api/leads/import/confirm` | Confirmación transaccional e idempotente de importación desde Excel; responde 200. |

La lista acepta `search`, `stage`, `followUp`, `priority`, `origin` y `page`; el tamaño de página
será limitado por el servidor. Los valores de `followUp` serán `today`,
`overdue` o `all`. Los valores de `priority` serán `BAJA`, `MEDIA` o `ALTA`
(insensibles a mayúsculas/minúsculas y recortados). Los valores de `origin`
serán `WEB`, `REFERIDO` o `MANUAL` (insensibles a mayúsculas/minúsculas y
recortados). Todos los filtros se combinan de forma conjuntiva (`AND`) y
mantienen aislamiento estricto por el usuario autenticado a nivel de consulta en base de datos.
El parámetro `tagIds` es repetible (por ejemplo, `?tagIds=1&tagIds=2`) y filtra
bajo semántica OR (coincide si el lead posee al menos una de las etiquetas
especificadas pertenecientes al usuario). Todos los filtros se combinan por AND
y mantienen aislamiento estricto por el usuario autenticado a nivel de consulta.

## Ejemplo de lead

```json
{
  "id": 42,
  "name": "María Torres",
  "service": "Consultoría",
  "phone": "+51 900 000 000",
  "email": "maria@example.test",
  "stage": "EN_SEGUIMIENTO",
  "priority": "MEDIA",
  "origin": "MANUAL",
  "nextFollowUp": "2026-10-10",
  "createdAt": "2026-10-06T15:30:00Z",
  "updatedAt": "2026-10-06T15:30:00Z",
  "tags": [
    {
      "id": 1,
      "name": "VIP",
      "createdAt": "2026-10-06T12:00:00Z"
    }
  ]
}
```

Ejemplo de creación/actualización:

```json
{
  "name": "María Torres",
  "service": "Consultoría",
  "phone": "+51 900 000 000",
  "email": "maria@example.test",
  "stage": "NUEVO",
  "priority": "ALTA",
  "origin": "WEB",
  "nextFollowUp": null
}
```
### Campo `service`

Representa el servicio o interés comercial asociado a la oportunidad.

| Propiedad | Valor |
|------------|--------|
| Tipo | string |
| Obligatorio | No |
| Permite `null` | Sí |
| Descripción | Servicio o interés comercial asociado al lead |

Ejemplos válidos:

```json
{
  "service": "Consultoría"
}
```

```json
{
  "service": null
}
```

Cuando el valor sea distinto de `null`, el backend devolverá el mismo valor asociado al lead en las respuestas correspondientes.

### Campo `priority` (Backend V2 C07 / DSW-47)

Representa la prioridad asignada a la oportunidad comercial en el CRM.

| Propiedad | Valor |
|------------|--------|
| Tipo | string (catálogo cerrado) |
| Obligatorio | No (opcional en creación y actualización) |
| Valor por defecto | `MEDIA` |
| Valores permitidos | `BAJA`, `MEDIA`, `ALTA` |
| Normalización | Insensible a mayúsculas/minúsculas de forma independiente de la configuración regional (`Locale.ROOT`) y recorta espacios en blanco |
| Validación | Valores no pertenecientes al catálogo, cadenas en blanco o `null` explícito son rechazados con HTTP 400 (`VALIDATION_ERROR`) |
| Compatibilidad histórica | La columna en base de datos se agrega con `NOT NULL DEFAULT 'MEDIA'`, asegurando que filas preexistentes reciban este valor por defecto a nivel de esquema |

Ejemplos válidos en peticiones POST/PUT:
- `"priority": " alta "` -> persistido y devuelto como `"ALTA"`.
- Omitir el campo -> persistido y devuelto como `"MEDIA"`.

Filtro en `GET /api/leads?priority=alta`:
- Parámetro opcional. Si se omite o viene vacío/en blanco, no aplica predicado.
- Valores no válidos responden HTTP 400.
- Se combina conjuntivamente (`AND`) con los demás filtros activos y restringe estrictamente por el propietario autenticado en la base de datos.

### Campo `origin` (Backend V2 C07 / DSW-47)

Representa el canal o procedencia por la cual ingresó el lead.

| Propiedad | Valor |
|------------|--------|
| Tipo | string (catálogo cerrado) |
| Obligatorio | No (opcional en creación y actualización) |
| Valor por defecto | `MANUAL` |
| Valores permitidos | `WEB`, `REFERIDO`, `MANUAL` |
| Normalización | Insensible a mayúsculas/minúsculas de forma independiente de la configuración regional (`Locale.ROOT`) y recorta espacios en blanco |
| Validación | Valores no pertenecientes al catálogo, cadenas en blanco o `null` explícito son rechazados con HTTP 400 (`VALIDATION_ERROR`) |
| Compatibilidad histórica | La columna en base de datos se agrega con `NOT NULL DEFAULT 'MANUAL'`, asegurando que filas preexistentes reciban este valor por defecto a nivel de esquema |

Ejemplos válidos en peticiones POST/PUT:
- `"origin": "referido"` -> persistido y devuelto como `"REFERIDO"`.
- Omitir el campo -> persistido y devuelto como `"MANUAL"`.

Filtro en `GET /api/leads?origin=referido`:
- Parámetro opcional. Si se omite o viene vacío/en blanco, no aplica predicado.
- Valores no válidos responden HTTP 400.
- Se combina conjuntivamente (`AND`) con los demás filtros activos y restringe estrictamente por el propietario autenticado en la base de datos.

Ejemplo de entrada interna de bitácora (no se envía al cliente):

```json
{
  "content": "Solicitó recibir la propuesta el viernes."
}
```

Cada entrada devuelve su identificador y fecha de registro, además del
contenido. El listado se ordena del más antiguo al más reciente para facilitar
la lectura del progreso; la interfaz puede desplazarse a la entrada más nueva
al agregar una actualización. El contenido se trata siempre como texto plano.

## Etiquetas (Tags - Backend V2 C08)

Endpoints para la administración de etiquetas y su asignación a leads (Tarea C08 / DSW-48):

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/tags` | Listar etiquetas del usuario autenticado ordenadas por nombre ASC. |
| GET | `/api/tags/{id}` | Obtener una etiqueta propia o 404. |
| POST | `/api/tags` | Crear etiqueta propia (201 Created); rechaza duplicados (409 Conflict). |
| PUT | `/api/tags/{id}` | Renombrar etiqueta propia (200 OK); rechaza duplicados (409 Conflict). |
| DELETE | `/api/tags/{id}` | Eliminar etiqueta y sus asociaciones en leads (204 No Content). |
| PUT | `/api/leads/{id}/tags` | Reemplazar atómicamente las etiquetas del lead propio (200 OK). |

### Reglas y contratos de etiquetas

- **Aislamiento por propietario:** Cada etiqueta y relación está estrictamente aislada por el `usuario_id` del usuario en sesión.
- **Normalización de nombre:** Los nombres se recortan (`trim`); nombres vacíos o con solo espacios se rechazan con `400 Bad Request` (`VALIDATION_ERROR`).
- **Unicidad:** La unicidad del nombre es por propietario e insensible a mayúsculas/minúsculas (`case-insensitive`). Propietarios distintos pueden utilizar el mismo nombre. Si se intenta crear o renombrar con un nombre existente para el mismo usuario, responde `409 Conflict` (`CONFLICT`).
- **Asociación de etiquetas:** `PUT /api/leads/{id}/tags` recibe `{"tagIds": [1, 2]}` y reemplaza el conjunto completo de etiquetas asignadas al lead. Una lista vacía `{"tagIds": []}` desasocia todas las etiquetas. IDs duplicados en la petición no generan duplicados en base de datos. Si el lead o alguna de las etiquetas no existe o pertenece a otro propietario, la solicitud se rechaza con `404 Not Found` de forma atómica sin mutación parcial.
- **Filtro de leads por etiquetas:** `GET /api/leads?tagIds=1&tagIds=2` aplica semántica **OR** (retorna leads que tengan al menos una de las etiquetas indicadas pertenecientes al usuario). Etiquetas inexistentes o ajenas en el filtro devuelven 0 coincidencias de forma segura sin revelar la existencia del recurso.

Ejemplo de cuerpo para `POST /api/tags` y `PUT /api/tags/{id}`:
```json
{
  "name": "Cliente VIP"
}
```

Ejemplo de respuesta de etiqueta (`TagResponse`):
```json
{
  "id": 1,
  "name": "Cliente VIP",
  "createdAt": "2026-10-18T10:00:00Z"
}
```

Ejemplo de cuerpo para `PUT /api/leads/{id}/tags`:
```json
{
  "tagIds": [1, 2]
}
```

## Importación de leads desde Excel (Backend V2 C10 / DSW-50)

Endpoints acotados para la carga masiva y validada de leads mediante archivos `.xlsx` bajo plantilla fija de 8 columnas, con diagnóstico detallado fila por fila y confirmación transaccional e idempotente (Tarea C10 / DSW-50):

| Método | Ruta | Contenido | Resultado |
|---|---|---|---|
| POST | `/api/leads/import/preview` | `multipart/form-data` (`file`) | Diagnóstico fila por fila y emisión de `previewId` criptográfico (200 OK). No realiza escrituras en base de datos. |
| POST | `/api/leads/import/confirm` | `multipart/form-data` (`file`, `previewId`) | Confirmación atómica e idempotente (200 OK); reintentos exactos retornan el recibo durable. |

### Contrato de petición y plantilla fija

- **Formato admitido:** Exclusivamente libros en formato `.xlsx` (OpenXML). Se rechaza cualquier otra extensión o archivos corruptos/no-ZIP mediante verificación estricta de bytes mágicos (`PK\003\004`).
- **Límites de carga y envoltura HTTP:**
  - El contenedor de servlet acota la petición multipart a nivel de infraestructura mediante `spring.servlet.multipart.max-file-size=5MB` y `spring.servlet.multipart.max-request-size=6MB` (para albergar holgadamente las partes multipart y parámetros sin buffer desmedido).
  - A nivel de aplicación, se valida de forma exacta el límite de **5 MiB** (5,242,880 bytes) y un tope de **500 filas de datos** (excluyendo cabecera). Archivos que superen estos límites se rechazan con `400 Bad Request` (`VALIDATION_ERROR`).
- **Estructura de la hoja:** Se procesa exclusivamente la primera hoja del libro.
- **Cabeceras obligatorias (orden y nombres exactos en fila 1):**
  1. `nombre`: Obligatorio, longitud 2..120 caracteres.
  2. `telefono`: Opcional, pero se requiere al menos teléfono o correo. Longitud máxima 50. Normalizado a dígitos ASCII.
  3. `correo`: Opcional, pero se requiere al menos teléfono o correo. Longitud máxima 120. Formato de correo electrónico estándar. Normalizado a minúsculas canónicas (`Locale.ROOT`).
  4. `service`: Opcional, longitud máxima 255 caracteres.
  5. `etapa`: Obligatorio. Valores permitidos: `NUEVO`, `EN_SEGUIMIENTO`, `CERRADO`.
  6. `priority`: Opcional. Si se omite o viene en blanco, aplica valor por defecto `MEDIA`. Si se indica, solo se permite `BAJA`, `MEDIA` o `ALTA` (insensible a mayúsculas/minúsculas).
  7. `origin`: Opcional. Si se omite o viene en blanco, aplica valor por defecto `MANUAL`. Si se indica, solo se permite `WEB`, `REFERIDO` o `MANUAL` (insensible a mayúsculas/minúsculas).
  8. `proximoSeguimiento`: Opcional. Formato ISO `YYYY-MM-DD` o celda nativa de fecha de Excel. Si la etapa es `CERRADO`, el valor se limpia automáticamente a `null`.
- **Política de filas en blanco:** Filas 100% vacías (todas las celdas nulas o con solo espacios) son ignoradas de manera segura y no consumen límite de filas ni aparecen en el diagnóstico. Filas parcialmente pobladas son evaluadas como filas de datos y sujetas a validación de campos obligatorios.
- **Protección contra fórmulas:** Las fórmulas en celdas de datos no son evaluadas y se rechazan sistemáticamente como entrada inválida (`No se permiten fórmulas en las celdas`).
- **Seguridad del parser:** Procesamiento streaming basado en SAX (`XSSFReader` y `XMLHelper.newXMLReader()`) con DOCTYPE deshabilitado (`disallow-doctype-decl`), entidades externas bloqueadas (defensa XXE) y límites de ratio de inflación activos (`ZipSecureFile` contra Zip Bombs). La configuración de seguridad del parser opera bajo fallo cerrado (falla con error seguro si no se pueden establecer las directivas de protección).
- **Configuración criptográfica:** La generación y descifrado de tokens de vista previa requiere configurar la variable de entorno `URLEADS_IMPORT_TOKEN_SECRET` (o propiedad `urleads.import.token-secret`) con un secreto de al menos 32 caracteres. El sistema opera bajo fallo cerrado y rechaza operaciones de importación si dicha configuración está ausente o es débil. No existen claves fijas en código.

### Detección de duplicados y aislamiento de propietario

- **Duplicados intra-archivo:** Si múltiples filas del mismo archivo presentan el mismo correo o teléfono normalizado, la primera ocurrencia es válida y las siguientes se marcan como `DUPLICATE` indicando la fila conflictiva previa.
- **Duplicados en base de datos:** Se validan contra leads existentes pertenecientes exclusivamente al usuario autenticado (`usuario_id`). Si el mismo teléfono o correo existe para el usuario, la fila se marca como `DUPLICATE`.
- **Aislamiento estricto:** La existencia de contactos idénticos en leads de otros usuarios no genera conflictos ni revela su existencia (`existsByUsuarioId...`).

### Semántica de Preview (`POST /api/leads/import/preview`)

Genera un diagnóstico detallado fila por fila sin realizar ninguna mutación en la base de datos (0 escrituras):
- Si todas las filas son válidas y hay al menos una fila de datos, `canConfirm` es `true` y se emite un `previewId` totalmente opaco cifrado de forma autenticada con AES-256-GCM (IV de 12 bytes y etiqueta de autenticación de 128 bits). No expone usuario, marcas de tiempo ni resúmenes en texto plano al ser inspeccionado.
- Si el archivo contiene una o más filas con error o duplicadas, `canConfirm` es `false`. Un archivo con cualquier error no es apto para confirmación.

Ejemplo de respuesta exitosa de preview:
```json
{
  "previewId": "eyJhbGciOiJIUzI1NiJ9...",
  "totalRows": 2,
  "validRows": 2,
  "invalidRows": 0,
  "canConfirm": true,
  "rows": [
    {
      "rowNumber": 2,
      "status": "VALID",
      "values": {
        "name": "Juan Pérez",
        "phone": "987654321",
        "email": "juan@example.com",
        "service": "Consultoría",
        "stage": "NUEVO",
        "priority": "MEDIA",
        "origin": "MANUAL",
        "nextFollowUp": "2026-11-15"
      },
      "errors": []
    },
    {
      "rowNumber": 3,
      "status": "VALID",
      "values": {
        "name": "María López",
        "phone": "912345678",
        "email": "maria@example.com",
        "service": "Auditoría",
        "stage": "EN_SEGUIMIENTO",
        "priority": "ALTA",
        "origin": "WEB",
        "nextFollowUp": "2026-12-01"
      },
      "errors": []
    }
  ]
}
```

### Semántica de Confirmación (`POST /api/leads/import/confirm`)

Inserta atómicamente la totalidad de los leads de un archivo previamente verificado:
- **Parámetros requeridos:** Multipart `file` (el mismo archivo .xlsx) y parámetro `previewId`.
- **Orden de verificación y ciclo de vida del recibo:**
  1. Autentica y descifra el token AES-GCM, comprobando que pertenezca al usuario en sesión y que el resumen SHA-256 del archivo coincida exactamente con el vinculado al token.
  2. Si existe un recibo previo confirmado en `lead_import_receipt` para este token y propietario, retorna inmediatamente el resultado almacenado (idempotencia durable). El reintento exacto es válido **indefinidamente** (incluso tras 24 horas y tras reiniciar el servicio), sin recrear leads ni eventos outbox.
  3. Si es un primer uso (token aún no confirmado), se verifica que no hayan transcurrido más de 24 horas desde la vista previa. Pasadas las 24h, tokens no confirmados expiran y son rechazados.
- **Transaccionalidad (Todo o nada):** Si cualquier fila falla en validación o restricción de base de datos, se aborta la transacción completa y se crean cero leads.
- **Idempotencia durable:** Tras una confirmación exitosa, se persiste un recibo en `lead_import_receipt` con restricción de unicidad en `preview_id`.
- **Aislamiento en reintentos:** Intentos de reuso de un `previewId` ajeno retornan `404 Not Found` sin revelar la existencia del recibo.

Ejemplo de respuesta de confirmación exitosa:
```json
{
  "previewId": "eyJhbGciOiJIUzI1NiJ9...",
  "importedCount": 2,
  "leadIds": [101, 102]
}
```

### Seguridad y códigos de error

- **Autenticación requerida:** Sesión web activa (cookie `JSESSIONID`) y cabecera CSRF obligatoria (`X-XSRF-TOKEN`).
- **Códigos HTTP:**
  - `200 OK`: Vista previa generada exitosamente o confirmación exitosa/recibo idempotente devuelto.
  - `400 Bad Request`: Formato no válido, tamaño > 5 MiB, filas > 500, cabeceras incorrectas, token de vista previa inválido/expirado, archivo no coincidente con la vista previa, o confirmación de archivo con filas inválidas (`code: VALIDATION_ERROR`).
  - `401 Unauthorized`: Solicitud sin sesión activa.
  - `403 Forbidden`: Token CSRF ausente o inválido.
  - `404 Not Found`: Vista previa inexistente o perteneciente a otro propietario (`code: NOT_FOUND`).
## Archivo y restauración lógica (Backend V2 C12 / DSW-52)

Operaciones para la gestión de ciclo de vida lógico de leads sin eliminación física:

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/leads/{id}/archive` | Archiva un lead activo propio; responde 200 OK con `archivedAt` asignado. |
| POST | `/api/leads/{id}/restore` | Restaura un lead archivado propio; responde 200 OK con `archivedAt: null`. |
| GET | `/api/leads/archived` | Listado paginado de leads archivados del usuario (orden `createdAt` DESC, `id` DESC). |

### Reglas y contratos de archivo y restauración:

- **Marca temporal de archivo:** Se utiliza la columna `archived_at TIMESTAMPTZ NULL` en la tabla `lead`. `NULL` representa un lead activo; un timestamp UTC representa un lead archivado.
- **Diferenciación estricta de DELETE:** El archivo lógico es una operación independiente de `DELETE`. No realiza borrado físico en base de datos ni invoca cascada de notas (`nota`), registros de auditoría (`lead_audit_log`), etiquetas (`lead_tag`) ni eventos outbox históricos. Tampoco emite eventos outbox adicionales.
- **Exclusión de leads archivados:** Todas las consultas y mutaciones estándar excluyen leads archivados, respondiendo HTTP 404 indistinguible (`{"status":404,"code":"NOT_FOUND","message":"Lead no encontrado"}`):
  - Detalle `GET /api/leads/{id}`.
  - Edición `PUT /api/leads/{id}`.
  - Gestión de etiquetas `PUT /api/leads/{id}/tags`.
  - Notas `GET` y `POST /api/leads/{id}/notes`.
  - Auditoría `GET /api/leads/{id}/audit`.
  - Eliminación física `DELETE /api/leads/{id}`.
  - Dashboard `GET /api/dashboard` (conteos totales, distribución por etapa, seguimientos de hoy y atrasados excluyen archivados).
  - Consulta interna inter-servicios `GET /internal/v1/leads/{id}`.
  - Búsqueda y listado activo `GET /api/leads`.
- **Transiciones de estado y respuestas de error:**
  - `POST /api/leads/{id}/archive` requiere que el lead pertenezca al usuario autenticado y se encuentre activo (`archived_at IS NULL`). Si el lead ya está archivado, no existe o pertenece a otro usuario, responde 404 (semántica de no-existencia).
  - `POST /api/leads/{id}/restore` requiere que el lead pertenezca al usuario autenticado y se encuentre archivado (`archived_at IS NOT NULL`). Si el lead ya está activo, no existe o pertenece a otro usuario, responde 404.
- **Unicidad de contactos normalizados C06:** Las restricciones únicas sobre `correo_norm` y `telefono_norm` aplican a nivel de tabla sobre todas las filas (incluyendo archivadas). Los contactos de leads archivados permanecen reservados y restaurar un lead no evade las reglas de unicidad.
- **Seguridad:** Requiere sesión web activa (cookie `JSESSIONID`) y cabecera obligatoria `X-XSRF-TOKEN` para mutaciones (`archive`, `restore`). La propiedad del lead siempre se resuelve a través del usuario autenticado en el servidor (`CurrentUserProvider`), ignorando cualquier parámetro del cliente.

## Monitoreo y salud

El endpoint de salud es público y no requiere sesión ni credenciales. Es el
único endpoint implementado actualmente en el scaffold del backend.

| Método | Ruta | Estado | Resultado |
|---|---|---|---|
| GET | `/api/health` | Implementado | Estado HTTP del backend (público, responde HTTP 200). |

Respuesta exitosa:

```json
{
  "status": "UP"
}
```

> **Nota:** Este endpoint valida únicamente la capacidad de respuesta HTTP del
> servidor de la aplicación en ejecución. Se mantiene HTTP-only con
> `{"status":"UP"}` sin realizar comprobaciones de conectividad ni de
> disponibilidad de PostgreSQL ni del resto de funciones del producto.

## Errores

Se usará una forma estable, por ejemplo:

```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Revisa los campos indicados.",
  "fieldErrors": {
    "name": "El nombre es obligatorio."
  }
}
```

- 400: validación o formato inválido.
- 401: no existe sesión válida o credenciales inválidas.
- 403: CSRF inválido o acceso prohibido.
- 404: recurso inexistente o que no pertenece al usuario.
- 409: conflicto de estado cuando aplique.
- 500: error interno sin detalles sensibles.

## Endpoints internos (Backend V2)

This section describes internal inter-service endpoints exposed by the CRM service.

### Internal lead lookup (`GET /internal/v1/leads/{id}`)

Operative internal endpoint for inter-service communication (CRM -> Comercial / Agenda), implemented in Backend V2 (Task C03):

- **Method and path:** `GET /internal/v1/leads/{id}`
- **Authentication and security:** Requires HTTP header `Authorization: Bearer <token>`. The token must be an RS256-signed JWT issued by `urleads-crm`, containing audience `crm-api` and scope `crm.leads.read`. The subject (`sub`) must be a positive decimal string resolving to the authenticated owner ID. Prerequisite: `urleads.jwt.enabled=true`.
- **Stateless execution:** Stateless request processing (`STATELESS`, `NullSecurityContextRepository`) with CSRF disabled exclusively for `/internal/**`. Session authentication is ignored with no fallback.
- **Success response (200 OK):** Returns a minimal non-PII payload containing only the lead ID, current canonical stage, and next follow-up date:
```json
{
  "id": 105,
  "stage": "EN_SEGUIMIENTO",
  "nextFollowUp": "2026-10-25"
}
```
If the lead has no scheduled follow-up (such as in stage `CERRADO` or `NUEVO` without follow-up), `nextFollowUp` is returned as `null`:
```json
{
  "id": 106,
  "stage": "CERRADO",
  "nextFollowUp": null
}
```
- **Error responses:**
  - `401 Unauthorized`: Missing or malformed token, invalid signature, untrusted key, expired beyond 30-second skew tolerance, wrong issuer, missing or invalid `aud`, or invalid subject.
  - `403 Forbidden`: Valid token lacking the required `crm.leads.read` scope.
  - `404 Not Found`: Lead does not exist or belongs to another owner (`{"status":404,"code":"NOT_FOUND","message":"Lead no encontrado"}`). Does not leak resource existence across owners.
- **Content negotiation:** Returns `application/json`. Requests specifying `Accept: text/html` receive `406 Not Acceptable`; SPA HTML is never served.
- **Application service reuse:** Core lookup logic is encapsulated in `LeadService.obtenerInterno(Long leadId, Long usuarioId)`, reusable directly by future internal integrations (such as SOAP C13) without HTTP self-calls.
- **Known boundaries:** Exclusion of archived leads is implemented in task C12 (DSW-52); archived leads return HTTP 404 identical to nonexistent or other-owner leads. Live port 8080 verification is constrained by existing local environment processes.

## Método PUT

`PUT /api/leads/{id}` reemplaza todos los campos editables. El cliente envía
los valores conservados además de los modificados. No se introduce PATCH solo
para el cambio de etapa.

## Documentación OpenAPI 3 y Swagger UI (Backend V2 C05)

El servicio expone la documentación interactiva y el contrato formal OpenAPI 3:

| Método | Ruta | Formato | Descripción |
|---|---|---|---|
| GET | `/v3/api-docs` | JSON | Especificación OpenAPI 3 formal con contratos, esquemas y seguridad. |
| GET | `/swagger-ui.html` | HTML | Interfaz Swagger UI interactiva (redirige a `/swagger-ui/index.html`). |

Esquemas de seguridad y cabeceras documentadas:
- `cookieAuth` (`JSESSIONID` vía cookie): Autenticación web para rutas bajo `/api/**`.
- `X-XSRF-TOKEN` (cabecera HTTP obligatoria en mutaciones): Requerido en métodos que mutan estado (`POST`, `PUT`, `DELETE`). Las mutaciones autenticadas requieren sesión web activa (`cookieAuth`) Y cabecera `X-XSRF-TOKEN`. El inicio de sesión (`POST /api/auth/login`) es público pero requiere la cabecera `X-XSRF-TOKEN`.
- `bearerJwt` (`Authorization: Bearer <token>`): Token Bearer JWT RS256 con audiencia `crm-api` y alcance `crm.leads.read` para `/internal/**`.

## Colección Postman (Backend V2 C05)

La colección oficial importable se encuentra en `backend/postman/urleads-crm.postman_collection.json` (formato Postman v2.1) acompañada de su entorno `backend/postman/urleads-crm.postman_environment.json`.

Cubre:
1. Salud del servicio (`GET /api/health`).
2. Autenticación, CSRF, login, consulta de usuario (`/api/auth/me`, `/api/auth/session`) y logout.
3. Emisión de tokens de acceso JWT (`POST /api/auth/access-token`).
4. Operaciones CRUD y filtros de leads (`GET`, `POST`, `PUT`, `DELETE /api/leads`).
5. Bitácora interna de notas de lead (`GET`, `POST /api/leads/{id}/notes`).
6. Métricas del dashboard (`GET /api/dashboard`).
7. Consulta interna inter-servicios (`GET /internal/v1/leads/{id}`) con autenticación Bearer JWT.
8. Verificación de documentación OpenAPI (`GET /v3/api-docs` y `/swagger-ui.html`).

Todos los valores utilizan variables de entorno seguras (`{{baseUrl}}`, `{{username}}`, `{{password}}`, `{{csrfToken}}`, `{{accessToken}}`, `{{leadId}}`) sin credenciales reales ni URLs de producción.
