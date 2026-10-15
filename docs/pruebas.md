# Plan de pruebas

Este plan distingue pruebas automatizadas de desarrollo, pruebas de API con Postman y pruebas manuales de producto. Las tres aportan evidencia distinta y ninguna sustituye por completo a las otras.

## Checks en pull request

GitHub Actions ejecuta checks en pull requests hacia `main`, pushes a `main` y ejecuciones manuales. Los jobs de backend y frontend son independientes. Un comando fallido hace fallar su job.

- **Backend:** Java 21, Maven Wrapper y un servicio efímero PostgreSQL 16. Ejecuta desde `backend/`:

  ```bash
  ./mvnw --batch-mode --no-transfer-progress clean verify -Pdb-integration
  ```

  El job configura `TEST_DB_URL` hacia `127.0.0.1` y la base aislada `urleads_test`, junto con credenciales ficticias desechables. No usa la base de desarrollo ni requiere secretos del repositorio.

- **Frontend:** Node.js 22 y Bun 1.3.10. Ejecuta desde `frontend/`:

  ```bash
  bun install --frozen-lockfile
  bun run lint
  bun run build
  ```

  `lint` ejecuta TypeScript con `tsc --noEmit`. No hay un script de pruebas frontend configurado, por lo que CI no inventa uno ni requiere una clave Gemini.

Puedes reproducir localmente los checks frontend con los comandos anteriores. Para backend, sigue el procedimiento de base de datos aislada de [`infra/README.md`](../infra/README.md), usando `urleads_test` y variables `TEST_DB_*`, antes de ejecutar el comando del job.

### Comandos de ejecución local del backend

El backend cuenta con dos suites de verificación según la necesidad de aislamiento.

1. **Build estándar y pruebas unitarias sin base de datos**

   Ejecuta la compilación y los 12 tests unitarios aislados, correspondientes al contexto, health y seguridad web simulada, sin requerir servicios externos.

   Ejecutar desde `backend/` en PowerShell:

   ```powershell
   .\mvnw.cmd clean test
   ```

