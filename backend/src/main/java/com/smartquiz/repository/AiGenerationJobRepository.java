package com.smartquiz.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.smartquiz.entity.AiGenerationJob;

@Repository
public interface AiGenerationJobRepository extends JpaRepository<AiGenerationJob, String> {

    Optional<AiGenerationJob> findByJobIdAndEnterpriseId(String jobId, String enterpriseId);

    boolean existsByEnterpriseIdAndStatusIn(String enterpriseId, Collection<AiGenerationJob.Status> statuses);

    /** Number of queued jobs submitted before the given time, i.e. how many wait ahead of a job. */
    long countByStatusAndCreatedAtBefore(AiGenerationJob.Status status, LocalDateTime createdAt);

    @Transactional
    @Modifying
    @Query("UPDATE AiGenerationJob j SET j.status = com.smartquiz.entity.AiGenerationJob.Status.FAILED, "
            + "j.errorMessage = :reason, j.finishedAt = :now WHERE j.status IN :statuses")
    int failAll(@Param("statuses") Collection<AiGenerationJob.Status> statuses,
                @Param("reason") String reason,
                @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("DELETE FROM AiGenerationJob j WHERE j.finishedAt IS NOT NULL AND j.finishedAt < :cutoff")
    int deleteFinishedBefore(@Param("cutoff") LocalDateTime cutoff);
}
