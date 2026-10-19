import { localDeadline } from './holdDeadline.ts';
// Axios API Client - Base configuration and error handling

import axios, { AxiosError } from 'axios';
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ApiError } from '../types/api';

const API_BASE_URL = '/api';

// Create axios instance
const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Fetch a fresh session CSRF token before each write; never retry a mutation automatically.
api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  if (!['get', 'head', 'options'].includes(config.method?.toLowerCase() ?? 'get')) {
    const { data } = await api.get<{ headerName: string; token: string }>('/auth/csrf');
    config.headers.set(data.headerName, data.token);
  }
  return config;
});
// Response interceptor - extract data and normalize errors
api.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error: AxiosError<ApiError>) => {
    if (!axios.isAxiosError(error)) return Promise.reject(error);
    if (error.response?.status === 401 && !error.config?.url?.startsWith('/auth/')) window.dispatchEvent(new Event('seatlock-auth-expired'));
    if (error.response?.status === 403 && !error.config?.url?.startsWith('/auth/')) {
      void api.get('/auth/me').catch((sessionError: { status?: number }) => {
        if (sessionError.status === 401) window.dispatchEvent(new Event('seatlock-auth-expired'));
      });
    }
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
  holdSeat: (seatId: string, _userId?: string) =>
    api.post<import('../types/api').HoldResponse>(`/seats/${seatId}/hold`, {}).then(response => response.data),

  quote: async (seatIds: string[]) => {
    const started = Date.now();
    const { data } = await api.post<import('../types/api').PriceQuote>('/bookings/quote', { seatIds });
    return { ...data, validUntil: data.serverTime ? localDeadline({ expiresAt: data.expiresAt, serverTime: data.serverTime }, started) : Date.parse(data.expiresAt) };
  },
  bookingPrice: (id: string) => api.get<import('../types/api').PriceQuote>(`/bookings/${id}/price`).then(r => r.data),
  // Booking
  getActiveHolds: (_userId?: string) => api.get<import('../types/api').ActiveHoldsResponse>('/seats/holds').then(response => response.data),
  releaseHold: (seatId: string, _userId?: string) => api.delete(`/seats/${seatId}/hold`),
  getBooking: (id: string, _userId?: string) => api.get<import('../types/api').BookingReceipt>(`/bookings/${id}`).then(response => response.data),
  getBookings: (_userId?: string, page = 0) => api.get<import('../types/api').BookingHistory>('/bookings', { params: { page, size: 10 } }).then(response => response.data),
  confirmBooking: (request: import('../types/api').BookingConfirmRequest) =>
    api.post<import('../types/api').BookingResponse>('/bookings/confirm', { seatIds: request.seatIds, idempotencyKey: request.idempotencyKey, quoteId: request.quoteId }).then(response => response.data),

  startPayment: (request: import('../types/api').BookingConfirmRequest) =>
    api.post<import('../types/api').PaymentAttempt>('/payments', { seatIds: request.seatIds, quoteId: request.quoteId,
      idempotencyKey: request.idempotencyKey, method: request.method ?? 'UPI', scenario: request.scenario ?? 'SUCCESS' }).then(r => r.data),
  processPayment: (id: string) => api.post<import('../types/api').PaymentAttempt>(`/payments/${id}/process`).then(r => r.data),
  // Admin Metrics
  getOverviewMetrics: () => api.get<import('../types/metrics').MetricsOverviewResponse>('/admin/metrics/overview').then(response => response.data),
  getCacheMetrics: () => api.get<import('../types/metrics').CacheMetricsResponse>('/admin/metrics/cache').then(response => response.data),
  getRedisMetrics: () => api.get<import('../types/metrics').RedisMetricsResponse>('/admin/metrics/redis').then(response => response.data),
  getCircuitBreakerMetrics: () => api.get<import('../types/metrics').CircuitBreakerMetricsResponse[]>('/admin/metrics/circuit-breakers').then(response => response.data),
  getSystemHealth: () => api.get<import('../types/metrics').SystemHealthResponse>('/admin/health').then(response => response.data),

  // SSE endpoint is handled via EventSource directly, not axios
};

export default api;
