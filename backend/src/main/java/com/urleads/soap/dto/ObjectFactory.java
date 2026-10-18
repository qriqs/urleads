package com.urleads.soap.dto;

import jakarta.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory {

    public ObjectFactory() {
    }

    public GetLeadRequest createGetLeadRequest() {
        return new GetLeadRequest();
    }

    public GetLeadResponse createGetLeadResponse() {
        return new GetLeadResponse();
    }
}
