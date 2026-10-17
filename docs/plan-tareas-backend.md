# Propuesta de tareas de backend para ampliar UrLeads

Este documento propone una ampliación de backend organizada en tres grupos:
Cristopher, Sebas y Alex/Alexander. Las tareas son propuestas para acordar; no
indican trabajo completado ni cambian por sí solas las responsabilidades
existentes en Linear. El documento se centra exclusivamente en servicios,
persistencia, contratos, seguridad, integración y pruebas de backend. No asigna
implementación de React ni de otras interfaces.

La arquitectura actual es un backend Spring Boot con PostgreSQL y una interfaz
React integrada en el JAR. La propuesta empieza por consolidar esa base y después
añade servicios solo cuando una función lo justifique. Las tecnologías citadas
son opciones para evaluar, no una lista de dependencias que se deba instalar
completa.

## Contexto actual observado

La propuesta debe distinguir la implementación actual de las extensiones
pendientes.

| Área | Estado observado en el repositorio |
|---|---|
| Backend | Java 21 y Spring Boot 3.3.13, con controladores, servicios, DTO, entidades, repositorios y pruebas. |
| Leads | CRUD, búsqueda, paginación, filtros por etapa y seguimiento implementados. |
| Dashboard | Endpoint y servicio de métricas implementados. |
| Bitácora | API Spring para crear y consultar notas; la persistencia de las notas en la interfaz requiere revisión antes de usarla como base para eventos. |
| Seguridad | Spring Security, BCrypt, sesiones de servidor, cookies de sesión y protección CSRF configurados. |
| Persistencia | PostgreSQL, JPA y migraciones Flyway presentes. |
| Construcción | Maven Wrapper, Dockerfile de backend, Docker Compose para PostgreSQL y CI de GitHub Actions presentes. |
| Pruebas API | El repositorio incluye pruebas automatizadas. No se encontró una colección Postman versionada en la lista de archivos revisada; el equipo puede tenerla en su workspace. |
| Microservicios y mensajería | No se encontraron dependencias ni configuración de OpenFeign, RabbitMQ, MQTT/Mosquitto, Eureka, Gateway, Circuit Breaker, SOAP, WebSocket ni OpenAPI/Swagger UI. |

Antes de ampliar, conviene alinear las tareas con el estado real del código y
actualizar contratos o documentación que todavía describan funciones
implementadas como pendientes.

## Objetivo propuesto

El objetivo es entregar un flujo backend demostrable y comprobable, no acumular
tecnologías aisladas:

1. Un cliente externo registra un lead mediante un contrato de integración
   acordado.
2. El CRM valida y persiste el lead respetando el propietario y las reglas de
   negocio.
3. El CRM registra un evento de forma fiable.
4. El servicio responsable procesa el evento y guarda una notificación o un
   recordatorio.
5. Los endpoints permiten consultar el resultado y el historial de
   procesamiento.
6. Las pruebas verifican respuestas, seguridad, fallos y recuperación.

SOAP/XML es una opción si la rúbrica exige SOAP o el equipo necesita simular un
sistema legado. Si ese requisito no se confirma, la importación de Excel y una
API REST documentada pueden ser un alcance más sencillo. Esta decisión queda
pendiente de validación con el curso y el equipo.

## Reglas para elegir tecnologías

Antes de añadir dependencias, comparen cada opción con el contrato académico,
las versiones del proyecto, los recursos disponibles y el tiempo de pruebas.

