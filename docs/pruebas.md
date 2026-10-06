# Plan de pruebas

Este plan distingue pruebas automatizadas de desarrollo, pruebas de API con
Postman y pruebas manuales de producto. Las tres aportan evidencia distinta y
ninguna sustituye por completo a las otras.

## Pruebas automatizadas de desarrolladores

Karlo y Alexander implementan y mantienen las pruebas del backend en sus
módulos. Sebas ejecuta los checks frontend que existan en el proyecto.

### Acceso a datos

Con PostgreSQL de prueba, comprobar estas operaciones:

| Operación | Comprobación |
|---|---|
| Insertar | Un lead válido persiste y puede recuperarse. |
| Listar | Se devuelven leads del propietario correcto y filtros esperados. |
| Actualizar | Los campos enviados cambian y los no enviados se conservan según el contrato PUT acordado. |
| Eliminar | Lead y notas se eliminan de forma consistente y transaccional. |

Estas pruebas automatizadas cubren explícitamente la rúbrica académica de
insertar, listar, actualizar y eliminar. Una solicitud Postman por sí sola no es
una prueba del repositorio.

### Seguridad, reglas y endpoints

- Login válido e inválido; contraseñas almacenadas como hash BCrypt.
- Sesión, logout, cookie, CSRF correcto e incorrecto y endpoints privados.
- Validación de lead, contactos, correo, etapas y fecha de seguimiento.
- Bitácora vacía, límite de 2.000 caracteres, texto con HTML y asociación al
  lead y propietario correctos.
- Notas en orden cronológico y recurso inexistente o ajeno con respuesta
  segura.
- Filtros de hoy, atrasados y sin fecha usando fecha local de Lima.
- Respuestas sin secretos, hashes, datos de otros usuarios ni trazas internas.

## Pruebas de API con Postman

Villa mantiene una colección alineada con `docs/api.md`. Cristopher acompaña la
configuración de cookies y CSRF. Usar exclusivamente datos ficticios y no
guardar credenciales reales en la colección.

La colección debe incluir login válido e inválido, consulta de sesión,
protección sin sesión y los métodos GET, POST, PUT y DELETE para leads. Cuando
los endpoints de notas estén listos, agregar creación/listado de bitácora,
entradas inválidas y notas de un lead ajeno o inexistente.

Para cada solicitud, registrar método/ruta, código HTTP esperado y obtenido,
resultado de aserciones y evidencia sanitizada. No mostrar tokens, cookies ni
hashes completos.

## Pruebas manuales de producto

Villa usa el navegador para recorrer el producto como usuario y reporta
problemas reproducibles. No se espera que Villa escriba pruebas unitarias ni
corrija módulos de producción.

Recorridos mínimos:

1. Inicia sesión y cierra sesión; intenta entrar a una ruta privada sin sesión.
2. Crea un lead con correo, y otro solo con teléfono.
3. Busca, filtra, abre el detalle y edita un lead.
4. Agrega varias entradas a la bitácora y confirma que no aparecen en otro lead.
5. Programa un seguimiento, verifica los listados de hoy y atrasados y cambia
   la etapa.
6. Elimina un lead tras confirmar y revisa el resultado esperado para sus notas.
7. Repite los formularios en móvil y prueba teclado, foco y mensajes de error.

Casos problemáticos incluyen campos vacíos, correo inválido, nota con solo
espacios, nota que exceda 2.000 caracteres, contenido con etiquetas HTML,
sesión expirada, recurso inexistente y errores de red.

## Registro y seguimiento de defectos

Cada defecto que Villa encuentre debe incluir:

- Título breve y severidad/prioridad propuesta.
- Entorno, navegador y viewport.
- Datos ficticios usados.
- Pasos concretos para reproducir.
- Resultado esperado y resultado observado.
- Captura o respuesta sanitizada si aporta evidencia.
- Estado de re-prueba después de la corrección.

Linear será el tablero de seguimiento. El responsable del módulo corrige el
defecto en código; Villa repite los pasos y solo entonces marca la verificación
manual como aprobada. Capturar un error sin reproducirlo no confirma su causa.

## Validación final

En pull request se ejecutan los tests, type-check, lint y builds que estén
configurados en el repositorio. Antes de exposición, repetir los flujos
principales en la URL pública, comprobar persistencia después de reiniciar o
desplegar y confirmar que no haya defectos bloqueantes conocidos.

Villa aporta capturas y resultados reales al informe. No publicar contraseñas,
cookies, datos de personas reales ni hashes activos.
