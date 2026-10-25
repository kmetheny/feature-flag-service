package org.kyle.featureflagservice.flag;

import org.hibernate.Hibernate;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class FlagRepositoryTest {

    @Autowired
    private FlagRepository repo;

    @Autowired
    private TestEntityManager em;

    private Flag flag;

    private Long persist(Flag flagToPersist) {
        em.persistAndFlush(flagToPersist);
        em.clear();

        return Objects.requireNonNull(flagToPersist.getId());
    }

    private void assertIsEqualToFlag(Flag actualFlag) {
        assertThat(actualFlag.getId()).isEqualTo(flag.getId());
        assertThat(actualFlag.getName()).isEqualTo(flag.getName());
        assertThat(actualFlag.getDescription()).isEqualTo(flag.getDescription());
        assertThat(actualFlag.isEnabled()).isEqualTo(flag.isEnabled());
        assertThat(actualFlag.getEnvironments()).containsExactlyInAnyOrderElementsOf(flag.getEnvironments());
        assertThat(actualFlag.getRolloutPercentage()).isEqualTo(flag.getRolloutPercentage());
    }

    @BeforeEach
    void setUp() {
        flag = new Flag();
        flag.setName("test-flag");
        flag.setDescription("This is a test flag");
        flag.setEnabled(true);
        flag.setEnvironments(Set.of("dev", "prod"));
        flag.setRolloutPercentage(50);
    }

    @Test
    void testFindAll() {
        persist(flag);

        List<Flag> returnedFlags = repo.findAll();

        assertThat(returnedFlags).hasSize(1);
        Flag returnedFlag = returnedFlags.getFirst();

        assertThat(Hibernate.isInitialized(returnedFlag.getEnvironments())).isTrue();
        assertIsEqualToFlag(returnedFlag);
    }

    @Test
    void testFindById() {
        Long id = persist(flag);

        Flag returnedFlag = repo.findById(id).orElseThrow();

        assertThat(Hibernate.isInitialized(returnedFlag.getEnvironments())).isTrue();
        assertIsEqualToFlag(returnedFlag);
    }

    @Test
    void testFindByName() {
        persist(flag);

        Flag returnedFlag = repo.findByName(flag.getName()).orElseThrow();

        assertThat(Hibernate.isInitialized(returnedFlag.getEnvironments())).isTrue();
        assertIsEqualToFlag(returnedFlag);
    }

    @Test
    void testSave_DuplicateName() {
        persist(flag);
        Flag duplicateNameFlag = new Flag();
        duplicateNameFlag.setName(flag.getName());
        repo.save(duplicateNameFlag);

        assertThatThrownBy(em::flush).isInstanceOf(ConstraintViolationException.class);
    }
}
