// AdminSidebar - Left sidebar navigation for admin dashboard

import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Database, Server, BarChart2, Activity, HeartPulse, ArrowLeft } from 'lucide-react';

const adminNavItems = [
  { path: '/admin', label: 'Overview', icon: LayoutDashboard },
  { path: '/admin/redis', label: 'Redis', icon: Database },
  { path: '/admin/cache', label: 'Cache', icon: Server },
  { path: '/admin/circuit-breakers', label: 'Circuit Breakers', icon: Activity },
  { path: '/admin/kafka', label: 'Kafka', icon: BarChart2 },
  { path: '/admin/health', label: 'System Health', icon: HeartPulse },
] as const;

export function AdminSidebar() {

  return (
    <aside className="md:fixed left-0 top-16 bottom-0 z-[199] w-full md:w-64 bg-white/95 dark:bg-slate-950/95 border-r border-slate-200 dark:border-slate-800 flex flex-col">
      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        {adminNavItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === '/admin'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-indigo-600/20 text-indigo-700 dark:text-indigo-400 border border-indigo-500/30'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100/50 dark:hover:bg-slate-800/50'
              }`
            }
          >
            <item.icon className="w-5 h-5 flex-shrink-0" aria-hidden="true" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Footer */}
      <div className="p-4 border-t border-slate-200 dark:border-slate-800">
        <NavLink
          to="/"
          className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100/50 dark:hover:bg-slate-800/50 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" aria-hidden="true" />
          Back to Customer Portal
        </NavLink>
      </div>
    </aside>
  );
}
