package com.pacs.connector.repository;

import com.pacs.connector.model.PacsDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PacsDeviceRepository extends JpaRepository<PacsDevice, UUID> {
    Optional<PacsDevice> findByDeviceName(String deviceName);
    Optional<PacsDevice> findByMqttClientId(String mqttClientId);
}
