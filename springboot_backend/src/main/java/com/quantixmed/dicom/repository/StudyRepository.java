package com.quantixmed.dicom.repository;

import com.quantixmed.dicom.model.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudyRepository extends JpaRepository<Study, UUID> {

    Optional<Study> findByStudyInstanceUid(String uid);

    @Query("SELECT s FROM Study s WHERE s.subject.id = :subjectId ORDER BY s.studyDate DESC NULLS LAST")
    List<Study> findBySubjectIdSorted(@Param("subjectId") UUID subjectId);
}
