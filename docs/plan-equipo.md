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
| Karlo | API y datos de leads | CRUD, validaciones, filtros, reglas de etapa/seguimiento y pruebas automatizadas de datos; cuenta Linear pendiente. |
| Alexander | Seguridad y funciones de servidor | Login/sesión/CSRF, API de bitácora y dashboard; tests de autenticación y endpoints. |
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

El smoke test temprano de Villa solo comprueba que el scaffold Spring y
PostgreSQL arrancan y se conectan. La colección Postman prueba luego las rutas
funcionales; así el smoke test no duplica UL-16. El deploy temprano también
usa solo el scaffold y la DB, no espera CRUD/login completos.

## Estimación de disponibilidad

Se planifica con una disponibilidad declarada de 2–3 horas diarias por persona
hasta el 16 de octubre. Las estimaciones son horas de esfuerzo, no horas
calendario. Cada integrante confirma en Linear su disponibilidad real y el
responsable técnico revisa las tareas que excedan cuatro horas.

## Fechas objetivo

| Fecha | Hito | Resultado esperado |
|---|---|---|
| 6–8 oct. | Alcance, contrato, scaffold y DB local | Contrato/boceto acordados, Spring health y PostgreSQL reproducible. |
| 8–9 oct. | Smoke test y nube temprana | Arranque/conectividad local y deploy de scaffold autorizado; sin CRUD completo. |
| 10–12 oct. | CRUD, UI de leads, detalle y bitácora | Operaciones API, lista/alta/edición y detalle integrados. |
| 11–14 oct. | Reglas, dashboard y verificación | Etapas/fechas, dashboard real, Postman y QA móvil. |
| 14 oct. | Verificación y corrección | Seguridad, móvil, persistencia y errores importantes revisados. |
| 15 oct. | Candidata | Congelar alcance, completar informe y preparar demo. |
| 16 oct. | MVP validado | URL revisada, datos ficticios, respaldo y ensayo. |
| 17–18 oct. | Margen | Corregir solo problemas importantes y practicar sustentación. |

Las fechas dependen de disponibilidad y avance real; no son garantía. El primer
deploy del 9 de octubre prueba solo el scaffold y la conexión a PostgreSQL. El
smoke test del 8 es solo local; Postman prueba la API funcional después de que
sus endpoints estén listos. El primer recorrido CRUD queda para el 10 de
octubre. Si el 9 no existe un recorrido funcional, se recortan adornos y trabajo
no esencial, no seguridad, persistencia, operaciones REST ni despliegue.

## Linear y GitHub

Linear será el tablero único para tareas, responsables, prioridades, fechas,
estimaciones y dependencias. El proyecto [UrLeads en DSW2](https://linear.app/enmanuelprojects/project/urleads-b3c2085df458)
contiene los hitos y tareas publicadas. GitHub se usará para ramas, código,
pull requests y checks. No dupliquen Issues/tareas en ambos sitios.

Cada tarea en Linear incluye un resultado comprobable, responsable,
revisor, prioridad, esfuerzo estimado en horas, fecha objetivo, dependencias y
criterios de aceptación. Urgent queda reservado para DSW-9 (login académico)
y DSW-13 (CRUD evaluado); High indica trabajo crítico y Medium apoyo/diseño.
Los tickets UL-22a/b/c duplicados durante la carga
quedaron marcados como duplicados; usar como canónicos DSW-33 (frontend),
DSW-29 (datos) y DSW-31 (seguridad/integración). La cuenta de Karlo no aparece
en Linear, por lo que sus tickets están sin asignación con responsable previsto
indicado en la descripción. UL-02 ahora incluye scaffold Spring Boot y health;
UL-10 es solo smoke test de arranque, mientras UL-16 cubre pruebas funcionales
Postman. UL-08 implementa CRUD y UL-17 reglas/filtros backend; Sebas consume
esos filtros en UL-13b. El dashboard API usa leads y reglas, no notas.
Mantengan una tarea principal activa por persona y
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
