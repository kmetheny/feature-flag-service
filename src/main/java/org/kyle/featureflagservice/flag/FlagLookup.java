package org.kyle.featureflagservice.flag;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlagLookup {
    private final FlagRepository repo;

    public static final String FLAGS_BY_ID_CACHE = "flagsById";
    public static final String FLAGS_BY_NAME_CACHE = "flagsByName";

    public FlagLookup(FlagRepository repo) {
        this.repo = repo;
    }

    @Cacheable(FLAGS_BY_ID_CACHE)
    @Transactional(readOnly = true)
    public FlagResponse getById(Long id) {
        return repo.findById(id).map(FlagResponse::from).orElseThrow(() -> new FlagNotFoundException(id));
    }

    @Cacheable(FLAGS_BY_NAME_CACHE)
    @Transactional(readOnly = true)
    public FlagResponse getByName(String name) {
        return repo.findByName(name).map(FlagResponse::from).orElseThrow(() -> new FlagNotFoundException(name));
    }

    @Caching(evict = {
            @CacheEvict(value = FLAGS_BY_ID_CACHE, key = "#id"),
            @CacheEvict(value = FLAGS_BY_NAME_CACHE, key = "#name")
    })
    @SuppressWarnings("unused")
    public void evict(Long id, String name) {
    }
}
