package org.kyle.featureflagservice.flag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
public class FlagService {

    private final FlagRepository repo;
    private final FlagLookup flagLookup;
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

    public FlagService(FlagRepository repo, FlagLookup flagLookup, FlagProperties properties) {
        this.repo = repo;
        this.flagLookup = flagLookup;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Page<FlagResponse> findAll(Pageable pageRequest) {
        Pageable stable = PageRequest.of(
                pageRequest.getPageNumber(),
                pageRequest.getPageSize(),
                pageRequest.getSort().and(Sort.by("id")));

        return repo.findAll(stable).map(FlagResponse::from);
    }

    public FlagResponse get(Long id) {
        return flagLookup.getById(id);
    }

    public EvaluationResponse evaluate(String name, String userId, String environment) {
        requireKnownEnvironments(Set.of(environment));

        FlagResponse flagResponse = flagLookup.getByName(name);

        return new EvaluationResponse(name, userId, environment, FlagEvaluator.isEnabledFor(flagResponse, userId, environment));
    }

    @Transactional
    public FlagResponse create(FlagRequest request) {
        requireKnownEnvironments(request.environments());

        if (repo.existsByName(request.name())) {
            throw new DuplicateFlagException(request.name());
        }

        Flag flag = new Flag();
        applyRequest(flag, request);

        return FlagResponse.from(repo.save(flag));
    }

    @Transactional
    public FlagResponse update(Long id, FlagRequest request) {
        requireKnownEnvironments(request.environments());

        Flag flag = repo.findById(id).orElseThrow(() -> new FlagNotFoundException(id));

        String oldName = flag.getName();

        if (!Objects.equals(flag.getName(), request.name()) && repo.existsByName(request.name())) {
            throw new DuplicateFlagException(request.name());
        }

        applyRequest(flag, request);

        flagLookup.evict(id, oldName);

        return FlagResponse.from(flag);
    }

    @Transactional
    public void delete(Long id) {
        repo.findById(id).ifPresent(flag -> {
            repo.delete(flag);
            flagLookup.evict(id, flag.getName());
        });
    }
}
