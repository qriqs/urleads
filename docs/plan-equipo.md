# Plan del equipo y calendario

El equipo de cinco personas trabajará hacia un MVP listo el 16 de octubre de
2026, con versión candidata el día 15 y margen antes de la exposición.

## Integrantes y responsabilidad inicial

Los apodos se usan para distinguir compañeros. Antes de copiar las tareas a
GitHub, confirmen correspondencia con los nombres completos del informe AP1.

| Integrante | Responsabilidad | Entrega verificable |
|---|---|---|
| Cristopher | Coordinación técnica, estructura, CI, despliegue e integración | Build integral y recorrido completo en nube. |
| Sebas | Diseño y frontend | UI responsive conectada a API real. |
| Karlo | Backend de leads | CRUD, DTO, validación, repositorio y pruebas. |
| Sebastian 2 | Login/seguridad; luego notas y dashboard | Login, protección, logout, notas e indicadores. |
| Villa | Informe, pruebas manuales y exposición | Informe coherente, evidencias, checklist y presentación. |

Villa tendrá menos código porque ya elaboró el informe, pero sí entregables
concretos. Sebastian 2 recibirá acompañamiento en Spring Security: saber Git no
implica conocer ese framework. Cristopher ayudará con integración, no asumirá
por defecto toda la implementación.

## Disponibilidad y riesgo

Estimación orientativa: 90–120 horas-persona, suponiendo que quienes programan
puedan dedicar aproximadamente 2–3 horas diarias en los días de trabajo. No es
garantía. El 6–7 de octubre se hará una comprobación breve de que Karlo y
Sebastian 2 pueden ejecutar el backend, tests y su primera tarea. Si alguien se
bloquea, se reduce complejidad visual y se redistribuyen tareas críticas sin
quitar seguridad, CRUD o despliegue.

## Calendario

| Fecha | Hito | Resultado |
|---|---|---|
| 6–7 oct. | Alcance, contrato, bocetos y entorno | Reglas acordadas y proyectos base. |
| 8–9 oct. | Primer recorrido y primer deploy | Login, crear/listar, tests de repositorio. |
| 10–11 oct. | CRUD y detalle | Editar, eliminar, notas e integración. |
| 12–13 oct. | Seguimientos y resumen | Requisitos funcionales completos. |
| 14 oct. | Prueba y revisión visual | Correcciones de seguridad y responsive. |
| 15 oct. | Congelar funcionalidades | Candidata, evidencia, informe y guion. |
| 16 oct. | MVP listo | URL validada, respaldo y ensayo. |
| 17–18 oct. | Margen | Solo correcciones y exposición. |

El día 9 es un punto de decisión. Si no existe un flujo conectado, se recortan
adornos y no los requisitos evaluados por el curso.

## GitHub

- Un repositorio con `frontend/`, `backend/`, `infra/` y `docs/`.
- `main` se mantiene estable; ramas cortas por tarea, por ejemplo
  `feat/leads-api` y `feat/dashboard-ui`.
- Issues pequeños con responsable, revisor, criterio de aceptación y evidencia.
- Pull request pequeño con al menos una revisión y checks verdes.
- Tablero: Pendiente, En curso, En revisión y Terminado.
- Una tarea principal activa por integrante para evitar trabajo invisible.
- Nunca subir contraseñas, cookies, tokens, datos reales ni variables privadas.
- La persona que entrega código debe poder explicarlo y probarlo.

## Reunión diaria breve

Cada integrante informa: qué terminó, qué hará a continuación y qué bloqueo
necesita ayuda. Cristopher actualiza el tablero y resuelve dependencias entre
API, UI y despliegue. Las decisiones que cambien alcance se anotan en un Issue.