| Necesidad | Opciones que se pueden evaluar | Orientación |
|---|---|---|
| Llamada HTTP entre servicios | OpenFeign o cliente HTTP de Spring | Usar OpenFeign si lo exige la asignatura o su cliente declarativo aporta valor. Spring Cloud OpenFeign está considerado feature-complete por el proyecto Spring; evaluar HTTP Service Clients para una decisión nueva y confirmar compatibilidad con Spring Boot 3.3.13. |
| Procesamiento asíncrono | RabbitMQ con Spring AMQP (`spring-rabbit`) | Elegirlo si una solicitud no debe esperar el procesamiento de notificaciones, importaciones o integraciones. Acordar persistencia, confirmaciones, reintentos, idempotencia y manejo de mensajes fallidos. |
| Mensajería MQTT | Mosquitto y un cliente MQTT | Elegirlo solo si hay dispositivos o productores MQTT. Mosquitto es un broker MQTT, no un reemplazo directo de RabbitMQ con AMQP. No añadir ambos sin dos protocolos/casos de uso reales. |
| Tolerancia a fallos | Timeouts y, si hace falta, Circuit Breaker con Resilience4j | Primero fijar timeouts. Añadir circuit breaker para llamadas remotas con fallos repetidos y definir qué respuesta segura se ofrece; no fingir que una operación fallida tuvo éxito. |
| Descubrimiento de servicios | DNS/nombres de Docker Compose o Eureka | Para pocos servicios en Compose, empezar con nombres DNS configurados. Considerar Eureka si necesitan demostrar descubrimiento dinámico o la rúbrica lo exige. Eureka no es un gateway. |
| Entrada y enrutamiento de API | Spring Cloud Gateway o proxy del despliegue | Un API Gateway recibe y enruta solicitudes; es distinto de Eureka. Incorporarlo solo si hay varios servicios y una necesidad de entrada común, políticas o enrutamiento centralizado. |
| Documentación REST | OpenAPI y Swagger UI | Añadir documentación navegable para contratos REST y probar que refleja rutas, errores y autenticación reales. No sustituye tests ni documentación SOAP/WSDL. |
| API SOAP/XML | Spring Web Services, WSDL/XSD y SoapUI | Elegirlo si SOAP/XML es requisito de curso o una integración externa lo necesita. Definir operaciones, esquema, autenticación, validación y errores antes de implementar. |
| Actualizaciones al navegador | WebSocket, con o sin STOMP según el contrato elegido | Usarlo para avisos inmediatos; conservar REST para consultas y cambios durables. Autenticar el handshake, autorizar suscripciones y recuperar por REST los avisos perdidos. |
| Pruebas manuales de API | Postman y SoapUI | Usar Postman para REST y pruebas WebSocket que sean compatibles con el protocolo elegido; SoapUI para SOAP/WSDL. Mantener evidencia sanitizada y aserciones automatizadas cuando sea posible. |
| Autenticación del navegador | Sesión Spring Security y CSRF actuales | Conservar como punto de partida. Un token CSRF no es un token de acceso JWT. No migrar a JWT solo por dividir servicios. |
| Identidad entre servicios | Credenciales de servicio acotadas, token firmado validado por destinatario o mecanismo acordado | Decidirlo antes de exponer APIs internas. Usar TLS donde corresponda, secretos fuera de Git, permisos mínimos y validación de propietario. No confiar en un `userId` arbitrario del cliente. |

### Distinciones importantes

- **Spring Security ya está configurado.** No se debe planificar como si el
  proyecto careciera de login o CSRF. Sí falta decidir cómo autenticar llamadas
  servicio a servicio si se separan componentes.
- **CSRF y JWT cumplen propósitos distintos.** CSRF protege solicitudes
  asociadas a una sesión del navegador; no es el token para OpenFeign ni para
  una integración externa.
- **RabbitMQ y Mosquitto no son sinónimos.** RabbitMQ suele utilizar AMQP en este
  tipo de integración; Mosquitto es un broker MQTT. Se elige el protocolo según
  los productores y consumidores reales.
- **Eureka y API Gateway no son equivalentes.** Eureka registra y descubre
  instancias; Gateway enruta solicitudes entrantes.
- **Circuit Breaker no reemplaza timeout ni persistencia.** Interrumpe llamadas
  remotas repetidamente fallidas; no guarda mensajes ni garantiza reintentos.
- **WebSocket no reemplaza la API REST.** Sirve para avisos en vivo. El backend
  debe persistir lo que los usuarios necesitan ver después de desconectarse.
- **Postman y SoapUI prueban contratos, pero no sustituyen pruebas automáticas
  de servicio, seguridad, base de datos y consumidor de mensajes.**

## Reparto de trabajo propuesto

Las tres personas trabajan en backend. Cada grupo entrega código, pruebas y
documentación de su módulo; el grupo de integración coordina los contratos
compartidos. Los nombres y límites deben confirmarse con el equipo antes de
reflejarse en Linear.

### Grupo 1: Cristopher — arquitectura e integración

Cristopher coordina los contratos y la integración técnica de los servicios,
sin asumir silenciosamente el trabajo ajeno.

#### Tarea C1 — Acordar límites de servicio y contratos

