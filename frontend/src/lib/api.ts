// Axios API Client - Base configuration and error handling

import axios, { AxiosError } from 'axios';
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ApiError } from '../types/api';

const API_BASE_URL = '/api';

// Create axios instance
const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - can be used for auth tokens later
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Future: Add auth token if needed
    // const token = getAuthToken();
    // if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

// Response interceptor - extract data and normalize errors
api.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  (error: AxiosError<ApiError>) => {
    // Normalize error shape for consistent handling
    const normalizedError: ApiError = {
      message: error.response?.data?.message ?? error.message ?? 'Network error',
      timestamp: error.response?.data?.timestamp ?? new Date().toISOString(),
    };

    // Attach status code for UI to handle 4xx vs 5xx differently
    const status = error.response?.status ?? 0;

    // Create enriched error with status
    const enrichedError = new Error(normalizedError.message) as Error & {
      status: number;
      apiError: ApiError;
      originalError: AxiosError;
    };
    enrichedError.status = status;
    enrichedError.apiError = normalizedError;
    enrichedError.originalError = error;

    return Promise.reject(enrichedError);
  }
);

// Type-safe API methods
export const apiClient = {
  // Events
  getEvents: () => api.get<import('../types/api').EventResponse[]>('/events'),
  getEvent: (id: string) => api.get<import('../types/api').EventResponse>(`/events/${id}`),

  // Seats
  getSeats: (eventId: string, status?: import('../types/api').SeatStatus) =>
    api.get<import('../types/api').SeatResponse[]>(`/events/${eventId}/seats`, {
      params: status ? { status } : undefined,
    }),

  // Seat Hold
  holdSeat: (seatId: string, userId: string) =>
    api.post<import('../types/api').HoldResponse>(`/seats/${seatId}/hold`, { userId }),

  // Booking
  confirmBooking: (request: import('../types/api').BookingConfirmRequest) =>
    api.post<import('../types/api').BookingResponse>('/bookings/confirm', request),

  // Admin Metrics
  getOverviewMetrics: () => api.get<import('../types/metrics').MetricsOverviewResponse>('/admin/metrics/overview'),
  getCacheMetrics: () => api.get<import('../types/metrics').CacheMetricsResponse>('/admin/metrics/cache'),
  getRedisMetrics: () => api.get<import('../types/metrics').RedisMetricsResponse>('/admin/metrics/redis'),
  getCircuitBreakerMetrics: () => api.get<import('../types/metrics').CircuitBreakerMetricsResponse[]>('/admin/metrics/circuit-breakers'),
  getSystemHealth: () => api.get<import('../types/metrics').SystemHealthResponse>('/admin/health'),

  // SSE endpoint is handled via EventSource directly, not axios
};

export default api;