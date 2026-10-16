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
  (response: AxiosResponse) => response,
  (error: AxiosError<ApiError>) => {
    // Normalize error shape for consistent handling
    const normalizedError: ApiError = {
      message: !error.response ? 'Unable to reach SeatLock. Check your connection and try again.' : error.response.status >= 500 ? 'Something went wrong. Please try again.' : error.response.data?.message || 'The request could not be completed.',
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
  getEvents: () => api.get<import('../types/api').EventResponse[]>('/events').then(response => response.data),
  getEvent: (id: string) => api.get<import('../types/api').EventResponse>(`/events/${id}`).then(response => response.data),

  // Seats
  getSeats: (eventId: string, status?: import('../types/api').SeatStatus) =>
    api.get<import('../types/api').SeatResponse[]>(`/events/${eventId}/seats`, {
      params: status ? { status } : undefined,
    }).then(response => response.data),

  // Seat Hold
  holdSeat: (seatId: string, userId: string) =>
    api.post<import('../types/api').HoldResponse>(`/seats/${seatId}/hold`, { userId }).then(response => response.data),

  // Booking
  getActiveHolds: (userId: string) => api.get<import('../types/api').ActiveHoldsResponse>('/seats/holds', { params: { userId } }).then(response => response.data),
  releaseHold: (seatId: string, userId: string) => api.delete(`/seats/${seatId}/hold`, { params: { userId } }),
  getBooking: (id: string, userId: string) => api.get<import('../types/api').BookingReceipt>(`/bookings/${id}`, { params: { userId } }).then(response => response.data),
  getBookings: (userId: string, page = 0) => api.get<import('../types/api').BookingHistory>('/bookings', { params: { userId, page, size: 10 } }).then(response => response.data),
  confirmBooking: (request: import('../types/api').BookingConfirmRequest) =>
    api.post<import('../types/api').BookingResponse>('/bookings/confirm', request).then(response => response.data),

  // Admin Metrics
  getOverviewMetrics: () => api.get<import('../types/metrics').MetricsOverviewResponse>('/admin/metrics/overview').then(response => response.data),
  getCacheMetrics: () => api.get<import('../types/metrics').CacheMetricsResponse>('/admin/metrics/cache').then(response => response.data),
  getRedisMetrics: () => api.get<import('../types/metrics').RedisMetricsResponse>('/admin/metrics/redis').then(response => response.data),
  getCircuitBreakerMetrics: () => api.get<import('../types/metrics').CircuitBreakerMetricsResponse[]>('/admin/metrics/circuit-breakers').then(response => response.data),
  getSystemHealth: () => api.get<import('../types/metrics').SystemHealthResponse>('/admin/health').then(response => response.data),

  // SSE endpoint is handled via EventSource directly, not axios
};

export default api;
