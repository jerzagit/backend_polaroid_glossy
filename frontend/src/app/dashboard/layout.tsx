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

  useEffect(() => {
    const savedTheme = window.localStorage.getItem('seller-dashboard-theme');
    const nextTheme = savedTheme === 'dark' ? 'dark' : 'light';
    setTheme(nextTheme);
    document.documentElement.dataset.theme = nextTheme;
    setMounted(true);
  }, []);

  const toggleTheme = () => {
    const nextTheme: ThemeMode = theme === 'light' ? 'dark' : 'light';
    setTheme(nextTheme);
    document.documentElement.dataset.theme = nextTheme;
    window.localStorage.setItem('seller-dashboard-theme', nextTheme);
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
      <aside className="w-64 border-r border-border bg-surface flex flex-col">
        <div className="p-6 border-b border-border">
          <h1 className="font-display text-xl font-bold text-text-primary tracking-tight">
            SELLER
          </h1>
          <p className="font-mono text-xs text-text-muted mt-2 uppercase tracking-[0.15em]">
            {user.name}
          </p>
          <span className="inline-block font-mono text-xs text-accent border border-accent px-2 py-0.5 mt-2 uppercase tracking-[0.1em]">
            {user.role}
          </span>
          <button
            onClick={toggleTheme}
            className="mt-4 w-full font-mono text-xs uppercase tracking-[0.15em] px-4 py-3 border border-border text-text-muted hover:text-text-primary hover:border-text-primary transition-all duration-150"
            style={{borderRadius: 0, background: 'transparent'}}
          >
            {theme === 'light' ? '[◐] Dark Theme' : '[○] Light Theme'}
          </button>
        </div>
        <nav className="flex-1 py-4">
          {filteredMenuItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 px-6 py-3 font-mono text-sm uppercase tracking-[0.15em] transition-all duration-150 ${
                pathname === item.href
                  ? 'text-accent bg-surface-2 border-l-2 border-accent'
                  : 'text-text-muted hover:text-text-primary hover:bg-surface-2'
              }`}
            >
              <span className="text-xs opacity-50">{item.icon}</span>
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="p-4 border-t border-border">
          <button
            onClick={() => {
              logout();
              router.push('/login');
            }}
            className="w-full font-mono text-xs uppercase tracking-[0.15em] px-4 py-3 border border-border text-text-muted hover:text-danger hover:border-danger transition-all duration-150"
            style={{borderRadius: 0, background: 'transparent'}}
          >
            [x] Logout
          </button>
        </div>
      </aside>
      <main className="flex-1 p-8 overflow-auto">
        <div className="max-w-[1200px] mx-auto fade-in">
          {children}
        </div>
      </main>
    </div>
  );
}
