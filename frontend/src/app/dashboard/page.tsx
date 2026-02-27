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

  if (loading) return <div>Loading...</div>;

  const canViewRevenue = user?.role === 'ADMIN';

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">Total Orders</h3>
          <p className="text-3xl font-bold">{stats?.totalOrders || 0}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">Pending</h3>
          <p className="text-3xl font-bold text-yellow-600">{stats?.pendingOrders || 0}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">Processing</h3>
          <p className="text-3xl font-bold text-blue-600">{stats?.processingOrders || 0}</p>
        </div>
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">Delivered</h3>
          <p className="text-3xl font-bold text-green-600">{stats?.deliveredOrders || 0}</p>
        </div>
        {canViewRevenue && (
          <div className="bg-white p-6 rounded-lg shadow">
            <h3 className="text-gray-500 text-sm">Total Revenue</h3>
            <p className="text-3xl font-bold text-green-600">
              ${stats?.totalRevenue?.toFixed(2) || '0.00'}
            </p>
          </div>
        )}
        <div className="bg-white p-6 rounded-lg shadow">
          <h3 className="text-gray-500 text-sm">Total Customers</h3>
          <p className="text-3xl font-bold">{stats?.totalCustomers || 0}</p>
        </div>
      </div>
    </div>
  );
}
