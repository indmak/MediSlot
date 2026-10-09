package com.medislot.repository;

import com.medislot.entity.KnowledgeSource;
import com.medislot.entity.KnowledgeSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {

    Optional<KnowledgeSource> findFirstByTypeOrderByIdAsc(KnowledgeSourceType type);

    List<KnowledgeSource> findAllByOrderByIdAsc();

    boolean existsByName(String name);
}
