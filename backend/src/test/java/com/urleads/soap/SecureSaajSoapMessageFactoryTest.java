package com.urleads.soap;

import com.urleads.soap.config.SecureSaajSoapMessageFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ws.InvalidXmlException;
import org.springframework.ws.soap.saaj.SaajSoapMessage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureSaajSoapMessageFactoryTest {

    private final SecureSaajSoapMessageFactory messageFactory = new SecureSaajSoapMessageFactory();

    @Test
    @DisplayName("C1: Solicitud SOAP legítima sin DOCTYPE es procesada correctamente por el parser seguro")
    void createWebServiceMessage_ValidPayload_Success() throws Exception {
        String cleanSoap = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <v1:GetLeadRequest><v1:leadId>105</v1:leadId></v1:GetLeadRequest>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        SaajSoapMessage message = messageFactory.createWebServiceMessage(
                new ByteArrayInputStream(cleanSoap.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(message).isNotNull();
        assertThat(message.getSaajMessage()).isNotNull();
        assertThat(message.getSaajMessage().getSOAPBody()).isNotNull();
    }

    @Test
    @DisplayName("C1: Declaración DOCTYPE es rechazada explícitamente lanzando InvalidXmlException")
    void createWebServiceMessage_WithDoctype_ThrowsInvalidXmlException() {
        String doctypePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE test [\n" +
                "  <!ELEMENT test ANY >\n" +
                "]>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <v1:GetLeadRequest><v1:leadId>105</v1:leadId></v1:GetLeadRequest>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        assertThatThrownBy(() -> messageFactory.createWebServiceMessage(
                new ByteArrayInputStream(doctypePayload.getBytes(StandardCharsets.UTF_8))
        ))
                .isInstanceOf(InvalidXmlException.class)
                .hasMessageContaining("declaraciones DOCTYPE y entidades externas están deshabilitadas");
    }

    @Test
    @DisplayName("C1: Entidad externa SYSTEM es rechazada explícitamente por el parser seguro")
    void createWebServiceMessage_WithExternalEntity_ThrowsInvalidXmlException() {
        String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE test [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///dummy-sentinel.txt\" >\n" +
                "]>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <v1:GetLeadRequest><v1:leadId>&xxe;</v1:leadId></v1:GetLeadRequest>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        assertThatThrownBy(() -> messageFactory.createWebServiceMessage(
                new ByteArrayInputStream(xxePayload.getBytes(StandardCharsets.UTF_8))
        ))
                .isInstanceOf(InvalidXmlException.class)
                .hasMessageContaining("declaraciones DOCTYPE y entidades externas están deshabilitadas");
    }
}