- **Objetivo:** decidir si se conserva un monolito modular o se extraen
  servicios independientes para notificaciones e integraciones.
- **Entregables:** diagrama de componentes, límites de datos, contratos REST,
  contratos de eventos y decisión documentada sobre tecnologías obligatorias,
  opcionales y descartadas.
- **Criterios de aceptación:** cada servicio tiene una responsabilidad y
  propietario de datos definidos; no se permite leer directamente las tablas
  de otro servicio; el flujo incluye errores y autenticación interna.
- **Dependencias:** confirmar requisitos del docente y alcance de producto.

#### Tarea C2 — Preparar ejecución local e integración

- **Objetivo:** permitir que los servicios acordados arranquen localmente con
  configuración reproducible.
- **Entregables:** configuración de Docker Compose actualizada solo después de
  aprobar el diseño, variables de entorno de ejemplo sin secretos, healthchecks
  y guía de inicio.
- **Criterios de aceptación:** el equipo puede iniciar los componentes y
  comprobar su salud; ningún secreto real queda en Git; el entorno de pruebas
  no apunta a la base de datos de desarrollo.
- **Dependencias:** C1 y selección de broker/gateway si aplica.

#### Tarea C3 — Seguridad de llamadas entre servicios

- **Objetivo:** definir y probar cómo se autentican OpenFeign, el adaptador
  externo y las conexiones WebSocket.
- **Entregables:** contrato de identidad de servicio, reglas de autorización,
  configuración segura y pruebas de solicitudes permitidas y rechazadas.
- **Criterios de aceptación:** los servicios rechazan credenciales ausentes,
  inválidas o no destinadas a ellos; el propietario no se acepta como dato
  confiable desde el navegador; CSRF de sesión se conserva para la UI cuando
  corresponda.
- **Dependencias:** C1; coordinación con Alex para notificaciones y WebSocket.

#### Tarea C4 — Contrato de integración externa y evidencia

- **Objetivo:** acordar el caso de importación de Excel o la integración SOAP,
  sin mantener sincronización automática con otro CRM.
- **Entregables:** contrato de entrada, validaciones, códigos de error,
  autenticación, colección de pruebas Postman o proyecto SoapUI según protocolo
  elegido y guía de datos ficticios.
- **Criterios de aceptación:** los casos válidos e inválidos se pueden repetir;
  las operaciones no filtran secretos ni datos de otro propietario.
- **Dependencias:** C1 y acuerdo con Sebas sobre el contrato de creación de
  leads.

### Grupo 2: Sebas — servicios comerciales backend

Sebas implementa exclusivamente backend para ampliar los datos y flujos
comerciales. Las pantallas React quedan fuera de estas tareas.

#### Tarea S1 — Revisar y estabilizar las reglas actuales de leads

- **Objetivo:** alinear etapas, filtros, métricas y validaciones con el modelo
  actual `NUEVO`, `EN_SEGUIMIENTO` y `CERRADO`.
- **Entregables:** reglas de dominio consistentes, corrección de consultas y
  pruebas de repositorio/API relacionadas.
- **Criterios de aceptación:** ninguna consulta depende de etapas no admitidas;
  los cierres y fechas respetan las reglas documentadas; se comprueban búsqueda,
  filtros y aislamiento por propietario.
- **Dependencias:** revisión conjunta del estado actual y contrato de API.

#### Tarea S2 — Catálogo de servicios y precios

- **Objetivo:** mantener servicios ofrecidos, descripción, estado y precio para
  utilizarlos en cotizaciones.
- **Entregables:** modelo, migración Flyway, DTO, validación, repositorio,
  servicio, controlador y pruebas automatizadas de CRUD.
- **Criterios de aceptación:** persistencia, validación de importes y moneda,
  estados permitidos, acceso autorizado y errores consistentes. El contrato no
  depende de la interfaz.
- **Dependencias:** S1 y definición del modelo de cotización.

#### Tarea S3 — Cotizaciones y cálculo de importes

- **Objetivo:** crear propuestas asociadas a un lead y calcular sus importes a
  partir de conceptos del catálogo.
- **Entregables:** modelo de cotización y líneas, migraciones, DTO, endpoints,
  reglas de totales y pruebas automatizadas.
