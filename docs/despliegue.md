# Despliegue en Railway

Railway es la opción seleccionada para publicar el MVP. Se validará pronto para
detectar límites o costos antes de que el despliegue se vuelva una tarea final.

## Servicios

El entorno de producción tendrá dos servicios:

1. Aplicación Spring Boot, que sirve el frontend React compilado y `/api`.
2. PostgreSQL con almacenamiento persistente.

Ambos se comunican por las variables y la red privada que proporcione Railway.
No desplegar un servidor Node separado para React en producción. No exponer la
base de datos al navegador.

## Costos y límites

La página oficial de [precios de Railway](https://railway.com/pricing) indica
prueba inicial de 30 días con US$5 de crédito para cuentas nuevas; el plan Hobby
incluye US$5 de uso mensual y el consumo que exceda el crédito puede cobrarse.
La prueba no garantiza alojamiento sin costo hasta la exposición.

Antes de habilitar cobros, Cristopher debe confirmar elegibilidad, consumo
estimado, almacenamiento, método de pago y límite de gasto que el equipo autoriza.
No contratar ni desplegar en nombre de otra persona sin su aprobación.

## Configuración requerida

Los nombres definitivos se ajustarán al código y a Railway; los valores viven en
el gestor de variables de entorno, nunca en Git.

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL` o variables JDBC equivalentes de Railway
- Usuario y contraseña de base de datos generados por la plataforma
- Usuario inicial y contraseña suministrados de forma segura
- Puerto/healthcheck conforme a la plataforma
- Orígenes permitidos si el diseño final requiere CORS

No copiar secretos en Issues, capturas, documentación o logs. No utilizar una
cuenta compartida como sustituto de gestión segura de credenciales.

## Pasos de publicación

1. Probar app y PostgreSQL localmente.
2. Confirmar el contrato y healthcheck sin datos sensibles.
3. Crear servicios app y base de datos después de aprobar uso/costo.
4. Configurar variables y conexión privada.
5. Desplegar primero una versión de prueba y verificar logs.
6. Ejecutar login, CRUD, CSRF y consulta desde la URL HTTPS.
7. Reiniciar/re-desplegar y comprobar persistencia.
8. Antes de exposición, respaldar y probar recuperación si la plataforma lo permite.

## Criterios de terminado

- URL HTTPS pública estable.
- App conecta a PostgreSQL; la base no está expuesta públicamente.
- Healthcheck saludable y errores visibles solo en logs protegidos.
- Sesión/cookie funciona en navegador y CSRF no está desactivado en producción.
- Los datos sobreviven al reinicio.
- Variables no están en el repositorio y la cuenta demo no usa datos reales.

Como alternativa de contingencia, revisar otro host solo si Railway no cumple
la disponibilidad o costo aprobado. El servidor local es último recurso y no
satisface el requisito de nube del curso.
