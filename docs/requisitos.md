# Requisitos y criterios de aceptación

Esta especificación convierte el alcance de UrLeads y el plan del curso en
comportamientos que el equipo puede implementar y comprobar.

## Requisitos funcionales

| ID | Requisito | Criterio de aceptación |
|---|---|---|
| RF01 | Iniciar sesión | Credenciales correctas crean una sesión; incorrectas no autentican. |
| RF02 | Cerrar sesión | La sesión se invalida y las rutas privadas vuelven a requerir login. |
| RF03 | Registrar lead | Se guarda un lead válido con nombre, al menos un contacto y etapa inicial. |
| RF04 | Consultar leads | El usuario ve sus leads, con búsqueda, filtros y paginación sencilla. |
| RF05 | Consultar detalle | La pantalla muestra contacto, etapa, seguimiento y notas. |
| RF06 | Editar lead | Los campos editables se actualizan y persisten tras recargar. |
| RF07 | Eliminar lead | La interfaz pide confirmación; lead y notas se eliminan de forma consistente. |
| RF08 | Agregar nota | Una nota de texto se guarda con fecha y aparece en el detalle. |
| RF09 | Actualizar etapa | Se puede seleccionar una etapa permitida y ver el cambio persistido. |
| RF10 | Programar seguimiento | Se puede crear, cambiar o limpiar una fecha y ver si está atrasada. |
| RF11 | Ver dashboard | Los totales y pendientes corresponden a los datos reales del usuario. |

## Requisitos no funcionales

| ID | Requisito | Criterio de aceptación |
|---|---|---|
| RNF01 | Stack académico | Backend Java con Spring MVC, Spring Data, Spring Security y Lombok. |
| RNF02 | Persistencia | Los datos se guardan en PostgreSQL, incluso después de reiniciar. |
| RNF03 | Contraseña | Se almacena un hash BCrypt; nunca texto plano ni respuesta con el hash. |
| RNF04 | API REST | GET, POST, PUT y DELETE operativos y probados con Postman. |
| RNF05 | Acceso privado | Las rutas de gestión rechazan solicitudes no autenticadas. |
| RNF06 | Responsive | El flujo principal funciona en escritorio y móvil. |
| RNF07 | Despliegue | La versión final se puede usar desde una URL pública HTTPS. |
| RNF08 | Validación | Datos inválidos reciben errores claros sin revelar detalles internos. |
| RNF09 | Accesibilidad básica | Formularios etiquetados, foco visible, contraste suficiente y uso por teclado. |

## Reglas de negocio

- El nombre es obligatorio y tiene entre 2 y 120 caracteres.
- Debe existir teléfono, correo o ambos.
- El correo debe tener formato válido cuando se proporcione.
- Etapas permitidas: `NUEVO`, `EN_SEGUIMIENTO` y `CERRADO`.
- La fecha de seguimiento es opcional y se interpreta como fecha local de Lima.
- Una fecha pasada es válida y se presenta como atrasada.
- Al cerrar un lead se limpia su fecha de seguimiento. Al reabrirlo puede
  programarse otra fecha.
- La nota tiene entre 1 y 2.000 caracteres y se trata como texto plano.
- Cada lead pertenece al usuario autenticado; el servidor determina el dueño.
- Eliminar un lead elimina sus notas en una sola transacción.

## Trazabilidad académica

| Requisito del curso | Evidencia prevista |
|---|---|
| Login REST y BCrypt | Tests de login, Postman y captura sanitizada del hash. |
| Métodos GET, POST, PUT y DELETE | Colección Postman y pruebas de integración. |
| Pruebas de acceso a datos | Pruebas PostgreSQL para insertar, actualizar, eliminar y listar. |
| Aplicación en nube | URL Railway validada y prueba de persistencia. |
| Informe, presentación y video | Checklist de `entrega-academica.md`. |
