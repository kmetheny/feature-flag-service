package org.kyle.featureflagservice.flag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface FlagRepository extends ListCrudRepository<Flag, Long> {

    @Override
    @EntityGraph(attributePaths = "environments")
    List<Flag> findAll();

    @Override
    @EntityGraph(attributePaths = "environments")
    Optional<Flag> findById(Long id);

    boolean existsByName(String name);

    @EntityGraph(attributePaths = "environments")
    Optional<Flag> findByName(String name);
}
