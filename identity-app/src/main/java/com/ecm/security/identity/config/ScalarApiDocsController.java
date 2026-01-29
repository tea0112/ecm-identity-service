package com.ecm.security.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller for Scalar API Documentation UI.
 * 
 * Scalar is a modern, beautiful API documentation interface that provides
 * a cleaner alternative to Swagger UI with features like:
 * - Modern, responsive design
 * - Better code samples
 * - Dark/light mode
 * - Request playground
 * 
 * @see <a href="https://scalar.com">Scalar Documentation</a>
 */
@Controller
public class ScalarApiDocsController {

  @Value("${server.port:8080}")
  private String serverPort;

  @Value("${springdoc.api-docs.path:/v3/api-docs}")
  private String openApiPath;

  /**
   * Serves the Scalar API documentation page.
   * Uses Scalar CDN for the latest UI version.
   */
  @GetMapping(value = "/scalar.html", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String scalarDocs() {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>ECM Identity Service - API Documentation</title>
            <meta name="description" content="ECM Identity Service API Documentation - Modern, interactive API explorer">
            <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🔐</text></svg>">
            <style>
                body {
                    margin: 0;
                    padding: 0;
                }
                /* Custom theme overrides */
                .scalar-app {
                    --scalar-color-1: #1a1a2e;
                    --scalar-color-2: #16213e;
                    --scalar-color-3: #0f3460;
                    --scalar-color-accent: #e94560;
                }
            </style>
        </head>
        <body>
            <script
                id="api-reference"
                data-url="%s"
                data-configuration='{
                    "theme": "kepler",
                    "layout": "modern",
                    "showSidebar": true,
                    "hideDarkModeToggle": false,
                    "searchHotKey": "k",
                    "metaData": {
                        "title": "ECM Identity Service API",
                        "description": "Enterprise Content Management - Identity & Access Management"
                    },
                    "authentication": {
                        "preferredSecurityScheme": "bearer-jwt"
                    }
                }'
            ></script>
            <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
        </body>
        </html>
        """
        .formatted(openApiPath);
  }

  /**
   * Redirect /docs to /scalar.html for convenience.
   */
  @GetMapping("/docs")
  public String redirectDocs() {
    return "redirect:/scalar.html";
  }

  /**
   * Redirect /api-docs to /swagger-ui.html for classic Swagger UI.
   */
  @GetMapping("/api-docs")
  public String redirectApiDocs() {
    return "redirect:/swagger-ui.html";
  }
}
