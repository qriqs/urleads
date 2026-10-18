# Colección Postman de UrLeads CRM (Backend V2 C05)

Este directorio contiene la colección oficial y el archivo de entorno de Postman para el servicio **UrLeads CRM** (puerto 8080), correspondiente al hito **Backend V2 C05: Documentación OpenAPI, Postman y regresión de seguridad**.

## Archivos

- `urleads-crm.postman_collection.json`: Colección compatible con la especificación Postman v2.1.
- `urleads-crm.postman_environment.json`: Entorno local con variables seguras y valores ficticios.

## Variables de Entorno

| Variable | Descripción | Valor por defecto (Local) |
|---|---|---|
| `{{baseUrl}}` | URL base del servicio CRM | `http://localhost:8080` |
| `{{username}}` | Nombre de usuario para pruebas | `usuario_ejemplo` |
| `{{password}}` | Contraseña para pruebas | `contrasena_segura_ejemplo` |
| `{{csrfToken}}` | Token CSRF dinámico obtenido de `/api/auth/csrf` | `(automático en tests)` |
| `{{accessToken}}` | Token Bearer JWT dinámico emitido por `/api/auth/access-token` | `(automático en tests)` |
| `{{leadId}}` | ID de lead para operaciones de detalle/edición/eliminación | `1` |

> **Aviso de Seguridad:** Ningún archivo contiene contraseñas reales, tokens de producción, hashes criptográficos ni URLs de despliegue.

## Flujo de Uso en Postman

1. **Importar**: En Postman, seleccione **Import** y cargue `urleads-crm.postman_collection.json` y `urleads-crm.postman_environment.json`.
2. **Seleccionar entorno**: Active el entorno `UrLeads CRM - Local`.
3. **Flujo Web (Cookie de Sesión + CSRF)**:
   - Ejecute `02. Autenticación / Obtener Token CSRF`: el script captura el token en la variable `csrfToken`.
   - Ejecute `02. Autenticación / Iniciar Sesión (Login)`: envía credenciales y cabecera `X-XSRF-TOKEN`. Spring Boot establece la cookie `JSESSIONID`.
   - Ejecute operaciones de `04. Leads`, `05. Notas de Lead` o `06. Dashboard`. Las mutaciones (POST, PUT, DELETE) incluyen automáticamente la cabecera `X-XSRF-TOKEN: {{csrfToken}}`.
4. **Flujo Interservicios (Bearer JWT - C02/C03)**:
   - Con sesión activa, ejecute `03. Emisión de Tokens / Emitir Token de Acceso JWT (C02)`: el script guarda el token en `accessToken`.
   - Ejecute `07. Consulta Interna Inter-Servicios / Consultar Lead Interno (Bearer JWT)`: envía `Authorization: Bearer {{accessToken}}` contra `/internal/v1/leads/{{leadId}}` sin cookies de sesión.
5. **Documentación OpenAPI**:
   - `08. OpenAPI / Obtener Especificación OpenAPI 3`: devuelve el esquema JSON desde `/v3/api-docs`.
   - `08. OpenAPI / Acceder a Swagger UI`: interfaz interactiva en `/swagger-ui.html`.
