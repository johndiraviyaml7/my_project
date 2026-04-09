package com.quantixmed.dicom.repository;

import com.quantixmed.dicom.model.Series;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeriesRepository extends JpaRepository<Series, UUID> {

    Optional<Series> findBySeriesInstanceUid(String uid);

    @Query("SELECT s FROM Series s WHERE s.study.id = :studyId ORDER BY s.seriesNumber ASC NULLS LAST")
    List<Series> findByStudyIdOrdered(@Param("studyId") UUID studyId);
}
