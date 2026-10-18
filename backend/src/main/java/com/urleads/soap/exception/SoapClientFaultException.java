package com.urleads.soap.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

@SoapFault(faultCode = FaultCode.CLIENT)
public class SoapClientFaultException extends RuntimeException {

    public SoapClientFaultException(String message) {
        super(message);
    }
}
