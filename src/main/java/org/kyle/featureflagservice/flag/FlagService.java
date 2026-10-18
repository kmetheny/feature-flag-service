package org.kyle.featureflagservice.flag;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class FlagService {

    private static void applyRequest(Flag flag, FlagRequest request) {
        flag.setName(request.name());
        flag.setDescription(request.description());
        flag.setEnabled(request.enabled());
        flag.setEnvironments(new HashSet<>(request.environments()));
        flag.setRolloutPercentage(request.rolloutPercentage());
    }

    private final FlagRepository repo;

    public FlagService(FlagRepository repo) {
        this.repo = repo;
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
    public EvaluationResponse evaluate(String name, String userId, String env) {
        Flag flag = repo.findByName(name).orElseThrow(() -> new FlagNotFoundException(name));

        return new EvaluationResponse(name, userId, env, FlagEvaluator.isEnabledFor(flag, userId, env));
    }

    public FlagResponse create(FlagRequest request) {
        if (repo.existsByName(request.name())) {
            throw new DuplicateFlagException(request.name());
        }
        Flag flag = new Flag();
        applyRequest(flag, request);

        return FlagResponse.from(repo.save(flag));
    }

    public FlagResponse update(Long id, FlagRequest request) {
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
