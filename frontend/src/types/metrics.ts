// Metrics Types - Mirror admin DTO records from FRONTEND-INTEGRATION-GUIDE.md Part 4.2

export interface MetricsOverviewResponse {
  totalBookings: number;
  bookingsPerMinute: number;
  totalHolds: number;
  holdsExpired: number;
  holdsRejected: number;
  holdSuccessRate: number;
  avgHoldLatencyMs: number;
  p99HoldLatencyMs: number;
}

export interface CacheMetricsResponse {
  cacheHits: number;
  cacheMisses: number;
  hitRatio: number;
  cacheInvalidations: number;
  activeCacheKeys: number;
}

export interface RedisMetricsResponse {
  connected: boolean;
  activeLocks: number;
  memoryUsedBytes: number;
  totalKeys: number;
  hitRatio: number;
  connectedClients: number;
}

export interface KafkaMetricsResponse {
  messagesPublished: number;
  messagesConsumed: number;
  dlqMessages: number;
  consumerLagByTopic: Record<string, number>;
}

export interface CircuitBreakerMetricsResponse {
  name: string;
  state: string;
  failureRate: number;
  failedCalls: number;
  successfulCalls: number;
  bufferedCalls: number;
}

export interface SystemHealthResponse {
  status: string;
  cpuUsage: number;
  heapUsedBytes: number;
  heapMaxBytes: number;
  liveThreads: number;
  hikariActiveConnections: number;
  hikariPendingConnections: number;
  circuitBreakers: CircuitBreakerMetricsResponse[];
}

export interface MetricStreamSnapshot {
  overview: MetricsOverviewResponse;
  cache: CacheMetricsResponse;
  redis: RedisMetricsResponse;
  circuitBreakers: CircuitBreakerMetricsResponse[];
  timestamp: string; // ISO 8601 Instant
}