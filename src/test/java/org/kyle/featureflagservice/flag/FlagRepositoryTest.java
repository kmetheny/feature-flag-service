package org.kyle.featureflagservice.flag;

import org.hibernate.Hibernate;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

    private Flag persistFlag(String name, @Nullable String description) {
        Flag newFlag = new Flag();
        newFlag.setName(name);
        newFlag.setDescription(description);
        newFlag.setEnabled(true);
        newFlag.setEnvironments(Set.of("dev", "prod"));
        newFlag.setRolloutPercentage(50);

        em.persistAndFlush(newFlag);
        em.clear();

        return newFlag;
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
        flag = persistFlag("test-flag", "This is a test flag");
    }

    @Test
    void testFindAll() {
        Page<Flag> returnedFlags = repo.findAll(PageRequest.of(0, 1));

        assertThat(returnedFlags).hasSize(1);
        Flag returnedFlag = returnedFlags.getContent().getFirst();

        assertIsEqualToFlag(returnedFlag);
    }

    @Test
    void testFindAll_BatchFetchesEnvironments() {
        for (int i = 0; i < 3; i++) {
            persistFlag("test-flag-" + i, null);
        }

        List<Flag> flags = repo.findAll(PageRequest.of(0, 3, Sort.by("name"))).getContent();
        assertThat(flags).allSatisfy(f -> assertThat(Hibernate.isInitialized(f.getEnvironments())).isFalse());

        Hibernate.initialize(flags.getFirst().getEnvironments());

        assertThat(flags).allSatisfy(f -> assertThat(Hibernate.isInitialized(f.getEnvironments())).isTrue());
    }

    @Test
    void testFindAll_NullsSortedLast() {
        persistFlag("test-flag-1", "This is a test flag");
        persistFlag("test-flag-null-description", null);
        persistFlag("test-flag-2", "This is a test flag");

        Page<Flag> returnedFlagsAsc = repo.findAll(PageRequest.of(0, 100, Sort.Direction.ASC, "description"));
        Page<Flag> returnedFlagsDesc = repo.findAll(PageRequest.of(0, 100, Sort.Direction.DESC, "description"));

        assertThat(returnedFlagsAsc.getContent().getLast().getDescription()).isNull();
        assertThat(returnedFlagsDesc.getContent().getLast().getDescription()).isNull();
    }

    @Test
    void testFindById() {
        Long id = Objects.requireNonNull(flag.getId());

        Flag returnedFlag = repo.findById(id).orElseThrow();

        assertThat(Hibernate.isInitialized(returnedFlag.getEnvironments())).isTrue();
        assertIsEqualToFlag(returnedFlag);
    }

    @Test
    void testFindByName() {
        Flag returnedFlag = repo.findByName(flag.getName()).orElseThrow();

        assertThat(Hibernate.isInitialized(returnedFlag.getEnvironments())).isTrue();
        assertIsEqualToFlag(returnedFlag);
    }

    @Test
    void testSave_DuplicateName() {
        Flag duplicateNameFlag = new Flag();
        duplicateNameFlag.setName(flag.getName());
        repo.save(duplicateNameFlag);

        assertThatThrownBy(em::flush).isInstanceOf(ConstraintViolationException.class);
    }
}
