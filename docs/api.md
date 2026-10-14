# Contrato inicial de la API

La API usa JSON y el prefijo `/api`.

Actualmente están implementados `GET /api/health` y los endpoints CRUD de leads
(`/api/leads`). El backend cuenta con PostgreSQL 16 y el esquema versionado con
Flyway (`V1__initial_schema.sql`, `V2__add_service_column.sql`). Los endpoints de
autenticación, bitácora interna y dashboard corresponden al contrato acordado del
MVP y se implementarán en las siguientes tareas del backlog. Las rutas de leads,
bitácora y dashboard requerirán sesión; mientras la autenticación no esté
integrada, el propietario del lead lo resuelve el servidor y nunca el cliente. La
obtención inicial del token CSRF y el login no requieren una sesión autenticada
previa.

## Autenticación

Endpoints planificados para la autenticación y el control de sesiones:

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/auth/csrf` | Cookie/token CSRF para cliente web o Postman. |
| POST | `/api/auth/login` | Iniciar sesión y establecer cookie. |
| GET | `/api/auth/me` | Obtener usuario actual o 401. |
| POST | `/api/auth/logout` | Invalidar sesión. |

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

## Leads

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/leads` | Lista filtrable y paginada. |
| GET | `/api/leads/{id}` | Detalle de un lead propio. |
| POST | `/api/leads` | Crear lead; responde 201 y representación. |
| PUT | `/api/leads/{id}` | Reemplazar campos editables; responde 200. |
| DELETE | `/api/leads/{id}` | Eliminar lead y notas; responde 204. |
| GET | `/api/leads/{id}/notes` | Listar entradas internas de bitácora cronológicamente. |
| POST | `/api/leads/{id}/notes` | Registrar entrada interna; responde 201. |
| GET | `/api/dashboard` | Resumen y pendientes del usuario. |

La lista acepta `search`, `stage`, `followUp` y `page`; el tamaño de página
será limitado por el servidor. Los valores de `followUp` serán `today`,
`overdue` o `all`.

### Formato de la lista paginada

`GET /api/leads` acepta (todos opcionales):

- `search`: texto parcial, sin distinguir mayúsculas, sobre `name`, `phone` y `email`.
- `stage`: `NUEVO`, `EN_SEGUIMIENTO` o `CERRADO`.
- `followUp`: `today`, `overdue` o `all` (por defecto `all`).
- `page`: índice base 0 (por defecto 0).

El tamaño de página es fijo en el servidor (20); un parámetro `size` enviado por
el cliente se ignora. Orden: `createdAt` descendente.

Respuesta 200:

```json
{
  "content": [
    {
      "id": 42,
      "name": "María Torres",
      "service": "Consultoría",
      "phone": "+51 900 000 000",
      "email": "maria@example.test",
      "stage": "NUEVO",
      "nextFollowUp": null,
      "createdAt": "2026-10-06T15:30:00Z",
      "updatedAt": "2026-10-06T15:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Ejemplo de lead

```json
{
  "id": 42,
  "name": "María Torres",
  "service": "Consultoría",
  "phone": "+51 900 000 000",
  "email": "maria@example.test",
  "stage": "EN_SEGUIMIENTO",
  "nextFollowUp": "2026-10-10",
  "createdAt": "2026-10-06T15:30:00Z",
  "updatedAt": "2026-10-06T15:30:00Z"
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

## Monitoreo y salud

El endpoint de salud es público y no requiere sesión ni credenciales.Es público y no requiere sesión ni credenciales.

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

- `fieldErrors` usa siempre los nombres públicos del contrato (`name`, `phone`,
`email`, `stage`, `nextFollowUp`, `service`).

## Método PUT

`PUT /api/leads/{id}` reemplaza todos los campos editables. El cliente envía
los valores conservados además de los modificados. No se introduce PATCH solo
para el cambio de etapa.

## Postman

La colección deberá ejecutar CSRF, login, `GET`, `POST`, `PUT` y `DELETE` con
cookies de sesión. Incluirá pruebas de credenciales incorrectas, ruta sin
sesión y validación inválida. No incluirá una contraseña real.
