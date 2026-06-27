export type RangeMode = 'all' | 'week' | 'month' | 'custom';

export interface DashboardFilters {
  rangeMode: RangeMode;
  week: string;
  month: string;
  fromDate: string;
  toDate: string;
  status: string;
  orderReference: string;
  customerEmail: string;
  customerPhone: string;
}

const pad = (value: number) => value.toString().padStart(2, '0');

const toDateInput = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

const toMonthInput = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;

const toDateTimeParam = (date: Date) => (
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
);

const getWeekInput = (date: Date) => {
  const target = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNumber = target.getUTCDay() || 7;
  target.setUTCDate(target.getUTCDate() + 4 - dayNumber);
  const yearStart = new Date(Date.UTC(target.getUTCFullYear(), 0, 1));
  const weekNumber = Math.ceil((((target.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
  return `${target.getUTCFullYear()}-W${pad(weekNumber)}`;
};

const weekToRange = (value: string) => {
  const match = value.match(/^(\d{4})-W(\d{2})$/);
  if (!match) return null;

  const year = Number(match[1]);
  const week = Number(match[2]);
  const simple = new Date(Date.UTC(year, 0, 1 + (week - 1) * 7));
  const day = simple.getUTCDay() || 7;
  const monday = new Date(simple);
  monday.setUTCDate(simple.getUTCDate() - day + 1);
  const sunday = new Date(monday);
  sunday.setUTCDate(monday.getUTCDate() + 6);

  const from = new Date(monday.getUTCFullYear(), monday.getUTCMonth(), monday.getUTCDate(), 0, 0, 0);
  const to = new Date(sunday.getUTCFullYear(), sunday.getUTCMonth(), sunday.getUTCDate(), 23, 59, 59);
  return { from, to };
};

const monthToRange = (value: string) => {
  const match = value.match(/^(\d{4})-(\d{2})$/);
  if (!match) return null;

  const year = Number(match[1]);
  const month = Number(match[2]) - 1;
  return {
    from: new Date(year, month, 1, 0, 0, 0),
    to: new Date(year, month + 1, 0, 23, 59, 59),
  };
};

export const createDefaultDashboardFilters = (): DashboardFilters => {
  const now = new Date();
  return {
    rangeMode: 'month',
    week: getWeekInput(now),
    month: toMonthInput(now),
    fromDate: toDateInput(new Date(now.getFullYear(), now.getMonth(), 1)),
    toDate: toDateInput(now),
    status: '',
    orderReference: '',
    customerEmail: '',
    customerPhone: '',
  };
};

export const buildFilterParams = (filters: DashboardFilters) => {
  const params: Record<string, string> = {};

  if (filters.status) {
    params.status = filters.status;
  }
  if (filters.orderReference.trim()) {
    params.orderReference = filters.orderReference.trim();
  }
  if (filters.customerEmail.trim()) {
    params.customerEmail = filters.customerEmail.trim();
  }
  if (filters.customerPhone.trim()) {
    params.customerPhone = filters.customerPhone.trim();
  }

  let range: { from: Date; to: Date } | null = null;
  if (filters.rangeMode === 'week') {
    range = weekToRange(filters.week);
  } else if (filters.rangeMode === 'month') {
    range = monthToRange(filters.month);
  } else if (filters.rangeMode === 'custom' && filters.fromDate && filters.toDate) {
    range = {
      from: new Date(`${filters.fromDate}T00:00:00`),
      to: new Date(`${filters.toDate}T23:59:59`),
    };
  }

  if (range) {
    params.fromDate = toDateTimeParam(range.from);
    params.toDate = toDateTimeParam(range.to);
  }

  return params;
};
