package com.urleads.controller;

import org.springframework.http.HttpStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URLConnection;

@Controller
@RequestMapping(path = "/")
public class SpaController {

    @GetMapping(value = {"", "/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        return resource("index.html", MediaType.TEXT_HTML);
    }

    @GetMapping(value = "/{*path}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> forwardClientRoutes(@PathVariable String path) {
        if (path.equals("/api") || path.startsWith("/api/") || path.equals("/ws") || path.startsWith("/ws/") || path.contains(".")) {
            return ResponseEntity.notFound().build();
        }
        return resource("index.html", MediaType.TEXT_HTML);
    }

    @GetMapping("/assets/{*asset}")
    public ResponseEntity<Resource> staticAsset(@PathVariable String asset) {
        String relativeAsset = asset.startsWith("/") ? asset.substring(1) : asset;
        if (relativeAsset.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        String location = "assets/" + relativeAsset;
        MediaType mediaType = assetMediaType(location);
        return resource(location, mediaType);
    }

    private MediaType assetMediaType(String location) {
        if (location.endsWith(".js")) return MediaType.parseMediaType("application/javascript");
        if (location.endsWith(".css")) return MediaType.parseMediaType("text/css");
        String contentType = URLConnection.guessContentTypeFromName(location);
        return contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
    }

    private ResponseEntity<Resource> resource(String location, MediaType mediaType) {
        Resource resource = new ClassPathResource("static/" + location);
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
