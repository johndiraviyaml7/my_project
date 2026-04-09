package com.quantixmed.pas.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot's `server.ssl.*` settings apply to the primary connector.
 * We need a SECOND connector that speaks plain HTTP on 8444 for the
 * React dashboard's status/events polling, because the React app runs
 * in a browser and cannot present a client certificate.
 *
 * The primary 8443 connector still requires mTLS for /register and
 * /upload.  Both connectors see the same request dispatcher and thus
 * the same controllers — route gating is by port, not by URL.
 */
@Configuration
public class WebServerConfig {

    @Value("${pas.plain-http.port:8444}")
    private int plainPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpConnector() {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setScheme("http");
            connector.setSecure(false);
            connector.setPort(plainPort);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
