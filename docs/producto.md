# Producto y alcance

Este documento define para quién se construye UrLeads, el problema que resuelve
y qué queda dentro de la primera versión.

## Problema

La información de clientes potenciales puede quedar dispersa entre mensajes,
notas y recordatorios. UrLeads centraliza los datos de contacto, las notas, la
etapa comercial y la fecha del próximo seguimiento.

## Usuario objetivo

Una persona independiente o integrante de una micro o pequeña empresa que
necesita administrar sus contactos desde una aplicación web privada. El MVP
admite una cuenta preconfigurada, sin registro público ni roles.

## Propuesta de valor

El usuario puede identificar rápidamente quién necesita seguimiento, qué se
conversó y cuál es la siguiente acción, desde una interfaz clara tanto en
escritorio como en celular.

## Recorrido principal

1. Iniciar sesión.
2. Revisar el dashboard y los seguimientos de hoy o atrasados.
3. Buscar un lead existente o registrar uno nuevo.
4. Abrir su detalle y registrar en la bitácora lo que pidió, qué conversaron y
   cómo avanza el trabajo.
5. Actualizar su etapa y programar el próximo seguimiento.

## Alcance funcional

- Login y logout para una cuenta preconfigurada.
- Crear, listar, consultar, editar y eliminar leads.
- Búsqueda por nombre y filtros por etapa y seguimiento.
- Agregar y consultar una bitácora cronológica de notas internas por lead, con
  apariencia de conversación.
- Cambiar etapa entre `NUEVO`, `EN_SEGUIMIENTO` y `CERRADO`.
- Programar, cambiar o quitar la fecha del próximo seguimiento.
- Mostrar cantidades por etapa, pendientes de hoy y atrasados.
- Diseño responsive con estados de carga, error, vacío y confirmación.

La bitácora la escribe únicamente el usuario autenticado. No es un chat con el
cliente ni un canal de mensajería; cada entrada guarda texto y fecha de registro.
La etapa comercial y la fecha del próximo seguimiento siguen siendo campos
separados de la bitácora.

## Fuera del alcance

- Gestión de proyectos, tareas de equipo o pipeline Kanban avanzado.
- Múltiples usuarios, invitaciones, roles y registro abierto.
- Integraciones con WhatsApp, correo o redes sociales.
- Envíos automáticos, IA, archivos, facturación y reportes avanzados.

Estas funciones pueden evaluarse para una segunda versión de portafolio, después
de terminar, probar y desplegar el MVP.

## Principios del producto

- Priorizar un recorrido completo y confiable sobre una lista amplia de módulos.
- Mostrar estados y mensajes entendibles, no solo datos técnicos.
- Evitar registrar datos personales que no sean necesarios para el seguimiento.
- No mostrar como completada una función que solo tiene datos simulados.