- **Criterios de aceptación:** se calcula un subtotal/totales determinista;
  estados de borrador, enviada, aceptada y rechazada tienen transiciones
  definidas; se guarda el precio acordado en la cotización para preservar su
  histórico; no se altera el precio del catálogo al editar una propuesta.
- **Dependencias:** S2; definir con Cristopher si el cálculo se queda dentro del
  servicio comercial o se expone como SOAP para una rúbrica explícita.

#### Tarea S4 — Motivo y resultado de cierre

- **Objetivo:** registrar si una oportunidad se ganó o perdió y el motivo, sin
  confundir un lead cerrado con una venta ganada.
- **Entregables:** reglas de cierre, campos y migración si hace falta, DTO/API y
  pruebas de transiciones.
- **Criterios de aceptación:** cada estado final tiene significado inequívoco;
  los reportes distinguen ganadas, perdidas y descartadas; no se crean estados
  que contradigan el contrato vigente sin aprobación.
- **Dependencias:** S1 y definición de cotizaciones.

### Grupo 3: Alex/Alexander — agenda, eventos y notificaciones

Alex implementa backend para recordatorios, tareas y avisos. WebSocket y el
broker se incorporan solo si el contrato de producto y operación los necesita.

#### Tarea A1 — Tareas y citas asociadas a leads

- **Objetivo:** modelar acciones y reuniones con vencimiento, estado y relación
  con un lead.
- **Entregables:** migraciones Flyway, DTO, validaciones, repositorios,
  servicios, controladores y pruebas de persistencia y seguridad.
- **Criterios de aceptación:** se pueden crear, consultar, actualizar y completar
  tareas; las citas validan fecha/hora y estados; cada consulta respeta al
  propietario; cancelar/eliminar tiene una regla definida.
- **Dependencias:** contrato de estados y fechas; coordinación con S1.

#### Tarea A2 — Recordatorios persistentes

- **Objetivo:** generar avisos para tareas, citas o seguimientos próximos y
  conservarlos para usuarios desconectados.
- **Entregables:** modelo de notificación, migraciones, servicio de generación,
  endpoints para listar y marcar leída, y pruebas de persistencia e idempotencia.
- **Criterios de aceptación:** no se generan avisos duplicados por la misma regla
  y periodo; el usuario puede consultar avisos después de reiniciar el backend;
  posponer una notificación no cambia por accidente la fecha comercial del lead.
- **Dependencias:** A1; decisión sobre mecanismo de programación.

#### Tarea A3 — Procesamiento asíncrono, solo si se aprueba RabbitMQ

- **Objetivo:** procesar eventos de lead o recordatorio sin bloquear la
  transacción HTTP principal.
- **Entregables:** nombres/versionado de eventos, publicador, consumidor,
  configuración de confirmaciones, idempotencia, reintentos limitados y manejo
  de mensajes que agotan reintentos.
- **Criterios de aceptación:** un mensaje repetido no crea efectos duplicados;
  un consumidor caído no da por completada la operación; el estado y los errores
  pueden verificarse; se documenta qué ocurre al detener y reanudar el broker.
- **Dependencias:** C1, A2 y decisión comparativa RabbitMQ vs. alternativa.
- **Nota:** evaluar el patrón transactional outbox si perder un evento entre la
  transacción PostgreSQL y la publicación al broker no es aceptable.

#### Tarea A4 — Actualizaciones WebSocket, solo si aportan valor

- **Objetivo:** avisar en vivo sobre notificaciones nuevas sin reemplazar los
  endpoints REST ni la persistencia.
- **Entregables:** endpoint WebSocket y, si se decide, contrato STOMP;
  autenticación de conexión, autorización de suscripción y pruebas.
- **Criterios de aceptación:** no se pueden escuchar avisos de otro usuario;
  una desconexión no pierde notificaciones persistidas; REST permite recuperar
  avisos pendientes; se definen heartbeat y comportamiento de reconexión.
- **Dependencias:** A2 y C3.

## Dependencias y secuencia sugerida

La secuencia prioriza decisiones reversibles y funciones comprobables antes de
añadir infraestructura distribuida.

