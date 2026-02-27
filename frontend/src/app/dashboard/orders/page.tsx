'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { orderAPI } from '@/lib/api';
import { Order, OrderStatus, PaginatedResponse } from '@/types';

const statusColors: Record<OrderStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  PROCESSING: 'bg-blue-100 text-blue-800',
  POSTED: 'bg-purple-100 text-purple-800',
  ON_DELIVERY: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
  REFUNDED: 'bg-gray-100 text-gray-800',
};

export default function OrdersPage() {
  const { user } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [updating, setUpdating] = useState(false);

  const fetchOrders = async () => {
    setLoading(true);
    try {
      const response = await orderAPI.getAll(page, 20, statusFilter || undefined);
      const data = response.data as PaginatedResponse<Order>;
      setOrders(data.content);
      setTotalPages(data.totalPages);
    } catch (error) {
      console.error('Failed to fetch orders', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, [page, statusFilter]);

  const handleStatusUpdate = async (orderId: string, newStatus: string) => {
    setUpdating(true);
    try {
      await orderAPI.updateStatus(orderId, newStatus);
      fetchOrders();
      if (selectedOrder) {
        const response = await orderAPI.getById(orderId);
        setSelectedOrder(response.data);
      }
    } catch (error) {
      console.error('Failed to update status', error);
    } finally {
      setUpdating(false);
    }
  };

  const handleTrackingUpdate = async (orderId: string, trackingNumber: string) => {
    setUpdating(true);
    try {
      await orderAPI.updateTracking(orderId, trackingNumber);
      fetchOrders();
      if (selectedOrder) {
        const response = await orderAPI.getById(orderId);
        setSelectedOrder(response.data);
      }
    } catch (error) {
      console.error('Failed to update tracking', error);
    } finally {
      setUpdating(false);
    }
  };

  const canUpdateStatus = user?.role === 'ADMIN' || user?.role === 'MARKETING' || user?.role === 'PACKER';

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Orders</h1>
      
      <div className="mb-4 flex gap-2">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg"
        >
          <option value="">All Statuses</option>
          <option value="PENDING">Pending</option>
          <option value="PROCESSING">Processing</option>
          <option value="POSTED">Posted</option>
          <option value="ON_DELIVERY">On Delivery</option>
          <option value="DELIVERED">Delivered</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <button
          onClick={fetchOrders}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          Refresh
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          {loading ? (
            <div>Loading...</div>
          ) : (
            <div className="bg-white rounded-lg shadow overflow-hidden">
              <table className="min-w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Order #</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Customer</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Total</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Payment</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {orders.map((order) => (
                    <tr
                      key={order.id}
                      onClick={() => setSelectedOrder(order)}
                      className={`cursor-pointer hover:bg-gray-50 ${selectedOrder?.id === order.id ? 'bg-blue-50' : ''}`}
                    >
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">{order.orderNumber}</td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">{order.customerName}</td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">${order.total.toFixed(2)}</td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`px-2 py-1 text-xs rounded-full ${statusColors[order.status]}`}>
                          {order.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">
                        <span className={order.paymentStatus === 'PAID' ? 'text-green-600' : 'text-yellow-600'}>
                          {order.paymentStatus}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {new Date(order.createdAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="mt-4 flex justify-center gap-2">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-4 py-2 border rounded-lg disabled:opacity-50"
            >
              Previous
            </button>
            <span className="px-4 py-2">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page >= totalPages - 1}
              className="px-4 py-2 border rounded-lg disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          {selectedOrder ? (
            <div>
              <h2 className="text-lg font-bold mb-4">Order Details</h2>
              <div className="space-y-3">
                <div>
                  <label className="text-sm text-gray-500">Order Number</label>
                  <p className="font-medium">{selectedOrder.orderNumber}</p>
                </div>
                <div>
                  <label className="text-sm text-gray-500">Customer</label>
                  <p className="font-medium">{selectedOrder.customerName}</p>
                  <p className="text-sm">{selectedOrder.customerEmail}</p>
                  <p className="text-sm">{selectedOrder.customerPhone}</p>
                </div>
                <div>
                  <label className="text-sm text-gray-500">State</label>
                  <p className="font-medium">{selectedOrder.customerState.toUpperCase()}</p>
                </div>
                <div>
                  <label className="text-sm text-gray-500">Total</label>
                  <p className="font-medium">${selectedOrder.total.toFixed(2)}</p>
                </div>
                <div>
                  <label className="text-sm text-gray-500">Tracking #</label>
                  <input
                    type="text"
                    defaultValue={selectedOrder.trackingNumber || ''}
                    onBlur={(e) => handleTrackingUpdate(selectedOrder.id, e.target.value)}
                    placeholder="Enter tracking number"
                    className="w-full mt-1 px-3 py-2 border rounded-lg text-sm"
                  />
                </div>
                <div>
                  <label className="text-sm text-gray-500">Status</label>
                  <select
                    value={selectedOrder.status}
                    onChange={(e) => handleStatusUpdate(selectedOrder.id, e.target.value)}
                    disabled={!canUpdateStatus || updating}
                    className="w-full mt-1 px-3 py-2 border rounded-lg"
                  >
                    <option value="PENDING">Pending</option>
                    <option value="PROCESSING">Processing</option>
                    <option value="POSTED">Posted</option>
                    <option value="ON_DELIVERY">On Delivery</option>
                    <option value="DELIVERED">Delivered</option>
                    <option value="CANCELLED">Cancelled</option>
                    <option value="REFUNDED">Refunded</option>
                  </select>
                </div>
                <div>
                  <label className="text-sm text-gray-500">Notes</label>
                  <textarea
                    defaultValue={selectedOrder.notes || ''}
                    onBlur={(e) => orderAPI.addNotes(selectedOrder.id, e.target.value)}
                    placeholder="Add notes..."
                    className="w-full mt-1 px-3 py-2 border rounded-lg text-sm"
                    rows={3}
                  />
                </div>
                <div className="pt-4 border-t">
                  <p className="text-xs text-gray-500">
                    Created: {new Date(selectedOrder.createdAt).toLocaleString()}
                  </p>
                  {selectedOrder.paidAt && (
                    <p className="text-xs text-gray-500">
                      Paid: {new Date(selectedOrder.paidAt).toLocaleString()}
                    </p>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <p className="text-gray-500 text-center">Select an order to view details</p>
          )}
        </div>
      </div>
    </div>
  );
}
