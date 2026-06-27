'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { statsAPI } from '@/lib/api';
import { StatsOverview } from '@/types';

export default function DashboardPage() {
  const { user } = useAuth();
  const [stats, setStats] = useState<StatsOverview | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await statsAPI.getOverview();
        setStats(response.data);
      } catch (error) {
        console.error('Failed to fetch stats', error);
      } finally {
        setLoading(false);
      }
    };
    if (user?.role === 'ADMIN' || user?.role === 'MARKETING' || user?.role === 'PACKER') {
      fetchStats();
    } else {
      setLoading(false);
    }
  }, [user]);

  if (loading) {
    return (
      <div className="rounded border border-border bg-surface p-8 text-center shadow-sm">
        <span className="text-sm text-text-muted">Loading overview...</span>
      </div>
    );
  }

  const canViewRevenue = user?.role === 'ADMIN';

  const statCards: { label: string; value: number | string; accent: boolean }[] = [
    { label: 'Total Orders', value: stats?.totalOrders || 0, accent: false },
    { label: 'Pending', value: stats?.pendingOrders || 0, accent: true },
    { label: 'Processing', value: stats?.processingOrders || 0, accent: false },
    { label: 'Delivered', value: stats?.deliveredOrders || 0, accent: false },
  ];

  if (canViewRevenue) {
    statCards.push({ label: 'Total Revenue', value: `RM ${stats?.totalRevenue?.toFixed(2) || '0.00'}`, accent: true });
  }
  statCards.push({ label: 'Total Customers', value: stats?.totalCustomers || 0, accent: false });

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display text-xl text-text-primary font-bold">Overview</h1>
        <p className="text-sm text-text-muted mt-1">Today&apos;s order, fulfillment, and customer activity.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((card) => (
          <div
            key={card.label}
            className="rounded border border-border bg-surface p-6 shadow-sm transition-colors duration-150 hover:border-accent"
          >
            <p className="text-sm text-text-muted mb-3">
              {card.label}
            </p>
            <p className={`font-display text-2xl font-bold ${card.accent ? 'text-accent' : 'text-text-primary'}`}>
              {typeof card.value === 'number' ? card.value.toLocaleString() : card.value}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