| Etapa | Trabajo | Resultado para continuar |
|---:|---|---|
| 0 | Confirmar rúbrica, producto, responsable, formatos exigidos y tiempo disponible. | Lista de requisitos obligatorios y opcionales. |
| 1 | C1 y S1; corregir inconsistencias actuales y acordar contratos. | Reglas de lead y límites de servicio aprobados. |
| 2 | S2 y A1; implementar catálogo básico, tareas/citas y pruebas de datos. | Funciones backend persistentes con contratos revisables. |
| 3 | S3, S4 y A2; cotizaciones, cierres y notificaciones persistentes. | Recorrido comercial con estados y pruebas integradas. |
| 4 | C3 y una integración externa seleccionada: Excel **o** SOAP, según el curso. | Autenticación y errores verificables en Postman o SoapUI. |
| 5 | Evaluar RabbitMQ, Circuit Breaker, WebSocket y Gateway por necesidad. | Solo se agregan componentes con criterios de aceptación propios. |
| 6 | Ejecutar pruebas integradas y preparar evidencia sanitizada. | Flujo reproducible, fallos y recuperación documentados. |

No se recomienda introducir RabbitMQ, Mosquitto, Eureka, Gateway, OpenFeign,
SOAP y WebSocket en una sola entrega sin separar fases y demostrar cada
componente. Si la rúbrica exige una tecnología concreta, se ajusta la secuencia
para cumplirla con el menor alcance adicional.

## Criterios transversales de backend

Todas las tareas deben respetar las reglas de privacidad y calidad del
repositorio.

- Definir DTO y contratos antes de integrar consumidores; no exponer entidades
  JPA como respuestas públicas.
- Validar entradas en el servidor y usar migraciones Flyway revisadas.
- Mantener el aislamiento por propietario en operaciones de lead, tareas,
  citas, cotizaciones y notificaciones.
- No exponer contraseñas, cookies, tokens, secretos, datos personales reales ni
  trazas internas en respuestas, fixtures o logs.
- No registrar texto de bitácora ni mensajes completos en eventos si no son
  necesarios para el consumidor.
- Añadir pruebas de servicio/controlador y pruebas con PostgreSQL aislado para
  reglas de persistencia relevantes.
- Usar Postman para verificación funcional REST; usar SoapUI para SOAP si ese
  protocolo se aprueba. Las pruebas manuales no reemplazan las pruebas
  automatizadas.
- Comprobar compatibilidad de las dependencias con Java 21 y Spring Boot
  3.3.13 antes de cambiar el POM. No actualizar versiones sin acuerdo del equipo.
- Mantener el build y Docker Compose reproducibles; cualquier nuevo servicio
  debe tener configuración local, healthcheck y documentación de arranque.

## Plantilla para convertir una propuesta en ticket

Antes de publicar una tarea en el tablero, completen los campos siguientes.

| Campo | Contenido por definir |
|---|---|
| Responsable | Cristopher, Sebas o Alex/Alexander, confirmado por el equipo. |
| Objetivo | Cambio backend concreto y verificable. |
| Contrato | Rutas, DTO, eventos o mensajes y errores. |
| Persistencia | Entidades/tablas y migración necesaria. |
| Seguridad | Sesión, autorización, propietario y autenticación entre servicios. |
| Pruebas | Automatizadas, integración con base aislada y colección manual si corresponde. |
| Dependencias | Decisiones y módulos que deben completarse antes. |
| Aceptación | Resultados observables para dar la tarea por terminada. |
| Tecnología | Seleccionada con justificación; registrar alternativas consideradas. |

## Decisiones pendientes del equipo

Estas decisiones no se consideran aprobadas por este documento.

- Confirmar qué requisitos tecnológicos son obligatorios para el curso.
- Confirmar si la importación Excel puede sustituir una integración SOAP, o si
  ambos requisitos deben demostrarse.
- Elegir RabbitMQ o una alternativa solo después de definir productor,
  consumidor, eventos y requisitos de entrega.
- Decidir si OpenFeign es requisito explícito; si no, comparar con clientes HTTP
  de Spring compatibles con la versión actual.
- Confirmar si se requiere JWT para servicio a servicio. La seguridad de sesión,
  BCrypt y CSRF actuales ya están configurados para el backend existente.
- Evaluar si Eureka es necesario; separar esa decisión de la de API Gateway.
- Confirmar si WebSocket debe entregar avisos en vivo y si Postman será la
  herramienta de verificación manual acordada.
- Confirmar nombres y asignaciones finales de Sebas y Alex/Alexander antes de
  crear o reasignar tickets en Linear.
