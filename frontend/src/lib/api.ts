import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export const authAPI = {
  login: (email: string, password: string) =>
    api.post('/auth/login', { email, password }),
  register: (data: { email: string; password: string; name: string; phone?: string }) =>
    api.post('/auth/register', data),
  me: () => api.get('/auth/me'),
  updateProfile: (data: { name?: string; phone?: string }) =>
    api.put('/auth/profile', data),
};

export const orderAPI = {
  getAll: (page = 0, size = 20, status?: string) =>
    api.get('/admin/orders', { params: { page, size, status } }),
  getById: (id: string) => api.get(`/admin/orders/${id}`),
  getMyOrders: () => api.get('/orders/my'),
  updateStatus: (id: string, status: string, message?: string) =>
    api.patch(`/admin/orders/${id}/status`, { status, message }),
  updateTracking: (id: string, trackingNumber: string) =>
    api.patch(`/admin/orders/${id}/tracking`, { trackingNumber }),
  addNotes: (id: string, notes: string) =>
    api.post(`/admin/orders/${id}/notes`, { notes }),
};

export const userAPI = {
  getAll: (page = 0, size = 20) =>
    api.get('/admin/users', { params: { page, size } }),
  getById: (id: string) => api.get(`/admin/users/${id}`),
  updateRole: (id: string, role: string) =>
    api.patch(`/admin/users/${id}/role`, { role }),
};

export const statsAPI = {
  getOverview: () => api.get('/admin/stats/overview'),
  getSales: (from?: string, to?: string) =>
    api.get('/admin/stats/sales', { params: { from, to } }),
  getOrdersByStatus: () => api.get('/admin/stats/orders-by-status'),
  getTopSizes: () => api.get('/admin/stats/top-sizes'),
};

export default api;