2. **Pruebas de integración con PostgreSQL (`urleads_test`)**

   Ejecuta las 54 pruebas de integración relacionadas con migraciones Flyway, aislamiento de propietarios y persistencia, mediante el perfil `db-integration` y contra una instancia PostgreSQL aislada.

   Ejecutar desde `backend/` en PowerShell:

   ```powershell
   .\mvnw.cmd --batch-mode --no-transfer-progress clean verify -Pdb-integration `
     "-DTEST_DB_URL=jdbc:postgresql://localhost:5432/urleads_test" `
     "-DTEST_DB_USERNAME=urleads_app" `
     "-DTEST_DB_PASSWORD=sql"
   ```

   Los parámetros `-DTEST_DB_*` deben coincidir con la configuración de integración del proyecto. La base `urleads_test` debe existir y estar disponible antes de ejecutar el comando. Utiliza exclusivamente datos y credenciales de prueba; no apuntes estos comandos a la base de producción.

   En PowerShell, el carácter de acento grave al final de una línea permite continuar el comando en la siguiente. No debe haber espacios después de ese carácter.

3. **Verificación en contenedor Docker**

   Para construir la imagen del backend y validar el proceso de empaquetado, ejecutar desde el directorio que contiene el `Dockerfile`:

   ```bash
   docker build -t urleads-backend:latest -f Dockerfile .
   ```

   Este comando construye la imagen del backend. No sustituye la ejecución de las pruebas automatizadas ni demuestra por sí solo que la aplicación pueda conectarse correctamente a PostgreSQL. Para validar el funcionamiento integrado, utiliza el procedimiento de ejecución definido en la configuración Docker del proyecto.

### Trazabilidad de integración continua (CI)

Las tareas de autenticación, esquema relacional y endpoints privados (UL-06a y UL-06b) cuentan con validación formal aprobada en el pipeline de GitHub Actions.

- **Pull Request integrada:** PR #14 (`UL-06a & UL-06b: Autenticación, sesión y protección CSRF`).
- **Commit SHA de integración:** `[11ad9e8]`.
- **Estado de verificación:** 66 pruebas automatizadas aprobadas (12 estándar/unitarias y 54 de integración con PostgreSQL aislado), con resultado `BUILD SUCCESS`.

Para completar la trazabilidad, reemplaza el marcador del SHA por el hash real del commit integrado y enlaza la PR #14 y su ejecución correspondiente de GitHub Actions. No se debe registrar un SHA supuesto ni presentar una ejecución como aprobada sin comprobar su resultado.

## Pruebas automatizadas de desarrolladores

Karlo y Alexander implementan y mantienen las pruebas del backend en sus módulos. Sebas ejecuta los checks frontend que existan en el proyecto. El reparto exacto entre backend y UI está en Linear y en `docs/backlog.md`.

### Acceso a datos

Con PostgreSQL de prueba, comprobar estas operaciones:

| Operación | Comprobación |
|---|---|
| Insertar | Un lead válido persiste y puede recuperarse. |
| Listar | Se devuelven leads del propietario correcto y filtros esperados. |
| Actualizar | Los campos enviados cambian y los no enviados se conservan según el contrato PUT acordado. |
| Eliminar | Lead y notas se eliminan de forma consistente y transaccional. |

Estas pruebas automatizadas cubren explícitamente la rúbrica académica de insertar, listar, actualizar y eliminar. Una solicitud Postman por sí sola no es una prueba del repositorio.

### Seguridad, reglas y endpoints

- Login válido e inválido; contraseñas almacenadas como hash BCrypt.
- Sesión, logout, cookie, CSRF correcto e incorrecto y endpoints privados.
- Validación de lead, contactos, correo, etapas y fecha de seguimiento.
- Bitácora vacía, límite de 2.000 caracteres, texto con HTML y asociación al lead y propietario correctos.
- Notas en orden cronológico y recurso inexistente o ajeno con respuesta segura.
- Filtros de hoy, atrasados y sin fecha usando fecha local de Lima.
- Respuestas sin secretos, hashes, datos de otros usuarios ni trazas internas.

## Pruebas de API con Postman

Villa mantiene una colección alineada con `docs/api.md`. Cristopher acompaña la configuración de cookies y CSRF. Usar exclusivamente datos ficticios y no guardar credenciales reales en la colección. El smoke test temprano de Villa comprueba solo que el scaffold Spring y PostgreSQL arrancan; no repite estas pruebas funcionales.

La colección debe incluir login válido e inválido, consulta de sesión, protección sin sesión y los métodos GET, POST, PUT y DELETE para leads. Cuando los endpoints de notas estén listos, agregar creación/listado de bitácora, entradas inválidas y notas de un lead ajeno o inexistente.

Para cada solicitud, registrar método/ruta, código HTTP esperado y obtenido, resultado de aserciones y evidencia sanitizada. No mostrar tokens, cookies ni hashes completos.

## Pruebas manuales de producto

Villa usa el navegador para recorrer el producto como usuario y reporta problemas reproducibles. No se espera que Villa escriba pruebas unitarias ni corrija módulos de producción.

La primera ronda de QA (DSW-38) registra resultados y defectos reproducibles antes de las tareas de corrección. Después, Villa vuelve a ejecutar los casos afectados y registra la re-prueba en los issues de corrección. La ronda inicial no espera a que esos issues estén terminados.

Recorridos mínimos:

1. Inicia sesión y cierra sesión; intenta entrar a una ruta privada sin sesión.
2. Crea un lead con correo, y otro solo con teléfono.
3. Busca, filtra, abre el detalle y edita un lead.
4. Agrega varias entradas a la bitácora y confirma que no aparecen en otro lead.
5. Programa un seguimiento, verifica los listados de hoy y atrasados y cambia la etapa.
6. Elimina un lead tras confirmar y revisa el resultado esperado para sus notas.
7. Repite los formularios en móvil y prueba teclado, foco y mensajes de error.

Casos problemáticos incluyen campos vacíos, correo inválido, nota con solo espacios, nota que exceda 2.000 caracteres, contenido con etiquetas HTML, sesión expirada, recurso inexistente y errores de red.

## Registro y seguimiento de defectos

Cada defecto que Villa encuentre debe incluir:

- Título breve y severidad/prioridad propuesta.
- Entorno, navegador y viewport.
- Datos ficticios usados.
- Pasos concretos para reproducir.
- Resultado esperado y resultado observado.
- Captura o respuesta sanitizada si aporta evidencia.
- Estado de re-prueba después de la corrección.

Linear será el tablero de seguimiento. El responsable del módulo corrige el defecto en código; Villa repite los pasos después de la corrección y registra el resultado. Capturar un error sin reproducirlo no confirma su causa. Si la ronda inicial no encuentra defectos relevantes, registra ese resultado en QA.

## Validación final

En pull request se ejecutan los tests, type-check, lint y builds que estén configurados en el repositorio. Antes de exposición, repetir los flujos principales en la URL pública, comprobar persistencia después de reiniciar o desplegar y confirmar que no haya defectos bloqueantes conocidos.

Villa aporta capturas y resultados reales al informe. No publicar contraseñas, cookies, datos de personas reales ni hashes activos.