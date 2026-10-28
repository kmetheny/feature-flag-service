package org.kyle.featureflagservice.flag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    @Override
    @EntityGraph(attributePaths = "environments")
    Optional<Flag> findById(Long id);

    boolean existsByName(String name);

    @EntityGraph(attributePaths = "environments")
    Optional<Flag> findByName(String name);
}
