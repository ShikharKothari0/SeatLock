import { lazy, Suspense } from 'react';
import { Routes, Route, Link } from 'react-router-dom';
import { useTheme } from './hooks/useTheme';
const HomePage = lazy(() => import('./pages/customer/HomePage').then(module => ({ default: module.HomePage })));
const EventPage = lazy(() => import('./pages/customer/EventPage').then(module => ({ default: module.EventPage })));
const CheckoutPage = lazy(() => import('./pages/customer/CheckoutPage').then(module => ({ default: module.CheckoutPage })));
const PaymentPage = lazy(() => import('./pages/customer/PaymentPage').then(module => ({ default: module.PaymentPage })));
const BookingConfirmedPage = lazy(() => import('./pages/customer/BookingConfirmedPage').then(module => ({ default: module.BookingConfirmedPage })));
const MyBookingsPage = lazy(() => import('./pages/customer/MyBookingsPage').then(module => ({ default: module.MyBookingsPage })));
const DashboardPage = lazy(() => import('./pages/admin/DashboardPage').then(module => ({ default: module.DashboardPage })));
const RedisPage = lazy(() => import('./pages/admin/RedisPage').then(module => ({ default: module.RedisPage })));
const CachePage = lazy(() => import('./pages/admin/CachePage').then(module => ({ default: module.CachePage })));
const CircuitBreakersPage = lazy(() => import('./pages/admin/CircuitBreakersPage').then(module => ({ default: module.CircuitBreakersPage })));
const KafkaPage = lazy(() => import('./pages/admin/KafkaPage').then(module => ({ default: module.KafkaPage })));
const SystemHealthPage = lazy(() => import('./pages/admin/SystemHealthPage').then(module => ({ default: module.SystemHealthPage })));
import { Navbar } from './components/common/Navbar';
import { AuthBoundary } from './components/common/AuthBoundary';
import { LoginPage } from './pages/customer/LoginPage';
import { EventEditor } from './pages/admin/EventEditor';
import { CatalogPage } from './pages/admin/CatalogPage';
import { DeveloperConsole } from './components/developer/DeveloperConsole';

function App() {
  useTheme();

  return (
    <div className="min-h-screen flex flex-col bg-white dark:bg-slate-950">
      <Navbar />
      <div className="flex-1">
        <AuthBoundary><Suspense fallback={<p className="p-8 text-slate-500" role="status">Loading page…</p>}><Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<HomePage />} />
          <Route path="/events/:eventId" element={<EventPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/booking-confirmed/:bookingId" element={<BookingConfirmedPage />} />
          <Route path="/my-bookings" element={<MyBookingsPage />} />

          {/* Admin Dashboard Routes */}
          <Route path="/admin" element={<DashboardPage />} />
          <Route path="/admin/catalog" element={<CatalogPage />} />
          <Route path="/admin/catalog/events/:eventId" element={<EventEditor />} />
          <Route path="/admin/redis" element={<RedisPage />} />
          <Route path="/admin/cache" element={<CachePage />} />
          <Route path="/admin/circuit-breakers" element={<CircuitBreakersPage />} />
          <Route path="/admin/kafka" element={<KafkaPage />} />
          <Route path="/admin/health" element={<SystemHealthPage />} />
        <Route path="*" element={<main className="p-8 text-slate-700 dark:text-slate-300"><h1>Page not found</h1><Link to="/">Browse events</Link></main>} />
        </Routes></Suspense></AuthBoundary>
      </div>
      <DeveloperConsole />
    </div>
  );
}

export default App;
