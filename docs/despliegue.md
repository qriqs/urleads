# Despliegue en Railway

Esta guía separa la validación temprana de UL-20a del lanzamiento integrado de
UL-20b. UL-20a valida el backend mínimo y PostgreSQL 16; no constituye el
lanzamiento del MVP ni demuestra las funciones del producto. La aceptación del
instructor todavía no está confirmada. Usa únicamente una opción gratuita
aprobada y no cambies a un plan pago ni habilites cargos sin autorización
explícita.

## UL-20a: valida backend y PostgreSQL

UL-20a despliega solamente el backend actual y una base PostgreSQL. Antes de
crear servicios, confirma la elegibilidad de la cuenta y la autorización de
gasto. Esta prueba temprana se ejecutó con autorización expresa del solicitante
antes de recibir la aceptación del instructor; no sustituye esa aceptación para
la entrega académica. La prueba de 30 días y el crédito de US$5 dependen de la
elegibilidad y no están garantizados.

### Evidencia de la prueba temprana del 9 de octubre de 2026

La validación temprana está desplegada en el proyecto `urleads-early-test`, en
el entorno `production`. La aceptación del instructor sigue sin confirmarse.
Este despliegue no es UL-20b ni una aprobación académica del proveedor.

- Backend: [Health HTTPS de prueba](https://backend-production-60395.up.railway.app/api/health),
  servicio `backend`, una réplica activa, healthcheck `/api/health`.
  Solo sirve el scaffold; no hay una interfaz de producto desplegada.
- PostgreSQL: servicio `Postgres`, imagen oficial fijada a
  `postgres:16-alpine` mediante el digest
  `sha256:1a66d744c1b459e13b05a8fca341da84cb63383e99ce262210efee5a319d4551`.
  La base informa PostgreSQL 16.15.
- Almacenamiento: volumen persistente `postgres-volume`, 500 MB, montado en
  `/var/lib/postgresql/data`; `PGDATA` usa el subdirectorio
  `/var/lib/postgresql/data/pgdata`.
- Red: el backend se conectó al host privado `postgres.railway.internal` en el
  puerto `5432`. No hay proxy TCP público para PostgreSQL.
- Salud: `GET /api/health` respondió HTTP 200 con `{"status":"UP"}` antes y
  después de reiniciar el backend.
- Migración: los logs muestran que Flyway aplicó la migración V1 una vez. Tras
  el reinicio, Flyway validó una migración y reportó la versión actual 1 sin
  volver a aplicarla. La verificación independiente mediante SQL de solo
  lectura confirmó `usuario`, `lead`, `nota` y `flyway_schema_history`, con
  exactamente una fila exitosa para V1 después del reinicio del backend.
- Acceso de verificación: el solicitante autorizó una clave SSH temporal para
  consultar la base de forma privada. La clave se revocó y sus archivos locales
  se eliminaron al terminar. Railway confirmó que no quedan claves registradas;
  no se modificó la configuración SSH existente ni se abrió un proxy público.
- Costos observados al validar: aproximadamente US$4.9994 de crédito restante
  de US$5 y US$0.0006 de uso contabilizado, con prueba activa y 30 días indicados
  restantes. Son valores puntuales, no una garantía de disponibilidad futura.
  Railway rechazó los límites solicitados: exige al menos US$5 para una alerta
  y US$10 para un límite duro. No se configuró ese límite superior ni se
  habilitaron pagos (`usageLimit=null`).
- Los dos servicios permanecen activos por solicitud del equipo. No hay
  supervisión continua del consumo: revisa uso y créditos diariamente y pausa
  las pruebas antes de agotar el saldo. El volumen retenido también consume
  créditos, incluso si se detienen los procesos.

Para acceder a las credenciales, abre la sección **Variables** del servicio
Postgres en Railway y consulta `PGPASSWORD`. La contraseña no se copia aquí.

### Prepara PostgreSQL

Configura el almacenamiento y las variables antes de conectar la imagen al
servicio para evitar una inicialización sin volumen persistente.

1. Crea el proyecto Railway después de confirmar las condiciones anteriores.
2. Crea un servicio vacío `Postgres`, sin imagen, y asocia un volumen de 500 MB
   montado en `/var/lib/postgresql/data`.
3. Configura `PGDATA=/var/lib/postgresql/data/pgdata`, `POSTGRES_DB=urleads_test`
   y `POSTGRES_USER=urleads_app`. Define una contraseña segura en
   `POSTGRES_PASSWORD` dentro del gestor de variables, nunca en Git ni en
   argumentos de terminal. Añade `PGDATABASE=${{POSTGRES_DB}}`,
   `PGUSER=${{POSTGRES_USER}}`, `PGPASSWORD=${{POSTGRES_PASSWORD}}` y
   `PGPORT=5432` para las referencias del backend.
4. Conecta la imagen oficial `postgres:16-alpine` fijada al digest verificado
   indicado en la evidencia anterior. No dependas de una plantilla comunitaria
   cuya imagen o volumen no puedas inspeccionar. Después de inicializar la base,
   no cambies a una versión menor ni reutilices el directorio de datos con una
   versión incompatible.
5. En la configuración de red de PostgreSQL, confirma que el proxy TCP público
   está deshabilitado. No basta con omitir un dominio HTTP: la base de datos no
   debe quedar accesible mediante un proxy TCP público.

### Construye y configura el backend

Para construir localmente la imagen con Docker desde la raíz del repositorio,
ejecuta:

```bash
docker build -f backend/Dockerfile -t urleads-backend:local backend
```

El contexto es `backend/`. El archivo `backend/.dockerignore` usa una lista
permitida de archivos de compilación y excluye los demás archivos, incluidos
secretos y reportes locales.

Para cargar el código de una rama que todavía no está publicada en GitHub, no
hagas `git push`. `.dockerignore` filtra la construcción Docker, no garantiza
que Railway CLI excluya esos archivos al subir el código. Prepara un directorio
temporal con las fuentes versionadas y los archivos de construcción revisados.
Desde la raíz del repositorio, usa Bash o Zsh:

```bash
set -o pipefail
UPLOAD_DIR="$(mktemp -d)"
chmod 700 "$UPLOAD_DIR"
git archive --format=tar HEAD \
  backend/src backend/.mvn backend/mvnw backend/pom.xml \
  | tar -x -C "$UPLOAD_DIR" --strip-components=1
cp backend/Dockerfile backend/.dockerignore "$UPLOAD_DIR/"

# Revisa todos los archivos antes de subir; no incluyas secretos ni reportes.
find "$UPLOAD_DIR" -type f

# Sustituye los identificadores por los del proyecto y entorno autorizados.
railway up "$UPLOAD_DIR" --path-as-root \
  --project PROJECT_ID --environment ENVIRONMENT_ID --service BACKEND_SERVICE_ID
```

El proyecto y los servicios deben existir, con las variables y el healthcheck
configurados, antes de ejecutar `railway up`. La copia usa las fuentes de `HEAD`,
no cambios locales sin commit; revisa cualquier diferencia antes de elegir qué
versión desplegar. No copies automáticamente archivos locales adicionales.
La subida no necesita publicar la rama ni incluye el repositorio completo.
Si el CLI disponible no admite
`--path-as-root`, consulta `railway up --help` para verificar la sintaxis
equivalente; no amplíes el contexto para resolverlo.

Si despliegas desde GitHub, configura el directorio raíz del servicio como
`/backend`, de modo que Railway use el Dockerfile y el contexto del backend.
Esta ruta es solo para el flujo conectado a GitHub; no es una instrucción para
el modo CLI de carga local.

Antes de desplegar el servicio backend, configura las variables y el healthcheck
siguientes. Mantén todos los valores privados dentro de Railway:

| Variable | Valor |
| --- | --- |
| `PORT` | Puerto asignado por Railway; localmente la aplicación usa `8080` como valor predeterminado. |
| `DB_URL` | `jdbc:postgresql://${{Postgres.RAILWAY_PRIVATE_DOMAIN}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USERNAME` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |

Sustituye `Postgres` en las referencias si el nombre del servicio de base de
datos es distinto. Configura el healthcheck del backend como `/api/health` y
después despliega. El endpoint responde por HTTP y no prueba conectividad ni
disponibilidad de PostgreSQL.

### Verifica UL-20a

Estas comprobaciones se completaron en la prueba indicada. Repítelas después de
cambiar el despliegue y registra la nueva evidencia:

- [x] La URL HTTPS del backend responde `200 OK` a `GET /api/health` con
      `{"status":"UP"}`. Esta comprobación HTTP solo acredita que responde el
      endpoint de salud.
- [x] En los logs de inicio, Flyway aplica `V1__initial_schema.sql` sin errores.
- [x] Verifica por separado en PostgreSQL que existen las tablas del esquema
      esperado y la tabla `flyway_schema_history` con la migración V1 aplicada.
- [x] Reinicia o vuelve a desplegar el backend y confirma que la migración no se
      vuelve a aplicar y que el esquema y el historial siguen presentes.
- [x] Confirma que el proxy TCP público de PostgreSQL está deshabilitado y que
      el backend se conecta mediante las referencias de red privada.
- [x] Confirma que el volumen persistente sigue asociado al servicio y a su
      ruta de datos configurada.

No declares UL-20a desplegado hasta completar estas comprobaciones reales en
Railway. La persistencia del volumen ayuda a conservar los datos ante reinicios
del contenedor; no la desasocies ni la elimines mientras necesites la evidencia.

### Pausa el consumo de prueba

Cuando las pruebas estén pausadas, elimina la implementación activa tanto del
servicio backend como del servicio PostgreSQL mediante los controles de Railway
para retirar sus despliegues activos. Detener solo el backend no pausa el
consumo del servicio de base de datos. Un volumen retenido puede seguir
consumiendo créditos aunque no haya despliegues activos; verifica el consumo en
el panel. La eliminación de servicios o volúmenes no es una limpieza rutinaria:
es destructiva, y requiere autorización explícita. Eliminar el volumen borra los
datos.

No busques extensiones, cuentas alternas ni otros métodos para evadir límites de
uso o elegibilidad. No actualices a un plan pago ni permitas cobros sin
autorización expresa.

## UL-20b: lista de verificación del lanzamiento integrado

UL-20b es el lanzamiento integrado del MVP y es distinto de la validación
temprana UL-20a. No marques estos requisitos como cumplidos por haber desplegado
el backend mínimo. Antes del lanzamiento, confirma cada punto con la aplicación
integrada y evidencia real:

- [ ] El frontend compilado se sirve desde la aplicación Spring Boot; no depende
      de un servidor Node separado en producción.
- [ ] El inicio y cierre de sesión funcionan con sesiones y cookies `HttpOnly`,
      `Secure` en producción, y protección CSRF habilitada.
- [ ] Las operaciones CRUD de leads funcionan desde la interfaz y la API.
- [ ] Los datos del producto sobreviven a un reinicio del servicio y se verifica
      la persistencia de PostgreSQL.
- [ ] Se prepara y prueba una estrategia de respaldo y recuperación.
- [ ] No se usan datos personales reales en demostraciones o cuentas de prueba.
- [ ] El instructor acepta el proveedor y el costo, y el despliegue usa un plan
      gratuito autorizado sin actualización paga no aprobada.

Consulta los [precios de Railway](https://railway.com/pricing) y el uso real en
el panel antes de cada prueba. La disponibilidad de créditos y la elegibilidad
pueden cambiar.
