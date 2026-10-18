package com.urleads.soap.config;

import com.urleads.soap.exception.LeadSoapFaultDefinitionExceptionResolver;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.List;
import java.util.Properties;

/**
 * Configuración de Spring Web Services para el servicio SOAP 1.1 contract-first (C13).
 * Publica el servlet MessageDispatcherServlet sobre /ws/*, expone la definición
 * WSDL en /ws/leads.wsdl y configura interceptores de validación XSD y mapeo de fallos.
 */
@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    public static final String NAMESPACE_URI = "urn:urleads:crm:leads:v1";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "leads")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema leadsSchema) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("LeadsPort");
        wsdl11Definition.setLocationUri("/ws/leads");
        wsdl11Definition.setTargetNamespace(NAMESPACE_URI);
        wsdl11Definition.setSchema(leadsSchema);

        Properties soapActions = new Properties();
        soapActions.setProperty("GetLead", "urn:urleads:crm:leads:v1:GetLead");
        wsdl11Definition.setSoapActions(soapActions);

        return wsdl11Definition;
    }

    @Bean
    public XsdSchema leadsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/leads-v1.xsd"));
    }

    @Bean
    public SaajSoapMessageFactory messageFactory() {
        return new SecureSaajSoapMessageFactory();
    }

    @Bean
    public LeadSoapFaultDefinitionExceptionResolver leadSoapFaultDefinitionExceptionResolver() {
        return new LeadSoapFaultDefinitionExceptionResolver();
    }

    @Bean
    public PayloadValidatingInterceptor payloadValidatingInterceptor() {
        PayloadValidatingInterceptor interceptor = new PayloadValidatingInterceptor();
        interceptor.setValidateRequest(true);
        interceptor.setValidateResponse(false);
        interceptor.setXsdSchema(leadsSchema());
        return interceptor;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(payloadValidatingInterceptor());
    }
}
