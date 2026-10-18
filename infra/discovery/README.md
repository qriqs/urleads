# Discovery Service (Eureka Server)

Servidor de descubrimiento autónomo para la arquitectura de microservicios Backend V2 de UrLeads, basado en Spring Cloud Netflix Eureka Server.

## Características

- **Tecnología:** Java 21, Spring Boot 3.3.13, Spring Cloud 2023.0.6 (`spring-cloud-starter-netflix-eureka-server`).
- **Nombre de la aplicación:** `discovery-service`
- **Puerto HTTP por defecto:** `8761`
- **Endpoints de servicio:**
  - Dashboard Eureka: `http://localhost:8761/`
  - Endpoint de registro y APIs: `http://localhost:8761/eureka/` (desarrollo local) y `http://discovery:8761/eureka/` (red interna Docker Compose).
- **Modo autónomo:**
  - `eureka.client.register-with-eureka=false`
  - `eureka.client.fetch-registry=false`
  - Sin base de datos relacional, JPA ni RabbitMQ.
  - Componente de infraestructura interna local: sin exposición pública ni cloud.

## Construcción y pruebas locales

Desde este directorio (`infra/discovery`):

```bash
# Compilar y ejecutar pruebas unitarias/de configuración
./mvnw clean test

# Empaquetar el artefacto JAR ejecutable
./mvnw clean package

# Verificación completa
./mvnw --batch-mode --no-transfer-progress verify
```

## Ejecución local (Standalone)

```bash
./mvnw spring-boot:run
```

Una vez iniciado, la consola del registro está disponible en:
- `http://localhost:8761/`

## Contenedorización

El servicio incluye su propio `Dockerfile` multi-stage ligero basado en Eclipse Temurin 21 (Alpine).

Para construir la imagen Docker de forma independiente:

```bash
docker build -t urleads-discovery-service:latest .
```

## Alcance y limitaciones de este hito

Este componente cubre exclusivamente el **hito temprano de infraestructura Eureka Server**:
- Provee el servicio independiente de descubrimiento y su empaquetado Docker.
- **No incluye ni valida en este hito:** Docker Compose raíz integrado, RabbitMQ, registro activo de microservicios reales (`crm-service`, `comercial-service`, `agenda-service`), ni persistencia de volúmenes compartidos. Dichas integraciones corresponden a la fase final de integración C14 una vez disponibles los servicios de negocio completos.
