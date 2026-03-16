package com.pacs.connector.repository;

import com.pacs.connector.model.PacsConnectivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PacsConnectivityStatusRepository extends JpaRepository<PacsConnectivityStatus, UUID> {

    List<PacsConnectivityStatus> findByPacsDeviceIdOrderByReceivedAtDesc(UUID deviceId);

    @Query("SELECT p FROM PacsConnectivityStatus p WHERE p.pacsDevice.id = :deviceId ORDER BY p.receivedAt DESC LIMIT 1")
    Optional<PacsConnectivityStatus> findLatestByDeviceId(UUID deviceId);

    List<PacsConnectivityStatus> findByMessageSourceOrderByReceivedAtDesc(String messageSource);
}
