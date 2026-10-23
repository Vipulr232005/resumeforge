package com.resumeforge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ResumeEntityRepository extends JpaRepository<ResumeEntity, Long> {
    List<ResumeEntity> findByUserId(Long userId);
    List<ResumeEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
