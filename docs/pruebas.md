# Plan de pruebas

La definición de terminado requiere pruebas de datos, seguridad, API e
interfaz. Las capturas complementan las pruebas, pero no las sustituyen.

## Acceso a datos

Con una base PostgreSQL de prueba, verificar:

| Operación | Comprobación |
|---|---|
| Insertar | Un lead válido queda persistido y se puede recuperar. |
| Actualizar | Campos editados conservan los demás valores y se persisten. |
| Eliminar | Lead y notas desaparecen de forma transaccional. |
| Listar | Se obtienen resultados del dueño correcto y filtros esperados. |

## Seguridad y autenticación

- Login válido crea sesión y cookie segura según entorno.
- Credenciales incorrectas, vacías o malformadas no autentican.
- Rutas de leads sin sesión responden 401.
- Solicitudes mutables sin CSRF válido se rechazan.
- Logout invalida sesión; sesión antigua no recupera acceso.
- Hash BCrypt se almacena, nunca contraseña en texto plano.
- Respuestas no exponen hash, secretos, trazas o datos de otro dueño.

## API y reglas

- Ejecutar GET, POST, PUT y DELETE desde Postman.
- Probar nombre, contacto, correo, etapas y longitud de nota inválidos.
- Probar lead/notas inexistentes y lead perteneciente a otro usuario.
- Probar filtro de hoy, fecha pasada y seguimiento vacío.
- Verificar que fechas se interpreten en zona de Lima.
- Confirmar que eliminar un lead elimina sus notas en la misma transacción.

## Interfaz

- Recorrido: login, crear lead, editar, agregar nota, cambiar etapa, programar,
  buscar y eliminar.
- Probar carga, error, lista vacía, confirmación y sesión expirada.
- Verificar teclado, foco, etiquetas, contraste y viewport de 360 px.
- Confirmar que al recargar se muestran datos de API, no estado solo local.

## CI y despliegue

En pull request: tests backend, TypeScript, lint y build frontend/backend. Antes
de exposición: repetir los casos principales desde la URL pública, reiniciar o
volver a desplegar y verificar que los datos persisten.

## Evidencias

Villa reunirá capturas sanitizadas de Postman, tests de repositorio, tabla de
usuario con hash y aplicación pública. Usar datos ficticios. No mostrar
contraseñas, cookies ni el hash completo de una cuenta activa.
