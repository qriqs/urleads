# Backlog inicial para Linear

Este backlog está preparado para convertirse en tareas de Linear. Las
estimaciones son horas de esfuerzo tentativas, no plazos garantizados; las
fechas objetivo reflejan el plan de tener el MVP validado el 16 de octubre de
2026. Las tareas se gestionan en Linear y el código en GitHub.

## Convenciones

Antes de publicar este backlog, confirmen los usuarios de Linear que
corresponden a Cristopher, Sebas, Karlo, Sebastian 2 y Villa. No asignen por
apodos sin confirmar identidad. Cada tarea debe copiar su responsable, revisor,
prioridad, estimación en horas, fecha objetivo, dependencias y aceptación.

El esfuerzo recomendado por tarea es de 1–4 horas. Dividan cualquier tarea que
crezca más. Las dependencias se expresan por ID. Todas las fechas son objetivos
del plan inicial y deben reajustarse si el equipo confirma otro avance o plazo.

## Tareas

| ID | Tarea / responsable | Revisor | Prioridad | Est. | Fecha | Dep. | Criterios de aceptación |
|---|---|---|---|---:|---|---|---|
| UL-01 | Confirmar alcance, nombres y disponibilidad / Cristopher | Todo el equipo | Urgente | 1 h | 6 oct. | — | Apodos corresponden a miembros; disponibilidad y MVP confirmados; Linear tiene los responsables verificados. |
| UL-02 | Crear estructura local y convenciones / Cristopher | Sebastian 2 | Urgente | 3 h | 7 oct. | UL-01 | Estructura frontend/backend/docs, instrucciones para desarrollo y pasos de ejecución escritos; builds iniciales funcionan. |
| UL-03 | PostgreSQL local y primera migración / Cristopher | Karlo | Urgente | 3 h | 7 oct. | UL-02 | PostgreSQL local arranca; migración crea usuario, leads y notas/bitácora; no hay secretos en Git. |
| UL-04 | Cerrar contrato API y ejemplos Postman / Karlo | Sebastian 2, Villa | Urgente | 2 h | 7 oct. | UL-01 | Rutas, DTO, errores, CSRF y ejemplos coinciden entre `docs/api.md` y colección inicial. |
| UL-05 | Revisar boceto y tokens visuales / Sebas | Equipo | Alta | 2 h | 7 oct. | UL-01 | Login, dashboard, lista y detalle/bitácora tienen estados y versión móvil acordados. |
| UL-06 | Implementar login, sesión y protección / Sebastian 2 | Cristopher | Urgente | 4 h | 9 oct. | UL-03, UL-04 | Login correcto/incorrecto, BCrypt, cookie/sesión, CSRF, logout y rutas protegidas probados automáticamente. |
| UL-07 | Implementar modelo y repositorio de leads / Karlo | Cristopher | Urgente | 4 h | 9 oct. | UL-03 | Insertar, listar, actualizar y eliminar probados con PostgreSQL; propietario filtrado en consultas. |
| UL-08 | Implementar endpoints CRUD de leads / Karlo | Cristopher | Urgente | 4 h | 10 oct. | UL-04, UL-06, UL-07 | GET/POST/PUT/DELETE responden según contrato; validaciones y errores probados. |
| UL-09 | Crear colección Postman de autenticación / Villa | Cristopher | Alta | 2 h | 9 oct. | UL-04, UL-06 | Colección obtiene CSRF, inicia/cierra sesión y prueba credencial válida/inválida sin guardar contraseñas reales. |
| UL-10 | Ejecutar smoke test del backend / Villa | Cristopher | Alta | 2 h | 9 oct. | UL-06, UL-07 | Recorrido inicial repetible; registrar resultados esperados/obtenidos y defectos con evidencia ficticia. |
| UL-11 | Configurar build y CI inicial / Cristopher | Sebastian 2 | Alta | 3 h | 10 oct. | UL-02 | Pull request ejecuta los checks que realmente existan en backend y frontend; resultado visible. |
| UL-12 | Crear login y sesión en frontend / Sebas | Sebastian 2 | Urgente | 4 h | 10 oct. | UL-05, UL-06 | Pantalla responsive conecta con API; maneja carga, credenciales incorrectas, sesión activa y logout. |
| UL-13 | Crear lista y formulario de leads / Sebas | Karlo | Urgente | 4 h | 11 oct. | UL-05, UL-08, UL-12 | Alta, lista, búsqueda/filtros definidos, edición y validaciones conectadas a API; escritorio/móvil revisados. |
| UL-14 | Implementar bitácora interna por lead / Sebastian 2 | Karlo | Alta | 3 h | 11 oct. | UL-03, UL-04, UL-06 | GET/POST de notas; 1–2.000 caracteres, orden cronológico, dueño/lead verificados y texto plano. |
| UL-15 | Diseñar e integrar detalle y bitácora / Sebas | Sebastian 2 | Alta | 4 h | 12 oct. | UL-05, UL-13, UL-14 | Detalle muestra contacto, etapa, seguimiento y notas internas; agrega entradas; sin apariencia de chat externo engañosa. |
| UL-16 | Ejecutar pruebas API CRUD y notas / Villa | Karlo, Sebastian 2 | Alta | 3 h | 12 oct. | UL-08, UL-09, UL-14 | Colección prueba GET/POST/PUT/DELETE, casos inválidos, límites, CSRF y asociación lead; adjunta evidencia sanitizada. |
| UL-17 | Implementar etapa, seguimiento y filtros / Karlo | Cristopher | Urgente | 4 h | 12 oct. | UL-08 | Estados permitidos, fecha opcional, atrasados/hoy y propietario tienen tests automatizados. |
| UL-18 | Crear dashboard conectado a API / Sebastian 2 | Sebas | Alta | 3 h | 13 oct. | UL-08, UL-14, UL-17 | Totales por etapa, pendientes hoy/atrasados y enlaces filtrados usan datos reales. |
| UL-19 | Conectar dashboard responsive / Sebas | Sebastian 2 | Alta | 3 h | 13 oct. | UL-12, UL-13, UL-18 | Tarjetas y pendientes mantienen jerarquía visual; datos, estados vacíos y errores funcionan en móvil. |
| UL-20 | Desplegar app y PostgreSQL de prueba / Cristopher | Sebastian 2 | Urgente | 4 h | 13 oct. | UL-03, UL-06, UL-08, UL-11 | Despliegue Railway HTTPS y DB conectada; costo/límite aprobado previamente; persistencia verificada. |
| UL-21 | Prueba manual end-to-end y móvil / Villa | Sebas, Cristopher | Alta | 4 h | 14 oct. | UL-13, UL-15, UL-17, UL-19, UL-20 | Recorrido desde login hasta cerrar lead probado en navegador y viewport móvil; defectos reportados con pasos. |
| UL-22 | Corregir defectos priorizados / Responsables de módulo | Cristopher | Urgente | 4 h | 14 oct. | UL-16, UL-21 | Errores críticos/altos tienen dueño; correcciones revisadas por PR y Villa repite escenarios afectados. |
| UL-23 | Revisar seguridad, datos y recuperación / Cristopher | Karlo, Sebastian 2 | Urgente | 3 h | 15 oct. | UL-20, UL-22 | Secretos protegidos, rutas privadas verificadas, persistencia comprobada y respaldo documentado. |
| UL-24 | Completar informe y capturas / Villa | Cristopher | Alta | 3 h | 15 oct. | UL-16, UL-21, UL-23 | Informe casi terminado actualizado con evidencias reales sanitizadas; no inventar resultados. |
| UL-25 | Preparar video y ensayo de exposición / Villa | Todo el equipo | Alta | 3 h | 16 oct. | UL-20, UL-24 | Demo ficticia reproducible; video de 3–5 min y cada integrante puede explicar su aporte. |
| UL-26 | Validar candidato de MVP / Todo el equipo | Cristopher | Urgente | 2 h | 16 oct. | UL-22, UL-23, UL-24, UL-25 | URL, pruebas principales, respaldo, documentación y presentación comprobados; no quedan errores bloqueantes conocidos. |

## Secuencia y límites de alcance

El primer recorrido depende de UL-06, UL-07 y UL-08. La interfaz de lista y
login puede avanzar con contratos y datos ficticios, pero UL-15 y UL-18 requieren
API conectada para considerarse terminados.

Si la carga supera la disponibilidad real, recorten gráficos, animaciones y
decoración. No retiren seguridad, BCrypt, CSRF, persistencia PostgreSQL,
operaciones REST evaluadas, bitácora básica ni verificación de despliegue.

No añadir proyectos, roles, mensajería, IA, automatizaciones, notificaciones
push ni reportes de crecimiento en este MVP.

## Publicación en Linear

Este archivo es una propuesta local. Antes de crear tareas, revisen en Linear
que el proyecto sea el correcto, confirmen miembros y busquen duplicados.
Confirmen también el ciclo, estados, prioridades y si Linear está configurado
para estimar horas. Si su escala nativa representa puntos, mantengan las horas
en la descripción y no las conviertan silenciosamente a puntos.
