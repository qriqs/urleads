package com.urleads.soap.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "leadId"
})
@XmlRootElement(name = "GetLeadRequest", namespace = "urn:urleads:crm:leads:v1")
public class GetLeadRequest {

    @XmlElement(namespace = "urn:urleads:crm:leads:v1", required = true)
    protected Long leadId;

    public GetLeadRequest() {
    }

    public GetLeadRequest(Long leadId) {
        this.leadId = leadId;
    }

    public Long getLeadId() {
        return leadId;
    }

    public void setLeadId(Long leadId) {
        this.leadId = leadId;
    }
}
