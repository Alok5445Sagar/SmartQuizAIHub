package com.smartquiz.repository;

import com.smartquiz.entity.TechnologyStack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TechnologyStackRepository extends JpaRepository<TechnologyStack, Long> {
    Optional<TechnologyStack> findByStackName(String stackName);
}
