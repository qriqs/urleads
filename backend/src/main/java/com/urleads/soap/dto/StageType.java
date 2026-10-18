package com.urleads.soap.dto;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "StageType", namespace = "urn:urleads:crm:leads:v1")
@XmlEnum
public enum StageType {
    NUEVO,
    EN_SEGUIMIENTO,
    CERRADO;

    public String value() {
        return name();
    }

    public static StageType fromValue(String v) {
        return valueOf(v);
    }
}
