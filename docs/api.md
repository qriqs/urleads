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

## Ejemplo de lead

```json
{
  "id": 42,
  "name": "María Torres",
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
  "phone": "+51 900 000 000",
  "email": "maria@example.test",
  "stage": "NUEVO",
  "nextFollowUp": null
}
```

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

## Método PUT

`PUT /api/leads/{id}` reemplaza todos los campos editables. El cliente envía
los valores conservados además de los modificados. No se introduce PATCH solo
para el cambio de etapa.

## Postman

La colección deberá ejecutar CSRF, login, `GET`, `POST`, `PUT` y `DELETE` con
cookies de sesión. Incluirá pruebas de credenciales incorrectas, ruta sin
sesión y validación inválida. No incluirá una contraseña real.
