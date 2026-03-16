package com.pacs.connector.repository;

import com.pacs.connector.model.PacsDicomImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PacsDicomImageRepository extends JpaRepository<PacsDicomImage, UUID> {
    List<PacsDicomImage> findByPacsDeviceIdOrderByReceivedAtDesc(UUID deviceId);
    List<PacsDicomImage> findByPatientId(String patientId);
    List<PacsDicomImage> findByStatus(String status);
}
