# Entrega académica

Este checklist traduce el plan del curso de Desarrollo de Servicios Web II en
productos y evidencia verificable para AP1 y la entrega final.

## Requisitos del curso

- Backend Java con Spring Data, Lombok, Spring MVC y Spring Security.
- Login REST con usuario en base de datos y contraseña hasheada con BCrypt.
- Servicios REST GET, POST, PUT y DELETE con persistencia.
- Aplicación publicada en la nube; PostgreSQL recomendado por el plan.
- Informe editable, desarrollo, presentación y video demo estructurado de
  3–5 minutos. Confirmar con el docente formato técnico del video.

Railway reemplaza la recomendación Heroku para hosting; PostgreSQL se conserva.
Confirmar con el profesor que acepta el proveedor alternativo.

## AP1 y demostración técnica

La rúbrica AP1 prioriza login REST (6 puntos) y pruebas de acceso a datos
(insertar, actualizar, eliminar y listar; 6 puntos). Preparar:

- Login válido e inválido probado con Postman.
- Evidencia sanitizada de password almacenada como hash BCrypt.
- Pruebas de las cuatro operaciones de acceso a datos.
- Informe AP1 actualizado con resultados reales, no solo plan previsto.

## Informe final

Respetar formato del plan: A4, márgenes superior/inferior 3 cm, laterales
2,5 cm, Arial 11 e interlineado simple. Incluir carátula con título, curso,
profesor, ciclo, aula, semestre, coordinador e integrantes.

Estructura: resumen, introducción, diagnóstico SEPTE con fuentes citadas al pie,
objetivos SMART, justificación y beneficiarios, definición/alcance,
productos/entregables, conclusiones (máximo 3), recomendaciones (máximo 3),
glosario, bibliografía y anexos pertinentes.

Actualizar el documento AP1 antes de reutilizarlo:

- Usar nombre UrLeads consistentemente, incluido el proceso Bizagi.
- Corregir nombre del coordinador si corresponde (Cristopher, no Christopher).
- Actualizar equipo, responsabilidades y stack frontend.
- Sustituir Heroku por Railway si el docente lo acepta.
- Completar tablas/capturas con resultados reales.
- Describir BCrypt como hash de contraseña, aunque el material académico diga
  “cifrada”.
- Revisar fuentes, notas al pie y vigencia antes de la entrega.

Villa confirmará si el docente acepta `.docx`/`.pptx`, ya que el plan indica
`.doc`/`.ppt` editable.

## Presentación y video

- La sustentación en grupo no es reemplazada por el video.
- El video debe durar 3–5 minutos; confirmar resolución, formato y plataforma.
- Ensayar una demo pública con datos ficticios y conexión disponible.
- Cada integrante explica su responsabilidad y al menos una decisión técnica.
- No mostrar contraseñas, cookies ni información personal real.

## Guion sugerido para el video

1. Problema y usuario objetivo.
2. Login y privacidad.
3. Crear/buscar lead, agregar nota, cambiar etapa y programar seguimiento.
4. Dashboard en escritorio y móvil.
5. Vista breve de API, PostgreSQL y despliegue.
6. Impacto esperado y cierre.
