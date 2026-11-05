package org.kyle.featureflagservice.flag;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.Set;

@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
class FlagCacheConfig {

    @Bean
    CacheManager cacheManager(
            @Value("${flags.cache.maximum-size:1000}") long maximumSize,
            @Value("${flags.cache.time-to-live:30s}") Duration ttl
    ) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCacheNames(Set.of(FlagLookup.FLAGS_BY_ID_CACHE, FlagLookup.FLAGS_BY_NAME_CACHE));
        caffeineCacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(maximumSize)
                        .expireAfterWrite(ttl)
                        .recordStats());

        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }
}
