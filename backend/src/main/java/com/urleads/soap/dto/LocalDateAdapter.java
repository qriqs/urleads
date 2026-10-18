package com.urleads.soap.dto;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateAdapter extends XmlAdapter<String, LocalDate> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public LocalDate unmarshal(String v) {
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(v.trim(), FORMATTER);
    }

    @Override
    public String marshal(LocalDate v) {
        if (v == null) {
            return null;
        }
        return v.format(FORMATTER);
    }
}
