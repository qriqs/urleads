# Diseño de interfaz

La interfaz es una parte visible del portafolio y debe sentirse coherente y
usable, no ser solo una captura bonita. Sebas lidera el diseño y el frontend.

## Dirección visual

- Dashboard SaaS limpio y moderno, con fondo neutro claro y tarjetas blancas.
- Un solo color de acento (azul o índigo) y colores semánticos para estados.
- Tipografía legible, espacios consistentes e iconografía discreta.
- Tailwind CSS y componentes compartidos de shadcn/ui.
- Responsive: tabla en escritorio y filas/tarjetas legibles en móvil.
- Color nunca será el único modo de indicar etapa o vencimiento.

## Navegación

El sidebar tendrá **Resumen** y **Leads**, además de cerrar sesión. En móvil
se convierte en navegación compacta. No se mostrarán entradas de menú para
funciones que no existen.

## Pantallas

| Pantalla | Contenido y comportamiento |
|---|---|
| Login | Marca, usuario/contraseña, validación y error genérico. |
| Resumen | Conteos por etapa, seguimientos de hoy y atrasados, acción de crear. |
| Leads | Búsqueda, filtros, tabla/lista responsive, vacío y paginación sencilla. |
| Detalle | Contacto, etapa, fecha, acciones y bitácora interna cronológica. |
| Formulario | Crear/editar, errores por campo y confirmación de guardado. |

Los pendientes del dashboard llevan a la lista ya filtrada. Al eliminar, se
pide confirmación. Los botones de envío se deshabilitan mientras se guarda para
evitar duplicados.

## Bitácora del lead

El detalle presenta las entradas como una conversación interna, con autor,
fecha y hora de registro. Un campo con el texto **Escribe una actualización…**
y el botón **Agregar nota** permiten registrar qué pidió el contacto, qué se
conversó, cómo avanza el trabajo o qué queda pendiente. Las entradas son solo
para el usuario de UrLeads: no se envían al cliente y no requieren chat en
tiempo real. El texto se muestra sin interpretar HTML. La bitácora no sustituye
la etapa ni el próximo seguimiento.

## Estados de experiencia

Cada pantalla contempla carga, error recuperable, falta de datos, resultado y
sesión expirada. Se muestran mensajes en lenguaje de usuario; los errores
internos se registran solo en logs sin datos personales innecesarios.

## Accesibilidad y móvil

- Etiquetas asociadas a controles y errores anunciables.
- Foco visible y orden de teclado lógico.
- Contraste legible y tamaños de interacción cómodos.
- Formularios utilizables a 360 px de ancho sin scroll horizontal.
- Fechas mostradas con formato local de Perú.

## Criterios de entrega frontend

1. Revisión del boceto de login, lista y detalle antes de construir todas las
   pantallas.
2. Componentes reutilizables para botón, campo, badge de etapa, modal y tarjeta.
3. Integración con API real y manejo de CSRF/sesión.
4. Prueba en viewport móvil y escritorio, teclado y estados vacíos/errores.
5. Capturas finales con datos ficticios, sin credenciales.
