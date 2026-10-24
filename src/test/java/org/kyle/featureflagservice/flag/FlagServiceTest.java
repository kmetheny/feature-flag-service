package org.kyle.featureflagservice.flag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock
    private FlagRepository repo;

    @Captor
    private ArgumentCaptor<Flag> flagCaptor;

    private FlagService service;
    private Flag flag;
    private FlagRequest request;
    private FlagRequest updateRequest;
    private static final Long ID = 1L;

    private void assertFlagEqualsResult(FlagResponse response) {
        assertThat(response.id()).isEqualTo(flag.getId());
        assertThat(response.name()).isEqualTo(flag.getName());
        assertThat(response.description()).isEqualTo(flag.getDescription());
        assertThat(response.environments()).containsExactlyInAnyOrderElementsOf(flag.getEnvironments());
        assertThat(response.enabled()).isEqualTo(flag.isEnabled());
        assertThat(response.rolloutPercentage()).isEqualTo(flag.getRolloutPercentage());
    }

    @BeforeEach
    void setUp() {
        service = new FlagService(repo, new FlagProperties(Set.of("dev", "prod")));

        flag = new Flag();
        ReflectionTestUtils.setField(flag, "id", ID);
        flag.setName("test-flag");
        flag.setDescription("Test flag");
        flag.setEnabled(true);
        flag.setEnvironments(Set.of("dev"));
        flag.setRolloutPercentage(100);

        request = new FlagRequest(
                flag.getName(),
                flag.getDescription(),
                flag.isEnabled(),
                flag.getEnvironments(),
                flag.getRolloutPercentage());

        updateRequest = new FlagRequest(
                flag.getName() + "-updated",
                flag.getDescription() + " - Updated",
                !flag.isEnabled(),
                Set.of("prod"),
                50
        );
    }

    @Test
    void testFindAll() {
        given(repo.findAll()).willReturn(List.of(flag));

        List<FlagResponse> result = service.findAll();

        assertThat(result).hasSize(1);
        FlagResponse flagResponse = result.getFirst();

        assertFlagEqualsResult(flagResponse);
        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testGet() {
        given(repo.findById(ID)).willReturn(Optional.of(flag));

        FlagResponse result = service.get(ID);

        assertFlagEqualsResult(result);
        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testEvaluate() {
        given(repo.findByName(flag.getName())).willReturn(Optional.of(flag));

        String userId = "123456";
        String env = "dev";
        EvaluationResponse result = service.evaluate(flag.getName(), userId, env);

        assertThat(result.enabled()).isTrue();
        assertThat(result.flag()).isEqualTo(flag.getName());
        assertThat(result.environment()).isEqualTo(env);
        assertThat(result.userId()).isEqualTo(userId);
        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testEvaluate_NotFound() {
        given(repo.findByName(flag.getName())).willReturn(Optional.empty());

        assertThrows(FlagNotFoundException.class, () -> service.evaluate(flag.getName(), "123456", "dev"));

        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testEvaluate_UnknownEnvironment() {
        assertThrows(UnknownEnvironmentException.class, () -> service.evaluate(flag.getName(), "123456", "pord"));

        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testCreate() {
        given(repo.existsByName(flag.getName())).willReturn(false);
        given(repo.save(any(Flag.class))).willAnswer(inv -> {
            Flag f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", ID);
            return f;
        });

        service.create(request);

        then(repo).should().save(flagCaptor.capture());
        Flag saved = flagCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(ID);
        assertThat(saved.getName()).isEqualTo(flag.getName());
        assertThat(saved.getDescription()).isEqualTo(flag.getDescription());
        assertThat(saved.getEnvironments()).containsExactlyInAnyOrderElementsOf(flag.getEnvironments());
        assertThat(saved.isEnabled()).isEqualTo(flag.isEnabled());
        assertThat(saved.getRolloutPercentage()).isEqualTo(flag.getRolloutPercentage());
        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testCreate_UnknownEnvironment() {
        assertThrows(
                UnknownEnvironmentException.class,
                () -> service.create(new FlagRequest("test-flag", null, false, Set.of("pord"), 0)));

        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testCreate_NameAlreadyExists() {
        given(repo.existsByName(flag.getName())).willReturn(true);

        assertThrows(DuplicateFlagException.class, () -> service.create(request));

        then(repo).shouldHaveNoMoreInteractions();
    }


    @Test
    void testUpdate() {
        given(repo.findById(ID)).willReturn(Optional.of(flag));
        given(repo.existsByName(updateRequest.name())).willReturn(false);

        FlagResponse result = service.update(ID, updateRequest);

        assertThat(result.id()).isEqualTo(flag.getId());
        assertThat(result.name()).isEqualTo(updateRequest.name());
        assertThat(result.description()).isEqualTo(updateRequest.description());
        assertThat(result.environments()).containsExactlyInAnyOrderElementsOf(updateRequest.environments());
        assertThat(result.enabled()).isEqualTo(updateRequest.enabled());
        assertThat(result.rolloutPercentage()).isEqualTo(updateRequest.rolloutPercentage());

        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testUpdate_NoChanges() {
        given(repo.findById(ID)).willReturn(Optional.of(flag));

        FlagResponse result = service.update(ID, request);

        assertFlagEqualsResult(result);
        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testUpdate_UnknownEnvironment() {
        assertThrows(
                UnknownEnvironmentException.class,
                () -> service.update(1L, new FlagRequest("test-flag", null, false, Set.of("pord"), 0)));

        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testUpdate_NotFound() {
        given(repo.findById(ID)).willReturn(Optional.empty());

        assertThrows(FlagNotFoundException.class, () -> service.update(ID, request));

        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testUpdate_NameAlreadyExists() {
        String name = "updated-name";
        given(repo.findById(ID)).willReturn(Optional.of(flag));
        given(repo.existsByName(name)).willReturn(true);

        assertThrows(
                DuplicateFlagException.class,
                () -> service.update(
                        ID,
                        new FlagRequest(
                                name,
                                "Updated description",
                                !flag.isEnabled(),
                                Set.of("prod"),
                                100
                        )
                )
        );

        then(repo).shouldHaveNoMoreInteractions();
    }

    @Test
    void testDelete() {
        service.delete(ID);

        then(repo).should().deleteById(ID);
        then(repo).shouldHaveNoMoreInteractions();
    }
}
