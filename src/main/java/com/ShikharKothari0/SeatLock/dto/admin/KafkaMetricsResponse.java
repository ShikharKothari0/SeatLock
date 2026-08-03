package com.ShikharKothari0.SeatLock.dto.admin;

import java.util.Map;

public record KafkaMetricsResponse(
    long messagesPublished,
    long messagesConsumed,
    long dlqMessages,
    Map<String, Long> consumerLagByTopic
) {}