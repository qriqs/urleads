package com.urleads.soap.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Filtro de seguridad a nivel HTTP que acota estrictamente el tamaño máximo de
 * las solicitudes XML hacia la interfaz SOAP (/ws/**) antes de que alcancen el
 * analizador XML de Spring Web Services, previniendo ataques de denegación de
 * servicio (XML Bomb / Billion Laughs).
 */
public class SoapSizeLimitingFilter extends OncePerRequestFilter {

    /**
     * Límite máximo de carga XML permitido: 16 KB (16,384 bytes).
     * Una solicitud GetLeadRequest legítima requiere menos de 1 KB.
     */
    public static final int MAX_REQUEST_SIZE_BYTES = 16 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/ws/") && !path.equals("/ws");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!HttpMethod.POST.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        long contentLength = -1;
        String contentLengthHeader = request.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            try {
                contentLength = Long.parseLong(contentLengthHeader.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (contentLength < 0) {
            contentLength = request.getContentLengthLong();
        }

        if (contentLength > MAX_REQUEST_SIZE_BYTES) {
            rejectPayloadTooLarge(response);
            return;
        }

        BoundedServletRequestWrapper requestWrapper;
        try {
            requestWrapper = new BoundedServletRequestWrapper(request, MAX_REQUEST_SIZE_BYTES);
        } catch (PayloadTooLargeException e) {
            rejectPayloadTooLarge(response);
            return;
        }

        if (containsDoctype(requestWrapper.getCachedBody())) {
            rejectDoctypeDisallowed(response);
            return;
        }

        filterChain.doFilter(requestWrapper, response);
    }

    private void rejectPayloadTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE); // HTTP 413
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Carga XML excede el limite maximo permitido de " + MAX_REQUEST_SIZE_BYTES + " bytes\"}");
    }

    private void rejectDoctypeDisallowed(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // HTTP 400
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Carga XML rechazada: declaraciones DOCTYPE estan deshabilitadas\"}");
    }

    private static boolean containsDoctype(byte[] bytes) {
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return content.toUpperCase().contains("<!DOCTYPE");
    }

    private static class PayloadTooLargeException extends IOException {
        PayloadTooLargeException(String message) {
            super(message);
        }
    }

    private static class BoundedServletRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        BoundedServletRequestWrapper(HttpServletRequest request, int maxSize) throws IOException {
            super(request);

            InputStream inputStream = request.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > maxSize) {
                    throw new PayloadTooLargeException("Payload size exceeds maximum allowed of " + maxSize + " bytes");
                }
                outputStream.write(buffer, 0, bytesRead);
            }

            this.cachedBody = outputStream.toByteArray();
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return byteArrayInputStream.read(b, off, len);
                }
            };
        }
    }
}
