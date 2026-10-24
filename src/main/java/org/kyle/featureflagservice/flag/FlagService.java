package org.kyle.featureflagservice.flag;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
@Transactional
public class FlagService {

    private final FlagRepository repo;
    private final FlagProperties properties;

    private static void applyRequest(Flag flag, FlagRequest request) {
        flag.setName(request.name());
        flag.setDescription(request.description());
        flag.setEnabled(request.enabled());
        flag.setEnvironments(new HashSet<>(request.environments()));
        flag.setRolloutPercentage(request.rolloutPercentage());
    }

    private void requireKnownEnvironments(Collection<String> environments) {
        List<String> unknownEnvironments = environments.stream()
                .filter(environment -> !properties.environments().contains(environment))
                .toList();

        if (!unknownEnvironments.isEmpty()) {
            throw new UnknownEnvironmentException(unknownEnvironments);
        }
    }

    public FlagService(FlagRepository repo, FlagProperties properties) {
        this.repo = repo;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<FlagResponse> findAll() {
        return repo.findAll().stream().map(FlagResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FlagResponse get(Long id) {
        return FlagResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public EvaluationResponse evaluate(String name, String userId, String environment) {
        requireKnownEnvironments(Set.of(environment));

        Flag flag = repo.findByName(name).orElseThrow(() -> new FlagNotFoundException(name));

        return new EvaluationResponse(name, userId, environment, FlagEvaluator.isEnabledFor(flag, userId, environment));
    }

    public FlagResponse create(FlagRequest request) {
        requireKnownEnvironments(request.environments());

        if (repo.existsByName(request.name())) {
            throw new DuplicateFlagException(request.name());
        }

        Flag flag = new Flag();
        applyRequest(flag, request);

        return FlagResponse.from(repo.save(flag));
    }

    public FlagResponse update(Long id, FlagRequest request) {
        requireKnownEnvironments(request.environments());

        Flag flag = find(id);

        if (!Objects.equals(flag.getName(), request.name()) && repo.existsByName(request.name())) {
            throw new DuplicateFlagException(request.name());
        }

        applyRequest(flag, request);

        return FlagResponse.from(flag);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    private Flag find(Long id) {
        return repo.findById(id).orElseThrow(() -> new FlagNotFoundException(id));
    }
}
