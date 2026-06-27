'use client';

import { DashboardFilters, RangeMode, createDefaultDashboardFilters } from '@/lib/dashboardFilters';

interface DashboardFilterBarProps {
  filters: DashboardFilters;
  onChange: (filters: DashboardFilters) => void;
  onApply: () => void;
  onReset?: (filters: DashboardFilters) => void;
  showAdminSearch?: boolean;
}

const ORDER_STATUSES = ['PENDING', 'PROCESSING', 'POSTED', 'ON_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUNDED'];

export default function DashboardFilterBar({
  filters,
  onChange,
  onApply,
  onReset,
  showAdminSearch = false,
}: DashboardFilterBarProps) {
  const update = (patch: Partial<DashboardFilters>) => onChange({ ...filters, ...patch });
  const reset = () => {
    const nextFilters = createDefaultDashboardFilters();
    onChange(nextFilters);
    onReset?.(nextFilters);
  };

  return (
    <div className="mb-6 border border-border bg-surface p-4" style={{borderRadius: 0}}>
      <div className="grid grid-cols-1 gap-3 xl:grid-cols-[1.1fr_1fr_1fr_auto]">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">Range</span>
            <select
              value={filters.rangeMode}
              onChange={(e) => update({ rangeMode: e.target.value as RangeMode })}
              className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs uppercase tracking-[0.12em] text-text-primary focus:border-text-primary focus:outline-none"
              style={{borderRadius: 0}}
            >
              <option value="all">ALL TIME</option>
              <option value="week">WEEK</option>
              <option value="month">MONTH</option>
              <option value="custom">DATE RANGE</option>
            </select>
          </label>

          {filters.rangeMode === 'week' && (
            <label className="block">
              <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">Week</span>
              <input
                type="week"
                value={filters.week}
                onChange={(e) => update({ week: e.target.value })}
                className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs text-text-primary focus:border-text-primary focus:outline-none"
                style={{borderRadius: 0}}
              />
            </label>
          )}

          {filters.rangeMode === 'month' && (
            <label className="block">
              <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">Month</span>
              <input
                type="month"
                value={filters.month}
                onChange={(e) => update({ month: e.target.value })}
                className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs text-text-primary focus:border-text-primary focus:outline-none"
                style={{borderRadius: 0}}
              />
            </label>
          )}

          {filters.rangeMode === 'custom' && (
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">From</span>
                <input
                  type="date"
                  value={filters.fromDate}
                  onChange={(e) => update({ fromDate: e.target.value })}
                  className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs text-text-primary focus:border-text-primary focus:outline-none"
                  style={{borderRadius: 0}}
                />
              </label>
              <label className="block">
                <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">To</span>
                <input
                  type="date"
                  value={filters.toDate}
                  onChange={(e) => update({ toDate: e.target.value })}
                  className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs text-text-primary focus:border-text-primary focus:outline-none"
                  style={{borderRadius: 0}}
                />
              </label>
            </div>
          )}
        </div>

        <label className="block">
          <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">Status</span>
          <select
            value={filters.status}
            onChange={(e) => update({ status: e.target.value })}
            className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs uppercase tracking-[0.12em] text-text-primary focus:border-text-primary focus:outline-none"
            style={{borderRadius: 0}}
          >
            <option value="">ALL STATUSES</option>
            {ORDER_STATUSES.map((status) => (
              <option key={status} value={status}>{status}</option>
            ))}
          </select>
        </label>

        {showAdminSearch ? (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 xl:grid-cols-1">
            <label className="block">
              <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">Order ID</span>
              <input
                value={filters.orderReference}
                onChange={(e) => update({ orderReference: e.target.value })}
                placeholder="PG..."
                className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs text-text-primary placeholder:text-text-muted focus:border-text-primary focus:outline-none"
                style={{borderRadius: 0}}
              />
            </label>
            <label className="block">
              <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">Email</span>
              <input
                type="email"
                value={filters.customerEmail}
                onChange={(e) => update({ customerEmail: e.target.value })}
                placeholder="customer@email.com"
                className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs text-text-primary placeholder:text-text-muted focus:border-text-primary focus:outline-none"
                style={{borderRadius: 0}}
              />
            </label>
            <label className="block">
              <span className="mb-1.5 block font-mono text-xs uppercase tracking-[0.15em] text-text-muted">Phone</span>
              <input
                value={filters.customerPhone}
                onChange={(e) => update({ customerPhone: e.target.value })}
                placeholder="+60 / 013"
                className="w-full border border-border bg-transparent px-3 py-2 font-mono text-xs text-text-primary placeholder:text-text-muted focus:border-text-primary focus:outline-none"
                style={{borderRadius: 0}}
              />
            </label>
          </div>
        ) : (
          <div />
        )}

        <div className="flex items-end gap-2">
          <button
            onClick={onApply}
            className="h-[34px] flex-1 border border-accent px-4 font-mono text-xs uppercase tracking-[0.15em] text-accent transition-colors duration-150 hover:bg-surface-2"
            style={{borderRadius: 0, background: 'transparent'}}
          >
            [~] Apply
          </button>
          <button
            onClick={reset}
            className="h-[34px] border border-border px-4 font-mono text-xs uppercase tracking-[0.15em] text-text-muted transition-colors duration-150 hover:text-text-primary"
            style={{borderRadius: 0, background: 'transparent'}}
          >
            Reset
          </button>
        </div>
      </div>
    </div>
  );
}
