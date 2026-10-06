# Plan del equipo y calendario

El equipo organiza tareas en Linear y mantiene código, ramas y pull requests en
GitHub. La fecha objetivo es tener una versión candidata el 15 de octubre de
2026 y el MVP validado el 16, con días de margen antes de la exposición.

## Responsabilidades

Los apodos distinguen a los integrantes del grupo. Confirmen sus nombres
completos en el informe antes de asignar tareas a cuentas de Linear.

| Integrante | Responsabilidad principal | Entregables |
|---|---|---|
| Cristopher | Plataforma, integración y coordinación técnica | Entorno local, CI, build integrado, despliegue Railway y apoyo a seguridad e integración. |
| Sebas | Frontend completo | Diseño visual, componentes, pantallas, integración React/API y calidad responsive. |
| Karlo | API y datos de leads | CRUD, validaciones, filtros, reglas de etapa/seguimiento y pruebas automatizadas de datos. |
| Sebastian 2 | Seguridad y funciones de servidor | Login/sesión/CSRF, API de bitácora y dashboard; tests de autenticación y endpoints. |
| Villa | QA manual, Postman y entrega académica | Colección y ejecución Postman, recorridos manuales, registro/reprueba de errores, capturas e informe/demo. |

Sebas confirmó que quiere llevar el frontend por su cuenta, por lo que no se
asignará a Villa implementación de UI. Villa no será responsable de pruebas
unitarias ni tendrá que construir funcionalidades de producción. Su tarea
técnica es probar el producto y API con una colección Postman, con apoyo de
Cristopher para cookies de sesión y CSRF. El equipo de backend conserva las
pruebas automatizadas.

El informe de Villa ya está casi terminado. Su trabajo documental se limita a
completar fotografías/capturas y resultados reales, actualizar cualquier
sección que cambie con la implementación y preparar la presentación, no
rehacerlo desde cero.

## Pruebas y colaboración

El equipo distingue tres tipos de verificación:

- **Pruebas automatizadas:** quienes desarrollan backend comprueban repositorios,
  reglas, autenticación, CSRF y endpoints. Sebas ejecuta los checks frontend que
  existan en el proyecto.
- **Pruebas de API en Postman:** Villa ejecuta la colección sobre el backend,
  comprueba método, ruta, datos y estado HTTP, y adjunta evidencias ficticias.
- **Pruebas manuales de producto:** Villa usa los recorridos del navegador,
  busca problemas en formularios, sesiones y móvil, y registra cada hallazgo
  con pasos, esperado, obtenido y evidencia. El dueño del módulo corrige; Villa
  vuelve a probar.

Postman no reemplaza las pruebas de repositorio que solicita el curso. Villa no
debe afirmar que un error está corregido hasta repetir los pasos y verificarlos.

## Estimación de disponibilidad

Se planifica con una disponibilidad declarada de 2–3 horas diarias por persona
hasta el 16 de octubre. Las estimaciones son horas de esfuerzo, no horas
calendario. Cada integrante confirma en Linear su disponibilidad real y el
responsable técnico revisa las tareas que excedan cuatro horas.

## Fechas objetivo

| Fecha | Hito | Resultado esperado |
|---|---|---|
| 6–7 oct. | Alcance, contratos, bocetos y entorno | Acuerdos revisados, tareas asignables y base local. |
| 8–9 oct. | Primer recorrido de extremo a extremo | Login, alta/listado inicial, pruebas de datos y primer despliegue de verificación. |
| 10–11 oct. | CRUD y detalle | Edición, eliminación, bitácora integrada y pantalla detalle. |
| 12–13 oct. | Seguimientos y dashboard | Etapas, filtros y resumen con datos reales. |
| 14 oct. | Verificación y corrección | Seguridad, móvil, persistencia y errores importantes revisados. |
| 15 oct. | Candidata | Congelar alcance, completar informe y preparar demo. |
| 16 oct. | MVP validado | URL revisada, datos ficticios, respaldo y ensayo. |
| 17–18 oct. | Margen | Corregir solo problemas importantes y practicar sustentación. |

Las fechas dependen de disponibilidad y avance real; no son garantía. Si el 9
de octubre no existe un recorrido funcional, se recortan adornos y trabajo no
esencial, no seguridad, persistencia, operaciones REST ni despliegue.

## Linear y GitHub

Linear será el tablero único para tareas, responsables, prioridades, fechas,
estimaciones y dependencias. GitHub se usará para ramas, código, pull requests
y checks. No dupliquen Issues/tareas en ambos sitios.

Cada tarea en Linear debe incluir un resultado comprobable, responsable,
revisor, prioridad, esfuerzo estimado en horas, fecha objetivo, dependencias y
criterios de aceptación. Mantengan una tarea principal activa por persona y
dividan trabajo que supere cuatro horas en entregas revisables.

En GitHub, `main` se mantiene estable. Usen ramas cortas y pull requests pequeños
con una revisión y checks verdes. No incluyan claves, cookies, tokens ni datos
reales en código, Issues, capturas o logs. Cada integrante debe poder explicar
lo que entrega.

## Coordinación

En una sincronización breve, cada integrante informa qué terminó, qué hará y
qué bloqueo tiene. Cristopher revisa dependencias y ayuda con bloqueos de
integración. Las decisiones que cambien alcance se acuerdan antes de editar el
backlog de Linear.
