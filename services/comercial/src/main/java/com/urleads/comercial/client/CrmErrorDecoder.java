package com.urleads.comercial.client;

import com.urleads.comercial.client.exception.CrmAuthenticationException;
import com.urleads.comercial.client.exception.CrmForbiddenException;
import com.urleads.comercial.client.exception.CrmUnavailableException;
import com.urleads.comercial.exception.ResourceNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

@Component
public class CrmErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        return switch (status) {
            case 401 -> new CrmAuthenticationException("Token de acceso no autorizado o rechazado por CRM (401)");
            case 403 -> new CrmForbiddenException("Permisos insuficientes en CRM para consultar el lead (403)");
            case 404 -> new ResourceNotFoundException("Lead no encontrado o no pertenece al usuario autenticado (404)");
            case 503 -> new CrmUnavailableException("El servicio CRM no se encuentra disponible temporalmente.");
            default -> {
                if (status >= 500) {
                    yield new CrmUnavailableException("El servicio CRM no se encuentra disponible temporalmente.");
                }
                yield defaultDecoder.decode(methodKey, response);
            }
        };
    }
}
