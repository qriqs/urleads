# Contrato inicial de la API

La API usa JSON y el prefijo `/api`. Las rutas de leads requieren sesión. Las
rutas y ejemplos son un contrato inicial que el equipo confirmará antes de
implementar.

## Autenticación

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
| GET | `/api/leads/{id}/notes` | Listar notas cronológicas. |
| POST | `/api/leads/{id}/notes` | Crear nota; responde 201. |
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

Ejemplo de nota:

```json
{
  "content": "Solicitó recibir la propuesta el viernes."
}
```

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
