package com.quantixmed.pas.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Rejects mTLS-required endpoints (/register, /upload) when the request
 * arrives on the plain-HTTP connector (port 8444) — only the mTLS 8443
 * connector may process them.  This compensates for the fact that the
 * second Tomcat connector is deliberately non-secure for the dashboard.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PortGatingFilter implements Filter {

    @Value("${pas.plain-http.port:8444}")
    private int plainPort;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest r = (HttpServletRequest) req;
        HttpServletResponse s = (HttpServletResponse) resp;

        int port = r.getLocalPort();
        String path = r.getRequestURI();

        boolean secureOnlyPath = path.startsWith("/api/pas/register")
                || path.startsWith("/api/pas/upload");
        boolean plainOnlyPath  = path.startsWith("/api/pas/devices/status")
                || path.startsWith("/api/pas/events")
                || path.startsWith("/api/pas/health")
                || path.startsWith("/api/pas/devices/") && path.endsWith("/events");

        if (secureOnlyPath && port == plainPort) {
            s.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "This endpoint requires mutual TLS (use port 8443)");
            return;
        }
        // plainOnlyPath on the mTLS port is allowed — not worth blocking.
        chain.doFilter(req, resp);
    }
}
