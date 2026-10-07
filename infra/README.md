# Infraestructura y entorno local

Esta carpeta contiene la configuración de contenedores y servicios para el
desarrollo local de UrLeads mediante Docker Compose.

## Alcance implementado

- **UL-03 (DSW-10):** Servicio de PostgreSQL 16 local en Docker Compose con
  volumen persistente, comprobación de salud (`pg_isready`), puerto configurable
  restringido a `127.0.0.1` y plantilla de variables de entorno `.env.example`.

## Requisitos previos

- **Docker y Docker Compose:** Docker Engine o Docker Desktop instalado y en
  ejecución.
- **Java 21:** Para compilar y ejecutar el backend de Spring Boot.

## Configuración de variables de entorno para desarrollo

Docker Compose requiere que definas la contraseña de la base de datos a través
del archivo `.env`. Nunca almacenes contraseñas reales en control de versiones.

1. Desde la raíz del repositorio, copia la plantilla si aún no has creado tu
   archivo local (no sobreescribas un `.env` existente):

   ```bash
   [ ! -f infra/.env ] && cp infra/.env.example infra/.env
   ```

2. Abre `infra/.env` con tu editor preferido (por ejemplo `nano infra/.env` o en
   tu IDE) y define un valor para `POSTGRES_PASSWORD`:

   ```ini
   POSTGRES_DB=urleads
   POSTGRES_USER=urleads_app
   POSTGRES_PASSWORD=YOUR_PASSWORD_HERE
   POSTGRES_PORT=5432
   ```

   > **Recomendación de seguridad:** Edita el archivo directamente en lugar de
   > pasar contraseñas como argumentos en la línea de comandos para evitar que
   > queden registradas en el historial de tu terminal (`.bash_history` o
   > `.zsh_history`).

> **Importante:** Docker Compose lee automáticamente `infra/.env` cuando se
> ejecuta desde la carpeta `infra/`. Por el contrario, Spring Boot **no** lee
> automáticamente este archivo; debes exportar las variables `DB_*` en tu
> terminal antes de arrancar la aplicación o configurarlas en tu entorno de
> ejecución.

## Gestión del contenedor de base de datos de desarrollo

Para los comandos siguientes, sitúate en la carpeta `infra/`:

```bash
cd /ruta/a/urleads/infra
```

### 1. Iniciar el servicio

Inicia PostgreSQL en segundo plano y espera a que el healthcheck confirme su
disponibilidad:

```bash
docker compose up -d --wait
```

### 2. Verificar el estado

Comprueba que el contenedor esté activo y en estado saludable (`healthy`):

```bash
docker compose ps
```

### 3. Detener y reanudar el contenedor

Existen dos maneras habituales de detener el servicio:

- **Pausa sin retirar contenedores (`stop` / `start`):**
  Para pausar la ejecución conservando el contenedor existente:
  ```bash
  docker compose stop
  ```
  Para reanudarlo posteriormente:
  ```bash
  docker compose start
  ```

- **Retirar contenedores conservando datos (`down` / `up`):**
  Para detener y remover los contenedores y redes, pero **manteniendo intacto el
  volumen nombrado de datos**:
  ```bash
  docker compose down
  ```
  Para volver a levantar el servicio después de un `down`, ejecuta nuevamente:
  ```bash
  docker compose up -d --wait
  ```

> **Advertencia sobre pérdida de datos:** Nunca utilices `docker compose down -v`
> si deseas conservar la información de tu base de datos local, ya que el
> indicador `-v` destruye permanentemente los volúmenes nombrados asociados.

## Conexión desde el backend (Spring Boot)

Antes de iniciar Spring Boot (`./mvnw spring-boot:run` o ejecutar el archivo
JAR), sitúate en la carpeta `backend/` y exporta en tu terminal las variables de
conexión correspondientes:

```bash
cd ../backend

export DB_URL="jdbc:postgresql://localhost:5432/urleads"
export DB_USERNAME="urleads_app"
# Lee la contraseña de forma segura o exportala desde tu entorno:
printf 'Introduce DB_PASSWORD: '
read -r -s DB_PASSWORD
printf '\n'
export DB_PASSWORD
```

