package com.urleads.soap.exception;

import com.urleads.exception.LeadNotFoundException;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;

import javax.xml.namespace.QName;
import java.util.Properties;

/**
 * Mapeador de excepciones a fallos SOAP 1.1 formales.
 * Traduce LeadNotFoundException a un SOAP Fault Client con faultstring LEAD_NOT_FOUND,
 * garantizando indistinguibilidad estricta entre lead inexistente y ajeno para prevenir
 * ataques de enumeración o fuga de datos.
 */
public class LeadSoapFaultDefinitionExceptionResolver extends SoapFaultMappingExceptionResolver {

    public static final String LEAD_NOT_FOUND_FAULT = "LEAD_NOT_FOUND";
    private static final QName MESSAGE_QNAME = new QName("message");

    public LeadSoapFaultDefinitionExceptionResolver() {
        SoapFaultDefinition defaultFault = new SoapFaultDefinition();
        defaultFault.setFaultCode(SoapFaultDefinition.CLIENT);
        setDefaultFault(defaultFault);

        Properties mappings = new Properties();
        mappings.setProperty(LeadNotFoundException.class.getName(), SoapFaultDefinition.CLIENT + "," + LEAD_NOT_FOUND_FAULT);
        mappings.setProperty(SoapClientFaultException.class.getName(), SoapFaultDefinition.CLIENT.toString());
        setExceptionMappings(mappings);
        setOrder(1);
    }

    @Override
    protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
        if (ex instanceof LeadNotFoundException) {
            SoapFaultDetail detail = fault.addFaultDetail();
            detail.addFaultDetailElement(MESSAGE_QNAME).addText(
                    "El lead solicitado no existe o no pertenece al usuario autenticado."
            );
        }
    }
}
