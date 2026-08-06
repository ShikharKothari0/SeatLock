import { Routes, Route } from 'react-router-dom';
import { useTheme } from './hooks/useTheme';
import { HomePage } from './pages/customer/HomePage';
import { EventPage } from './pages/customer/EventPage';
import { CheckoutPage } from './pages/customer/CheckoutPage';
import { PaymentPage } from './pages/customer/PaymentPage';
import { BookingConfirmedPage } from './pages/customer/BookingConfirmedPage';
import { MyBookingsPage } from './pages/customer/MyBookingsPage';
import { DashboardPage } from './pages/admin/DashboardPage';
import { RedisPage } from './pages/admin/RedisPage';
import { CachePage } from './pages/admin/CachePage';
import { CircuitBreakersPage } from './pages/admin/CircuitBreakersPage';
import { KafkaPage } from './pages/admin/KafkaPage';
import { SystemHealthPage } from './pages/admin/SystemHealthPage';
import { Navbar } from './components/common/Navbar';

function App() {
  useTheme();

  return (
    <div className="min-h-screen flex flex-col bg-slate-950">
      <Navbar />
      <main className="flex-1">
        <Routes>
          {/* Customer Portal Routes */}
          <Route path="/" element={<HomePage />} />
          <Route path="/events/:eventId" element={<EventPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/payment" element={<PaymentPage />} />
          <Route path="/booking-confirmed/:bookingId" element={<BookingConfirmedPage />} />
          <Route path="/my-bookings" element={<MyBookingsPage />} />

          {/* Admin Dashboard Routes */}
          <Route path="/admin" element={<DashboardPage />} />
          <Route path="/admin/redis" element={<RedisPage />} />
          <Route path="/admin/cache" element={<CachePage />} />
          <Route path="/admin/circuit-breakers" element={<CircuitBreakersPage />} />
          <Route path="/admin/kafka" element={<KafkaPage />} />
          <Route path="/admin/health" element={<SystemHealthPage />} />
        </Routes>
      </main>
    </div>
  );
}

export default App;