En Windows (PowerShell):

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/urleads"
$env:DB_USERNAME = "urleads_app"
$password = Read-Host "Introduce DB_PASSWORD" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $password).Password
```

Al arrancar, Spring Boot aplicará automáticamente las migraciones versionadas
de Flyway (`V1__initial_schema.sql`) para crear las tablas `usuario`, `lead`
y `nota`.

## Alternativa con PostgreSQL nativo (sin Docker)

Si prefieres usar una instalación local nativa de PostgreSQL en tu sistema
operativo (por ejemplo, mediante Homebrew o instalador del sistema) en lugar de
Docker:

1. Crea la base de datos y el usuario en tu servidor PostgreSQL local:

   ```sql
   CREATE USER urleads_app WITH PASSWORD 'tu_contrasena_local_segura';
   CREATE DATABASE urleads OWNER urleads_app;
   ```

2. Exporta las variables `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` correspondientes
   en tu terminal.
3. Inicia el backend normalmente; Flyway ejecutará la migración inicial sobre tu
   instancia nativa.

## Pruebas de integración con base de datos aislada

El repositorio distingue claramente entre pruebas del scaffold y pruebas de
integración con base de datos:

- **Pruebas estándar del scaffold:** `./mvnw clean verify` (desde `backend/`)
  ejecuta las pruebas de contexto y controladores web. Excluye intencionalmente
  la autoconfiguración de DataSource para verificar la inicialización básica de
  Spring sin requerir Docker. Esta exclusión **no** constituye prueba de
  conectividad ni de persistencia con la base de datos.
- **Pruebas de integración (`DatabaseMigrationIT`):** Se ejecutan activando el
  perfil opt-in `db-integration` de Maven Failsafe:

  ```bash
  ./mvnw verify -Pdb-integration
  ```

### Aislamiento estricto y protección fail-fast

Las pruebas de integración **nunca** deben ejecutarse contra la base de datos de
desarrollo (`urleads` en el puerto 5432). Aunque las inserciones de prueba ruedan
sus transacciones hacia atrás, los rollbacks **no revierten ejecuciones de DDL de
migración ni avances de secuencias numéricas**.

Por ello, la suite cuenta con un inicializador de protección
(`TestDatabaseGuardInitializer`) que valida de forma fail-fast antes de cualquier
conexión:
1. Variables obligatorias: `TEST_DB_URL`, `TEST_DB_USERNAME` y `TEST_DB_PASSWORD`.
   No admite fallback a `DB_*`.
2. Host local: debe apuntar a `127.0.0.1` o `localhost`.
3. Nombre de base de datos: debe terminar estrictamente en `_test` (por ejemplo,
   `urleads_test`). Cualquier intento de apuntar a `urleads` o a un servidor
   remoto aborta de inmediato.

### Procedimiento para ejecutar pruebas de integración aisladas

Los comandos siguientes usan Bash o Zsh. Empieza desde la raíz del repositorio
y elige un puerto libre. No reutilices el proyecto Compose de desarrollo.

1. Prepara un archivo temporal con permisos restrictivos y edítalo sin poner
   contraseñas en el historial:

   ```bash
   TEST_ENV_FILE="$(mktemp)"
   chmod 600 "$TEST_ENV_FILE"
   cp infra/.env.example "$TEST_ENV_FILE"
   nano "$TEST_ENV_FILE"
   ```

   En el editor, configura `POSTGRES_DB=urleads_test`,
   `POSTGRES_USER=urleads_test_user`, `POSTGRES_PORT=5433`, y una contraseña
   local de pruebas en `POSTGRES_PASSWORD`. Mantén el archivo en formato de
   asignaciones Bash/Zsh; pon entre comillas simples los valores especiales.
   El archivo es privado y no se añade a Git.

2. Levanta el proyecto aislado y exporta exactamente sus credenciales en la
   misma terminal. Solo carga el archivo que acabas de crear:

   ```bash
   docker compose -p urleads_test --env-file "$TEST_ENV_FILE" \
     -f infra/docker-compose.yml up -d --wait

   set -a
   . "$TEST_ENV_FILE"
   set +a
   export TEST_DB_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/${POSTGRES_DB}"
   export TEST_DB_USERNAME="$POSTGRES_USER"
   export TEST_DB_PASSWORD="$POSTGRES_PASSWORD"

   cd backend
   ./mvnw verify -Pdb-integration
   ```

   Las pruebas verifican migraciones repetibles, claves foráneas, restricciones
   de integridad, límites de notas, y eliminación de notas en cascada.

3. Vuelve a la raíz y retira solo los recursos temporales de este proyecto de
   pruebas. Este comando elimina su volumen; nunca lo uses con el proyecto
   de desarrollo:

   ```bash
   cd ..
   docker compose -p urleads_test --env-file "$TEST_ENV_FILE" \
     -f infra/docker-compose.yml down -v
   rm -f "$TEST_ENV_FILE"
   unset TEST_DB_URL TEST_DB_USERNAME TEST_DB_PASSWORD
   unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD POSTGRES_PORT TEST_ENV_FILE
   ```
