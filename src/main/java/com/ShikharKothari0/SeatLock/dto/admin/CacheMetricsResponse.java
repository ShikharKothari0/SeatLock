package com.ShikharKothari0.SeatLock.dto.admin;

public record CacheMetricsResponse(
    long cacheHits,
    long cacheMisses,
    double hitRatio,
    long cacheInvalidations,
    long activeCacheKeys
) {}