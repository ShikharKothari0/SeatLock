// AdminLayout - Layout wrapper for admin pages with sidebar

import { AdminSidebar } from './AdminSidebar';

interface AdminLayoutProps {
  children: React.ReactNode;
}

export function AdminLayout({ children }: AdminLayoutProps) {
  return (
    <div className="flex min-h-screen bg-slate-950">
      <AdminSidebar />
      <div className="flex-1 ml-64 min-w-0">
        <header className="sticky top-0 z-[199] bg-slate-950/80 backdrop-blur-sm border-b border-slate-800">
          <div className="max-w-full mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex items-center justify-between h-16">
              <h1 className="text-xl font-bold text-white">Admin Dashboard</h1>
              <div className="text-xs text-slate-500 font-mono">
                SeatLock Admin
              </div>
            </div>
          </div>
        </header>
        <main className="flex-1 p-4 sm:p-6 lg:p-8 overflow-auto">
          {children}
        </main>
      </div>
    </div>
  );
}