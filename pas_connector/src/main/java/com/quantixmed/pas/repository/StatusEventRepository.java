package com.quantixmed.pas.repository;

import com.quantixmed.pas.model.StatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusEventRepository extends JpaRepository<StatusEvent, Long> {
    List<StatusEvent> findTop50ByOrderByOccurredAtDesc();
    List<StatusEvent> findTop50BySerialNumberOrderByOccurredAtDesc(String serialNumber);
}
