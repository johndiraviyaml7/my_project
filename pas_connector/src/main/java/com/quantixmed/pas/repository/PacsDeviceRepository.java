package com.quantixmed.pas.repository;

import com.quantixmed.pas.model.PacsDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PacsDeviceRepository extends JpaRepository<PacsDevice, UUID> {
    Optional<PacsDevice> findBySerialNumber(String serialNumber);
    List<PacsDevice> findAllByOrderByCreatedAtDesc();
}
