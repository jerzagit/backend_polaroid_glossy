export type Role = 'CUSTOMER' | 'AFFILIATE' | 'PACKER' | 'MARKETING' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  name: string;
  phone?: string;
  avatarUrl?: string;
  role: Role;
  affiliateCode?: string;
  referredBy?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export type OrderStatus = 'PENDING' | 'PROCESSING' | 'POSTED' | 'ON_DELIVERY' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED';
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED';

export interface OrderItem {
  id: string;
  orderId: string;
  sizeId: string;
  sizeName: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  images: string[];
  customTexts: string[];
  s3Keys: string[];
  createdAt: string;
}

export interface Order {
  id: string;
  orderNumber: string;
  userId?: string;
  affiliateId?: string;
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  customerState: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  paymentMethod?: string;
  toyyibpayRef?: string;
  subtotal: number;
  shipping: number;
  total: number;
  paidAt?: string;
  trackingNumber?: string;
  shippedAt?: string;
  deliveredAt?: string;
  cancelledAt?: string;
  cancelReason?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
  items?: OrderItem[];
}

export interface AuthResponse {
  token: string;
  refreshToken?: string;
  user: User;
}

export interface StatsOverview {
  totalOrders: number;
  pendingOrders: number;
  processingOrders: number;
  deliveredOrders: number;
  totalRevenue?: number;
  totalCustomers: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
