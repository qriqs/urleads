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


---

# Guía de Pruebas Postman y WebSocket RFC 6455: Servicio Agenda

Esta guía describe cómo utilizar la colección de Postman (`UrLeads_Agenda_Service.postman_collection.json`) y cómo ejecutar pruebas manuales y automatizadas sobre los endpoints REST y el canal WebSocket del microservicio Agenda y Notificaciones (Alex).

---

## 1. Importación de la Colección en Postman

1. Abre **Postman**.
2. Haz clic en **Import** y selecciona el archivo `UrLeads_Agenda_Service.postman_collection.json`.
3. Se creará la colección **UrLeads - Servicio Agenda y Notificaciones (Alex)**.

---

## 2. Variables de la Colección

La colección incluye las siguientes variables configurables:

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `baseUrl` | `http://localhost:8082` | URL base del microservicio Agenda (o `http://localhost:8080`). |
| `jwt_token` | *(Token JWT generado)* | Token de acceso con claim `sub: "42"` y scope `agenda.access`. |
| `appointmentId` | `1` | ID numérico de prueba para citas. |
| `taskId` | `1` | ID numérico de prueba para tareas. |
| `notificationId` | `1` | ID numérico de prueba para notificaciones. |

---

## 3. Endpoints REST Disponibles en la Colección

### A. Documentación Swagger & OpenAPI 3
- `GET {{baseUrl}}/swagger-ui.html` - Interfaz interactiva de Swagger UI.
- `GET {{baseUrl}}/v3/api-docs` - Especificación OpenAPI 3 en formato JSON.

### B. Notificaciones (`/api/v1/notifications`)
- `GET /api/v1/notifications` - Listado paginado con filtros (`unreadOnly`, `page`, `size`).
- `PATCH /api/v1/notifications/{id}/read` - Marcar notificación como leída.
- `POST /api/v1/notifications/mark-all-read` - Marcar todas como leídas.
- `POST /api/v1/notifications/{id}/snooze` - Posponer notificación (`15m`, `1h`, `1d`, máx 3 veces).

### C. Tareas (`/api/v1/tasks`)
- `POST /api/v1/tasks` - Crear tarea asociada opcionalmente a un lead autorizado.
- `GET /api/v1/tasks` - Listar tareas filtradas por lead y estado.
- `GET /api/v1/tasks/{id}` - Obtener tarea por ID.
- `PUT /api/v1/tasks/{id}` - Actualizar título, descripción o fecha límite.
- `PATCH /api/v1/tasks/{id}/complete` - Marcar tarea completada o pendiente.
- `DELETE /api/v1/tasks/{id}` - Eliminar tarea.

### D. Citas (`/api/v1/appointments`)
- `POST /api/v1/appointments` - Crear cita con control de zona horaria (`America/Lima`) y fechas ISO-8601.
- `GET /api/v1/appointments` - Listar citas paginadas.
- `GET /api/v1/appointments/{id}` - Obtener cita por ID.
- `PUT /api/v1/appointments/{id}` - Actualizar horario y datos de la cita.
- `PATCH /api/v1/appointments/{id}/status` - Cambiar estado (`PROGRAMADA`, `REALIZADA`, `CANCELADA`).
- `DELETE /api/v1/appointments/{id}` - Eliminar cita.

### E. Minutas y Resultados de Cita (`/api/v1/appointments/{id}/outcome`)
- `POST /api/v1/appointments/{id}/outcome` - Registrar minuta, acuerdos y próximas acciones al finalizar cita (almacenamiento local en Agenda, sin inyectar notas privadas en CRM).
- `GET /api/v1/appointments/{id}/outcome` - Consultar resultado y minuta registrada.

### F. Consultas Agregadas de Agenda (`/api/v1/agenda`)
- `GET /api/v1/agenda` - Feed combinado cronológico de citas y tareas en rango de hasta 31 días.
- `GET /api/v1/agenda/daily-pending` - Resumen diario de pendientes en la zona horaria del usuario.

### G. Preferencias de Usuario (`/api/v1/preferences`)
- `GET /api/v1/preferences` - Consulta de zona horaria, horario laboral y canales habilitados.
- `PUT /api/v1/preferences` - Actualización de preferencias del usuario autenticado.

---

## 4. Instrucciones para Probar WebSocket RFC 6455

Postman soporta peticiones **WebSocket** nativas:

1. En Postman, crea una nueva petición seleccionando el tipo **WebSocket Request**.
2. Ingresa la URL del WebSocket de Agenda:
   ```text
   ws://localhost:8082/ws/notifications
   ```
   *(O `ws://localhost:8080/ws/notifications` en entorno unificado)*.
3. Haz clic en **Connect**.
4. La conexión se establecerá inmediatamente en estado **pendiente de autenticación**.
5. **Primer frame de autenticación (OBLIGATORIO dentro de 5 segundos):**
   Envía el siguiente mensaje JSON en la pestaña de mensajes:
   ```json
   {
     "type": "authenticate",
     "accessToken": "{{jwt_token}}"
   }
   ```
6. **Respuesta esperada del servidor:**
   El servidor responderá con el frame de confirmación:
   ```json
   {
     "type": "authenticated"
   }
   ```
7. **Comportamiento ante error / timeout:**
   - Si no se envía el frame dentro de 5 segundos, el servidor cierra la conexión con código **1008 (Policy Violation)**.
   - Si el token es inválido o expiró, el servidor cierra inmediatamente con código **1008**.
8. **Recepción en vivo de notificaciones:**
   Al producirse un evento de cotización aceptada (`quotation.accepted.v1`), el servidor emite al socket:
   ```json
   {
     "type": "notification.created",
     "data": {
       "id": 501,
       "sourceEventId": "a7b3c1d9-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
       "createdAt": "2026-10-17T23:30:00Z",
       "read": false
     }
   }
   ```
