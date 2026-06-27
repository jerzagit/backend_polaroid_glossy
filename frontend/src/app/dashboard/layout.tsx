'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';

const menuItems = [
  { href: '/dashboard', label: 'Overview', icon: '[ ]' },
  { href: '/dashboard/orders', label: 'Orders', icon: '[~]' },
  { href: '/dashboard/users', label: 'Users', icon: '[@]', roles: ['ADMIN'] },
];

type ThemeMode = 'light' | 'dark';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { user, logout, isLoading } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [mounted, setMounted] = useState(false);
  const [theme, setTheme] = useState<ThemeMode>('light');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  useEffect(() => {
    const savedTheme = window.localStorage.getItem('seller-dashboard-theme');
    const nextTheme = savedTheme === 'dark' ? 'dark' : 'light';
    const savedSidebarState = window.localStorage.getItem('seller-dashboard-sidebar');
    setTheme(nextTheme);
    setSidebarCollapsed(savedSidebarState === 'collapsed');
    document.documentElement.dataset.theme = nextTheme;
    setMounted(true);
  }, []);

  const toggleTheme = () => {
    const nextTheme: ThemeMode = theme === 'light' ? 'dark' : 'light';
    setTheme(nextTheme);
    document.documentElement.dataset.theme = nextTheme;
    window.localStorage.setItem('seller-dashboard-theme', nextTheme);
  };

  const toggleSidebar = () => {
    setSidebarCollapsed((current) => {
      const next = !current;
      window.localStorage.setItem('seller-dashboard-sidebar', next ? 'collapsed' : 'expanded');
      return next;
    });
  };

  useEffect(() => {
    if (!isLoading && !user) {
      router.push('/login');
    }
  }, [user, isLoading, router]);

  if (isLoading || !mounted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg text-text-muted">
        <div className="glyph-scan border border-border bg-surface px-8 py-4 font-mono text-xs uppercase tracking-[0.15em]">LOADING_</div>
      </div>
    );
  }

  if (!user) return null;

  const filteredMenuItems = menuItems.filter(
    (item) => !item.roles || item.roles.includes(user.role)
  );

  return (
    <div className="min-h-screen flex bg-bg">
      <aside className={`${sidebarCollapsed ? 'w-[72px]' : 'w-64'} border-r border-border bg-surface flex flex-col transition-all duration-150 shrink-0`}>
        <div className={`${sidebarCollapsed ? 'p-3' : 'p-6'} border-b border-border transition-all duration-150`}>
          <div className="flex items-center justify-between gap-3">
            <h1 className={`font-display font-bold text-text-primary tracking-tight whitespace-nowrap overflow-hidden transition-all duration-150 ${sidebarCollapsed ? 'w-0 opacity-0 text-lg' : 'w-auto opacity-100 text-xl'}`}>
            SELLER
            </h1>
            <button
              onClick={toggleSidebar}
              aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              title={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              className="h-10 w-10 shrink-0 border border-border font-mono text-xs text-text-muted hover:text-text-primary hover:border-text-primary transition-all duration-150"
              style={{borderRadius: 0, background: 'transparent'}}
            >
              {sidebarCollapsed ? '[>]' : '[<]'}
            </button>
          </div>
          {!sidebarCollapsed && (
            <>
              <p className="font-mono text-xs text-text-muted mt-2 uppercase tracking-[0.15em] truncate">
                {user.name}
              </p>
              <span className="inline-block font-mono text-xs text-accent border border-accent px-2 py-0.5 mt-2 uppercase tracking-[0.1em]">
                {user.role}
              </span>
            </>
          )}
          <button
            onClick={toggleTheme}
            title={theme === 'light' ? 'Dark Theme' : 'Light Theme'}
            className={`mt-4 w-full font-mono text-xs uppercase tracking-[0.15em] border border-border text-text-muted hover:text-text-primary hover:border-text-primary transition-all duration-150 ${sidebarCollapsed ? 'h-10 px-0' : 'px-4 py-3'}`}
            style={{borderRadius: 0, background: 'transparent'}}
          >
            {sidebarCollapsed ? (theme === 'light' ? '[D]' : '[L]') : (theme === 'light' ? '[◐] Dark Theme' : '[○] Light Theme')}
          </button>
        </div>
        <nav className="flex-1 py-4">
          {filteredMenuItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              title={item.label}
              className={`flex items-center ${sidebarCollapsed ? 'justify-center px-0' : 'gap-3 px-6'} py-3 font-mono text-sm uppercase tracking-[0.15em] transition-all duration-150 ${
                pathname === item.href
                  ? 'text-accent bg-surface-2 border-l-2 border-accent'
                  : 'text-text-muted hover:text-text-primary hover:bg-surface-2'
              }`}
            >
              <span className="text-xs opacity-60">{item.icon}</span>
              {!sidebarCollapsed && <span>{item.label}</span>}
            </Link>
          ))}
        </nav>
        <div className={`${sidebarCollapsed ? 'p-3' : 'p-4'} border-t border-border`}>
          <button
            onClick={() => {
              logout();
              router.push('/login');
            }}
            title="Logout"
            className={`w-full font-mono text-xs uppercase tracking-[0.15em] border border-border text-text-muted hover:text-danger hover:border-danger transition-all duration-150 ${sidebarCollapsed ? 'h-10 px-0' : 'px-4 py-3'}`}
            style={{borderRadius: 0, background: 'transparent'}}
          >
            {sidebarCollapsed ? '[x]' : '[x] Logout'}
          </button>
        </div>
      </aside>
      <main className="flex-1 min-w-0 p-4 md:p-6 xl:p-8 overflow-auto">
        <div className="max-w-[1600px] mx-auto fade-in">
          {children}
        </div>
      </main>
    </div>
  );
}
