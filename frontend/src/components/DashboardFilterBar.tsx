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
const fieldLabelClass = 'mb-1 block font-mono text-[0.62rem] uppercase tracking-[0.12em] text-text-muted';
const fieldControlClass = 'h-9 w-full border border-border bg-transparent px-3 font-mono text-xs text-text-primary focus:border-text-primary focus:outline-none';
const selectControlClass = `${fieldControlClass} uppercase tracking-[0.1em]`;

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
    <div className="mb-5 border border-border bg-surface p-3 md:p-4" style={{borderRadius: 0}}>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-12 lg:items-end">
        <div className="col-span-2 sm:col-span-1 lg:col-span-2">
          <label className="block">
            <span className={fieldLabelClass}>Range</span>
            <select
              value={filters.rangeMode}
              onChange={(e) => update({ rangeMode: e.target.value as RangeMode })}
              className={selectControlClass}
              style={{borderRadius: 0}}
            >
              <option value="all">ALL TIME</option>
              <option value="week">WEEK</option>
              <option value="month">MONTH</option>
              <option value="custom">DATE RANGE</option>
            </select>
          </label>
        </div>

        <div className="col-span-2 sm:col-span-1 lg:col-span-2">
          {filters.rangeMode === 'week' ? (
            <label className="block">
              <span className={fieldLabelClass}>Week</span>
              <input
                type="week"
                value={filters.week}
                onChange={(e) => update({ week: e.target.value })}
                className={fieldControlClass}
                style={{borderRadius: 0}}
              />
            </label>
          ) : filters.rangeMode === 'month' ? (
            <label className="block">
              <span className={fieldLabelClass}>Month</span>
              <input
                type="month"
                value={filters.month}
                onChange={(e) => update({ month: e.target.value })}
                className={fieldControlClass}
                style={{borderRadius: 0}}
              />
            </label>
          ) : filters.rangeMode === 'custom' ? (
            <div className="grid grid-cols-2 gap-2">
              <label className="block">
                <span className={fieldLabelClass}>From</span>
                <input
                  type="date"
                  value={filters.fromDate}
                  onChange={(e) => update({ fromDate: e.target.value })}
                  className={fieldControlClass}
                  style={{borderRadius: 0}}
                />
              </label>
              <label className="block">
                <span className={fieldLabelClass}>To</span>
                <input
                  type="date"
                  value={filters.toDate}
                  onChange={(e) => update({ toDate: e.target.value })}
                  className={fieldControlClass}
                  style={{borderRadius: 0}}
                />
              </label>
            </div>
          ) : (
            <div>
              <span className={fieldLabelClass}>Period</span>
              <div className="flex h-9 items-center border border-border px-3 font-mono text-xs uppercase tracking-[0.1em] text-text-muted">
                Any date
              </div>
            </div>
          )}
        </div>

        <label className="col-span-2 sm:col-span-1 lg:col-span-2">
          <span className={fieldLabelClass}>Status</span>
          <select
            value={filters.status}
            onChange={(e) => update({ status: e.target.value })}
            className={selectControlClass}
            style={{borderRadius: 0}}
          >
            <option value="">ALL STATUSES</option>
            {ORDER_STATUSES.map((status) => (
              <option key={status} value={status}>{status}</option>
            ))}
          </select>
        </label>

        {showAdminSearch && (
          <>
            <label className="col-span-2 sm:col-span-1 lg:col-span-2">
              <span className={fieldLabelClass}>Order ID</span>
              <input
                value={filters.orderReference}
                onChange={(e) => update({ orderReference: e.target.value })}
                placeholder="PG..."
                className={`${fieldControlClass} placeholder:text-text-muted`}
                style={{borderRadius: 0}}
              />
            </label>
            <label className="col-span-2 sm:col-span-1 lg:col-span-2">
              <span className={fieldLabelClass}>Email</span>
              <input
                type="email"
                value={filters.customerEmail}
                onChange={(e) => update({ customerEmail: e.target.value })}
                placeholder="customer@email.com"
                className={`${fieldControlClass} placeholder:text-text-muted`}
                style={{borderRadius: 0}}
              />
            </label>
            <label className="col-span-2 sm:col-span-1 lg:col-span-1">
              <span className={fieldLabelClass}>Phone</span>
              <input
                value={filters.customerPhone}
                onChange={(e) => update({ customerPhone: e.target.value })}
                placeholder="+60 / 013"
                className={`${fieldControlClass} placeholder:text-text-muted`}
                style={{borderRadius: 0}}
              />
            </label>
          </>
        )}

        <div className={`col-span-2 grid grid-cols-2 gap-2 ${showAdminSearch ? 'lg:col-span-12 lg:flex lg:justify-end xl:col-span-1 xl:grid xl:grid-cols-1' : 'lg:col-span-6 lg:flex lg:justify-end'}`}>
          <button
            onClick={onApply}
            className="h-9 border border-accent px-3 font-mono text-xs uppercase tracking-[0.12em] text-accent transition-colors duration-150 hover:bg-surface-2 lg:min-w-24"
            style={{borderRadius: 0, background: 'transparent'}}
          >
            [~] Apply
          </button>
          <button
            onClick={reset}
            className="h-9 border border-border px-3 font-mono text-xs uppercase tracking-[0.12em] text-text-muted transition-colors duration-150 hover:text-text-primary lg:min-w-20"
            style={{borderRadius: 0, background: 'transparent'}}
          >
            Reset
          </button>
        </div>
      </div>
    </div>
  );
}
