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
  customerHouseUnitNo?: string;
  customerAddressLine1?: string;
  customerAddressLine2?: string;
  customerPostcode?: string;
  customerCity?: string;
  customerState: string;
  customerCountry?: string;
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

export type ProductStyle = 'FULL' | 'BORDER';

/** A purchasable package: whole-package price, serialized as an exact string. */
export interface CatalogPackage {
  id: number;
  quantity: number;
  price: string;
  currency: string;
  active: boolean;
}

/** A style (Full / Border) of a size, each with its own packages. */
export interface CatalogVariant {
  id: number;
  style: ProductStyle;
  label: string;
  active: boolean;
  packages: CatalogPackage[];
}

/** One product per print size; styles and packages hang beneath it. */
export interface CatalogProduct {
  image?: string | null;
  images: string[];
  id: number;
  code: string;
  name: string;
  displayName: string;
  width?: number;
  height?: number;
  price?: number;
  description?: string;
  tag?: string;
  active?: boolean;
  available?: boolean;
  variants: CatalogVariant[];
}

export interface UploadedFile {
  name: string;
  key: string;
  url: string;
}
