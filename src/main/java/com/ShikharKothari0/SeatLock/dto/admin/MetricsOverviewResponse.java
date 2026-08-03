package com.ShikharKothari0.SeatLock.dto.admin;

public record MetricsOverviewResponse(
    long totalBookings,
    double bookingsPerMinute,
    long totalHolds,
    long holdsExpired,
    long holdsRejected,
    double holdSuccessRate,
    double avgHoldLatencyMs,
    double p99HoldLatencyMs
) {}