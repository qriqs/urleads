package com.urleads.soap.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.time.LocalDate;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "id",
    "stage",
    "nextFollowUp"
})
@XmlRootElement(name = "GetLeadResponse", namespace = "urn:urleads:crm:leads:v1")
public class GetLeadResponse {

    @XmlElement(namespace = "urn:urleads:crm:leads:v1", required = true)
    protected Long id;

    @XmlElement(namespace = "urn:urleads:crm:leads:v1", required = true)
    protected StageType stage;

    @XmlElement(namespace = "urn:urleads:crm:leads:v1", nillable = true)
    @XmlSchemaType(name = "date")
    @XmlJavaTypeAdapter(LocalDateAdapter.class)
    protected LocalDate nextFollowUp;

    public GetLeadResponse() {
    }

    public GetLeadResponse(Long id, StageType stage, LocalDate nextFollowUp) {
        this.id = id;
        this.stage = stage;
        this.nextFollowUp = nextFollowUp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StageType getStage() {
        return stage;
    }

    public void setStage(StageType stage) {
        this.stage = stage;
    }

    public LocalDate getNextFollowUp() {
        return nextFollowUp;
    }

    public void setNextFollowUp(LocalDate nextFollowUp) {
        this.nextFollowUp = nextFollowUp;
    }
}
