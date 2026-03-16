package com.pacs.connector.mqtt;

import io.moquette.broker.security.IAuthenticator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class MqttAuthenticator implements IAuthenticator {

    private final Map<String, String> validCredentials = new HashMap<>();

    public MqttAuthenticator(
            @Value("${mqtt.auth.edge.username:edge_user}") String edgeUsername,
            @Value("${mqtt.auth.edge.password:edge_password}") String edgePassword,
            @Value("${mqtt.auth.pacs.username:pacs_server}") String pacsUsername,
            @Value("${mqtt.auth.pacs.password:pacs_password}") String pacsPassword) {
        
        validCredentials.put(edgeUsername, edgePassword);
        validCredentials.put(pacsUsername, pacsPassword);
        log.info("MQTT Authenticator initialized with {} users", validCredentials.size());
    }

    @Override
    public boolean checkValid(String clientId, String username, byte[] password) {
        if (username == null || password == null) {
            log.warn("MQTT auth failed for client {}: missing credentials", clientId);
            return false;
        }

        String expectedPassword = validCredentials.get(username);
        if (expectedPassword == null) {
            log.warn("MQTT auth failed for client {}: unknown user '{}'", clientId, username);
            return false;
        }

        boolean valid = expectedPassword.equals(new String(password));
        if (valid) {
            log.info("MQTT auth success for client {} as user '{}'", clientId, username);
        } else {
            log.warn("MQTT auth failed for client {}: invalid password for user '{}'", clientId, username);
        }
        return valid;
    }
}
