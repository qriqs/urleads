# Guía de desarrollo: Servicio CRM (Cris)

Esta guía describe el entorno de trabajo, comandos rápidos y el índice de tareas
asignadas a Cris para el microservicio CRM en UrLeads Backend V2.

---

## 1. Información general del servicio

- **Responsable:** Cris
- **Directorio de trabajo:** `backend/`
- **Rama Git:** `feat/crm`
- **Puerto HTTP local:** `8080`
- **Base de datos:** PostgreSQL local (base `urleads` o esquema `crm`)
- **Contratos compartidos:** Consulta [docs/backend-v2/contratos.md](contratos.md)
- **Flujo de ramas:** Consulta [docs/backend-v2/flujo-ramas.md](flujo-ramas.md)

---

## 2. Inicio rápido y comandos locales

```bash
# Cambiar a la rama de trabajo
git switch feat/crm

# Iniciar la base de datos PostgreSQL local
cd infra && docker compose up -d --wait && cd ..

# Ejecutar las pruebas automatizadas del servicio
cd backend
./mvnw clean test

# Arrancar la aplicación en modo desarrollo (puerto 8080)
./mvnw spring-boot:run
```

---

## 3. Matriz de tareas en Linear (15 tareas)

| Código | Tarea | Bloque | Estado | Depende de | Linear |
|:---:|---|:---:|:---:|:---:|:---:|
| **C01** | Preparar CRM independiente y auditar etapas | Principal | `Todo` | - | [DSW-41](https://linear.app/enmanuelprojects/issue/DSW-41) |
| **C02** | Identidad estándar y validación de tokens JWT | Principal | `Todo` | C01 | [DSW-42](https://linear.app/enmanuelprojects/issue/DSW-42) |
| **C03** | Endpoint interno de consulta de lead para Comercial | Principal | `Todo` | C01, C02 | [DSW-43](https://linear.app/enmanuelprojects/issue/DSW-43) |
| **C04** | Publicación fiable de eventos de dominio CRM con outbox | Principal | `Todo` | C01 | [DSW-44](https://linear.app/enmanuelprojects/issue/DSW-44) |
| **C05** | Documentación OpenAPI, Postman y regresión de seguridad CRM | Principal | `Todo` | C01, C02, C03 | [DSW-45](https://linear.app/enmanuelprojects/issue/DSW-45) |
| **C06** | Detección de duplicados por correo y teléfono normalizados | Ampliación | `Backlog` | C01 | [DSW-46](https://linear.app/enmanuelprojects/issue/DSW-46) |
| **C07** | Campos de prioridad y origen con filtros avanzados | Ampliación | `Backlog` | C01 | [DSW-47](https://linear.app/enmanuelprojects/issue/DSW-47) |
| **C08** | Gestión de etiquetas y relaciones por propietario | Ampliación | `Backlog` | C01 | [DSW-48](https://linear.app/enmanuelprojects/issue/DSW-48) |
| **C09** | Historial de cambios y auditoría independiente de notas | Ampliación | `Backlog` | C01 | [DSW-49](https://linear.app/enmanuelprojects/issue/DSW-49) |
| **C10** | Importación acotada de leads desde Excel con plantilla fija | Ampliación | `Backlog` | C01, C06 | [DSW-50](https://linear.app/enmanuelprojects/issue/DSW-50) |
| **C11** | Registro y consulta de historial de importaciones | Ampliación | `Backlog` | C01, C10 | [DSW-51](https://linear.app/enmanuelprojects/issue/DSW-51) |
| **C12** | Flujo de archivar y restaurar leads sin borrado físico | Ampliación | `Backlog` | C01, C03 | [DSW-52](https://linear.app/enmanuelprojects/issue/DSW-52) |
| **C13** | Servicio SOAP/XML mínimo para consulta e ingesta de leads | Ampliación | `Backlog` | C01 | [DSW-53](https://linear.app/enmanuelprojects/issue/DSW-53) |
| **C14** | Configuración de Docker Compose y pipeline de integración local | Integración | `Todo` | C01 | [DSW-54](https://linear.app/enmanuelprojects/issue/DSW-54) |
| **C15** | Verificación integral end-to-end, resiliencia y regresión | Integración | `Todo` | C05, C14, S15, A15 | [DSW-55](https://linear.app/enmanuelprojects/issue/DSW-55) |

---

## 4. Pautas clave de implementación

1. **Reutilización del backend existente:** No reconstruyas el CRUD de leads.
   El código en `backend/` ya cuenta con controladores, servicios y repositorios
   JPA funcionales.
2. **Seguridad y tokens (C02):** Mantén el inicio de sesión y la protección CSRF
   para la interfaz legacy. Añade soporte JWT para autorizar llamadas de API e
   inter-servicio. Genera pares de claves de prueba para JUnit sin subir claves
   privadas de producción al repositorio.
3. **Endpoint interno (C03):** Implementa `GET /internal/v1/leads/{id}`
   retornando únicamente `{ "id": 105, "stage": "EN_SEGUIMIENTO", "nextFollowUp": "2026-10-25" }`.
   Nunca expongas datos de contacto ni notas privadas en este endpoint.
4. **Outbox fiable (C04):** Guarda eventos en tabla `outbox` en la misma
   transacción del lead antes de publicar a RabbitMQ (`urleads.events.v1`).
