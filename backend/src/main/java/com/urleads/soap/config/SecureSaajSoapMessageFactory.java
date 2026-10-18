package com.urleads.soap.config;

import org.springframework.ws.InvalidXmlException;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Fábrica de mensajes SAAJ con endurecimiento explícito del analizador XML (C13 / DSW-53).
 * Deshabilita explícitamente declaraciones DOCTYPE, entidades generales y de parámetro externas,
 * carga de DTD externa y acceso a esquemas/DTDs externos, protegiendo contra ataques XXE,
 * inclusión externa y denegación de servicio antes de la construcción del sobre SOAP.
 */
public class SecureSaajSoapMessageFactory extends SaajSoapMessageFactory {

    public static final String FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    public static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    public static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    public static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private final SAXParserFactory hardenedSaxParserFactory;

    public SecureSaajSoapMessageFactory() {
        super();
        setSoapVersion(SoapVersion.SOAP_11);
        afterPropertiesSet();
        this.hardenedSaxParserFactory = createHardenedSaxParserFactory();
    }

    private static SAXParserFactory createHardenedSaxParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);

        try {
            // 1. Deshabilitar DOCTYPE completamente (disallow-doctype-decl)
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new IllegalStateException("El parser XML no soporta disallow-doctype-decl", e);
        }

        try {
            // 2. Deshabilitar entidades generales y de parámetro externas
            factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
            factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new IllegalStateException("El parser XML no soporta la desactivación de entidades externas", e);
        }

        try {
            // 3. Activar procesamiento seguro estricto
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new IllegalStateException("El parser XML no soporta FEATURE_SECURE_PROCESSING", e);
        }

        return factory;
    }

    @Override
    public SaajSoapMessage createWebServiceMessage(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return super.createWebServiceMessage();
        }

        byte[] payloadBytes = toByteArray(inputStream);

        // Validación previa con el analizador endurecido para garantizar rechazo explícito de DOCTYPE / XXE
        validateWithHardenedParser(payloadBytes);

        return super.createWebServiceMessage(new ByteArrayInputStream(payloadBytes));
    }

    private void validateWithHardenedParser(byte[] payloadBytes) throws IOException {
        try {
            SAXParser saxParser = hardenedSaxParserFactory.newSAXParser();

            XMLReader xmlReader = saxParser.getXMLReader();
            try {
                xmlReader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            } catch (SAXNotRecognizedException | SAXNotSupportedException ignored) {
            }
            try {
                xmlReader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (SAXNotRecognizedException | SAXNotSupportedException ignored) {
            }

            // Deshabilitar resolución de entidades externas
            xmlReader.setEntityResolver((publicId, systemId) -> new InputSource(new ByteArrayInputStream(new byte[0])));

            xmlReader.parse(new InputSource(new ByteArrayInputStream(payloadBytes)));
        } catch (SAXException e) {
            // Falla cerrada inmediata: ante cualquier violación de seguridad DTD/DOCTYPE/entidad externa
            throw new InvalidXmlException("Carga XML rechazada: declaraciones DOCTYPE y entidades externas están deshabilitadas.", e);
        } catch (ParserConfigurationException e) {
            throw new InvalidXmlException("Error de configuración del parser seguro de XML", e);
        }
    }

    private static byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
