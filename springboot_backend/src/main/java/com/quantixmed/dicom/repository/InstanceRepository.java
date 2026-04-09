package com.quantixmed.dicom.repository;

import com.quantixmed.dicom.model.Instance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstanceRepository extends JpaRepository<Instance, UUID> {
    List<Instance> findBySeriesIdOrderByInstanceNumber(UUID seriesId);
    Optional<Instance> findBySopInstanceUid(String uid);
}
