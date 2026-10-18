# Suite de Pruebas Automatizadas SoapUI — Servicio SOAP GetLead (C13)

Este directorio contiene el proyecto formal de pruebas automatizadas con SoapUI para la interfaz SOAP 1.1 del servicio CRM (`UrLeads`), correspondiente al entregable curricular y de verificación de interoperabilidad de la tarea **DSW-53 / C13**.

---

## 1. Archivos en este directorio

- `urleads-crm-soapui-project.xml`: Proyecto estándar XML de SoapUI (compatible con SoapUI 5.x y SmartBear ReadyAPI) que implementa la suite `GetLeadTestSuite` y sus 6 casos de prueba obligatorios.
- `README.md`: Documentación técnica de estructura, parametrización y comandos de ejecución.

---

## 2. Casos de prueba implementados

El suite `GetLeadTestSuite` ejecuta de forma secuencial los siguientes casos de prueba:

| Identificador | Caso de prueba | Descripción y aserciones |
| :--- | :--- | :--- |
| **TC01** | `Valid GetLead Request` | Solicitud `GetLead` con token JWT válido y scope `crm.leads.read`. Valida respuesta `HTTP 200 OK`, conformidad con esquema XSD y presencia exclusiva de `id`, `stage` y `nextFollowUp` sin fuga de datos personales (PII). |
| **TC02** | `Schema Invalid XML Request` | Solicitud con `leadId` que viola las restricciones del esquema XSD (número negativo). Valida respuesta `HTTP 500` con `SOAP Fault Client`. |
| **TC03** | `Missing or Invalid Bearer Token` | Solicitud sin cabecera HTTP `Authorization`. Valida respuesta pre-dispatch `HTTP 401 Unauthorized` con cabecera `WWW-Authenticate: Bearer`. |
| **TC04** | `Token Lacking Required Scope` | Solicitud con token JWT válido pero que carece del scope `crm.leads.read` (e.g. `crm.leads.write`). Valida respuesta pre-dispatch `HTTP 403 Forbidden`. |
| **TC05** | `Indistinguishable Missing and Foreign Lead` | Solicitud consultando un lead inexistente y un lead perteneciente a otro usuario. Valida que ambos casos retornan exactamente el mismo sobre `SOAP Fault` con código `LEAD_NOT_FOUND` y código `HTTP 500`, impidiendo ataques de enumeración. |
| **TC06** | `XXE and External DTD Injection Rejection` | Solicitud conteniendo declaración `<!DOCTYPE` con entidad externa hacia archivos del sistema (`file:///etc/passwd`). Valida rechazo inmediato sin resolución ni fuga de contenidos del sistema operativo. |

---

## 3. Parametrización y seguridad de credenciales

El proyecto está diseñado bajo el principio de **cero secretos en control de versiones**. Todas las propiedades del proyecto son parametrizables:

| Propiedad del proyecto | Valor por defecto | Propósito |
| :--- | :--- | :--- |
| `crm_endpoint` | `http://localhost:8080/ws/leads` | URL destino del endpoint SOAP de CRM. |
| `wsdl_url` | `http://localhost:8080/ws/leads.wsdl` | URL del contrato WSDL publicado. |
| `jwt_token_valid_owner_42` | `${#Project#jwt_token_valid_owner_42}` | Token JWT RS256 sintético con `sub=42` y scope `crm.leads.read`. |
| `jwt_token_insufficient_scope` | `${#Project#jwt_token_insufficient_scope}` | Token JWT RS256 sintético sin scope `crm.leads.read`. |
| `lead_id_success` | `105` | ID de lead perteneciente al usuario 42 en etapa activa. |
| `lead_id_cerrado` | `106` | ID de lead perteneciente al usuario 42 en etapa `CERRADO`. |
| `lead_id_not_found` | `99999` | ID de lead inexistente. |
| `lead_id_foreign` | `200` | ID de lead perteneciente a un usuario distinto. |

---

## 4. Instrucciones de ejecución

### 4.1. Mediante SoapUI CLI (`testrunner.sh`)

Si SoapUI está instalado en el entorno local (e.g., `/opt/SoapUI-5.7.2/bin/testrunner.sh` o en el `PATH`):

```bash
testrunner.sh -s "GetLeadTestSuite" \
  -P crm_endpoint=http://localhost:8080/ws/leads \
  -P jwt_token_valid_owner_42="<TOKEN_JWT_RS256_AQUI>" \
  -P jwt_token_insufficient_scope="<TOKEN_JWT_SIN_SCOPE>" \
  backend/soapui/urleads-crm-soapui-project.xml
```

### 4.2. Mediante la interfaz gráfica de SoapUI

1. Abra la aplicación de escritorio **SoapUI**.
2. Seleccione **File -> Import Project** y cargue el archivo `backend/soapui/urleads-crm-soapui-project.xml`.
3. En las propiedades del proyecto, configure los tokens JWT emitidos para la prueba.
4. Haga doble clic en `GetLeadTestSuite` y pulse el botón verde de reproducción (**Play**).

---

## 5. Estado y evidencia de ejecución de la suite SoapUI (A4)

- **Ejecución automatizada CLI en A4:** La suite completa de SoapUI fue ejecutada exitosamente de forma automatizada mediante el ejecutor oficial `testrunner.sh` de **SoapUI 5.7.2 Open Source** contra la instancia viva temporal de CRM levantada en `http://127.0.0.1:8113/ws/leads` y respaldada por la base de datos PostgreSQL aislada `urleads_c13_test`.
- **Resultados de ejecución SoapUI:**
  - `TC01 - Valid GetLead Request`: VALID (SOAP Response, HTTP 200, XPath Match, Not Contains PII).
  - `TC02 - Schema Invalid XML Request`: VALID (SOAP Fault Client, HTTP 500, Contains Client).
  - `TC03 - Missing or Invalid Bearer Token`: VALID (HTTP 401).
  - `TC04 - Token Lacking Required Scope`: VALID (HTTP 403).
  - `TC05 - Indistinguishable Missing and Foreign Lead`: VALID (Ambos casos retornan SOAP Fault Client, HTTP 500, `LEAD_NOT_FOUND`).
  - `TC06 - XXE and External DTD Injection Rejection`: VALID (HTTP 400 rechazado, Not Contains `root:`).
  - **Resumen:** 6 casos de prueba ejecutados, 0 fallos; 18 aserciones válidas, 0 fallos.
- **Pruebas JUnit / Spring-WS complementarias:**
  - `com.urleads.soap.LeadSoapSecurityWebTest` (12 pruebas automatizadas).
  - `com.urleads.soap.LeadSoapEndpointWebTest` (9 pruebas automatizadas, incluyendo centinelas locales observables contra XXE y DTD externa).
  - `com.urleads.soap.SecureSaajSoapMessageFactoryTest` (3 pruebas unitarias de rechazo explícito de DOCTYPE).
