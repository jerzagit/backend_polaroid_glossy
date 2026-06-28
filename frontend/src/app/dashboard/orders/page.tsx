'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { orderAPI, fileAPI } from '@/lib/api';
import { Order, OrderStatus, PaginatedResponse, UploadedFile } from '@/types';
import DashboardFilterBar from '@/components/DashboardFilterBar';
import { DashboardFilters, buildFilterParams, createDefaultDashboardFilters } from '@/lib/dashboardFilters';

const statusColors: Record<OrderStatus, string> = {
  PENDING: 'text-text-muted border-border',
  PROCESSING: 'text-text-primary border-border glyph-pulse',
  POSTED: 'text-text-primary border-border',
  ON_DELIVERY: 'text-text-primary border-border',
  DELIVERED: 'text-text-muted border-border',
  CANCELLED: 'text-accent border-accent',
  REFUNDED: 'text-text-muted border-border',
};

const ITEMS_PER_PAGE = 12;
const ORDERS_PER_PAGE = 20;

const ORDER_STATUSES: string[] = ['PENDING', 'PROCESSING', 'POSTED', 'ON_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUNDED'];

export default function OrdersPage() {
  const { user } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [filters, setFilters] = useState<DashboardFilters>(() => createDefaultDashboardFilters());
  const [appliedFilters, setAppliedFilters] = useState<DashboardFilters>(() => createDefaultDashboardFilters());
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [updating, setUpdating] = useState(false);
  const [activeTab, setActiveTab] = useState<'details' | 'images'>('details');
  const [files, setFiles] = useState<UploadedFile[]>([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [filePage, setFilePage] = useState(0);
  const [downloading, setDownloading] = useState<string | null>(null);

  const fetchOrders = async () => {
    setLoading(true);
    try {
      const response = await orderAPI.getAll(page, ORDERS_PER_PAGE, buildFilterParams(appliedFilters));
      const data = response.data as PaginatedResponse<Order>;
      setOrders(data.content);
      setTotalPages(data.totalPages);
      if (selectedOrder && !data.content.some((order) => order.id === selectedOrder.id)) {
        setSelectedOrder(null);
        setFiles([]);
      }
    } catch (error) {
      console.error('Failed to fetch orders', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, [page, appliedFilters]);

  const applyFilters = () => {
    setPage(0);
    setAppliedFilters(filters);
  };

  const resetFilters = (nextFilters: DashboardFilters) => {
    setPage(0);
    setAppliedFilters(nextFilters);
  };

  const fetchFiles = async (orderId: string) => {
    setFilesLoading(true);
    setFilePage(0);
    try {
      const response = await fileAPI.listByOrder(orderId);
      setFiles(response.data as UploadedFile[]);
    } catch (error) {
      console.error('Failed to fetch files', error);
      setFiles([]);
    } finally {
      setFilesLoading(false);
    }
  };

  const handleSelectOrder = (order: Order) => {
    setSelectedOrder(order);
    setActiveTab('details');
    fetchFiles(order.id);
  };

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

  const handleDownloadAll = async () => {
    if (!selectedOrder) return;
    setDownloading('all');
    try {
      const response = await fileAPI.downloadAll(selectedOrder.id);
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const a = document.createElement('a');
      a.href = url;
      a.download = `${selectedOrder.orderNumber}_images.zip`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Failed to download zip', error);
    } finally {
      setDownloading(null);
    }
  };

  const handleDownloadFile = async (file: UploadedFile) => {
    setDownloading(file.key);
    try {
      const response = await fetch(file.url);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = file.name;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Failed to download file', error);
    } finally {
      setDownloading(null);
    }
  };

  const canUpdateStatus = user?.role === 'ADMIN' || user?.role === 'MARKETING' || user?.role === 'PACKER';
  const canViewImages = user?.role === 'ADMIN';
  const hasSelectedOrder = Boolean(selectedOrder);

  const paginatedFiles = files.slice(filePage * ITEMS_PER_PAGE, (filePage + 1) * ITEMS_PER_PAGE);
  const fileTotalPages = Math.ceil(files.length / ITEMS_PER_PAGE);

  return (
    <div>
      <div className="mb-8">
        <h1 className="font-display text-xl text-text-primary font-bold tracking-tight">Orders</h1>
        <hr className="border-none border-t border-border mt-2" />
      </div>

      <DashboardFilterBar
        filters={filters}
        onChange={setFilters}
        onApply={applyFilters}
        onReset={resetFilters}
        showAdminSearch={user?.role === 'ADMIN'}
      />

      <div className={`grid grid-cols-1 gap-6 items-start ${hasSelectedOrder ? 'xl:grid-cols-[minmax(380px,0.78fr)_minmax(620px,1.22fr)]' : ''}`}>
        <div className="min-w-0">
          {loading ? (
            <div className="glyph-scan p-8">
              <span className="font-mono text-xs text-text-muted uppercase tracking-[0.15em]">LOADING_</span>
            </div>
          ) : (
            <div className="border border-border overflow-x-auto" style={{borderRadius: 0, background: 'var(--color-surface)'}}>
              <table className={`w-full ${hasSelectedOrder ? 'min-w-[720px]' : 'min-w-[920px]'}`}>
                <thead>
                  <tr className="border-b border-border">
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">No#</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Order #</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Customer</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Total</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Status</th>
                    <th className={`font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal ${hasSelectedOrder ? 'hidden 2xl:table-cell' : ''}`}>Payment</th>
                    <th className={`font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal ${hasSelectedOrder ? 'hidden 2xl:table-cell' : ''}`}>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.map((order, index) => (
                    <tr
                      key={order.id}
                      onClick={() => handleSelectOrder(order)}
                      className={`cursor-pointer border-b border-border transition-colors duration-150 hover:bg-surface-2 ${
                        selectedOrder?.id === order.id ? 'bg-surface-2 border-l-2 border-l-accent' : ''
                      }`}
                    >
                      <td className="px-4 py-3 font-mono text-xs text-text-muted whitespace-nowrap">
                        {page * ORDERS_PER_PAGE + index + 1}
                      </td>
                      <td className="px-4 py-3 font-mono text-sm text-text-primary whitespace-nowrap">{order.orderNumber}</td>
                      <td className="px-4 py-3 font-mono text-sm text-text-primary">
                        <span className="block truncate max-w-[180px]">{order.customerName}</span>
                        {hasSelectedOrder && (
                          <span className="block 2xl:hidden mt-1 text-xs text-text-muted truncate max-w-[180px]">
                            {order.paymentStatus} | {new Date(order.createdAt).toLocaleDateString()}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 font-mono text-sm text-text-primary whitespace-nowrap">RM {order.total.toFixed(2)}</td>
                      <td className="px-4 py-3">
                        <span className={`font-mono text-xs uppercase tracking-[0.1em] border px-2 py-0.5 ${
                          statusColors[order.status] || 'border-border text-text-muted'
                        }`}>
                          {order.status}
                        </span>
                      </td>
                      <td className={`px-4 py-3 font-mono text-xs uppercase tracking-[0.1em] ${hasSelectedOrder ? 'hidden 2xl:table-cell' : ''}`}>
                        <span className={order.paymentStatus === 'FAILED' ? 'text-accent' : order.paymentStatus === 'PAID' ? 'text-text-primary' : 'text-text-muted glyph-pulse'}>
                          {order.paymentStatus}
                        </span>
                      </td>
                      <td className={`px-4 py-3 font-mono text-xs text-text-muted whitespace-nowrap ${hasSelectedOrder ? 'hidden 2xl:table-cell' : ''}`}>
                        {new Date(order.createdAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="mt-4 flex justify-center items-center gap-4">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="font-mono text-xs uppercase tracking-[0.15em] px-4 py-2 border border-border text-text-muted hover:text-text-primary disabled:opacity-30 transition-all duration-150"
              style={{borderRadius: 0, background: 'transparent'}}
            >
              {'[<]'} Prev
            </button>
            <span className="font-mono text-xs text-text-muted">
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page >= totalPages - 1}
              className="font-mono text-xs uppercase tracking-[0.15em] px-4 py-2 border border-border text-text-muted hover:text-text-primary disabled:opacity-30 transition-all duration-150"
              style={{borderRadius: 0, background: 'transparent'}}
            >
              Next {'[>]'}
            </button>
          </div>
        </div>

        <div className={`border border-border min-w-0 ${hasSelectedOrder ? 'xl:sticky xl:top-0' : ''}`} style={{borderRadius: 0, background: 'var(--color-surface)'}}>
          {selectedOrder ? (
            <div>
              <div className="border-b border-border">
                <div className="flex items-start justify-between gap-4 px-5 py-4">
                  <div className="min-w-0">
                    <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em]">Selected Order</p>
                    <h2 className="mt-1 font-display text-lg text-text-primary truncate">{selectedOrder.orderNumber}</h2>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedOrder(null);
                      setFiles([]);
                    }}
                    className="h-9 w-9 shrink-0 border border-border font-mono text-xs text-text-muted hover:text-danger hover:border-danger transition-all duration-150"
                    style={{borderRadius: 0, background: 'transparent'}}
                    title="Close details"
                  >
                    [x]
                  </button>
                </div>
                <div className="flex">
                  <button
                    onClick={() => setActiveTab('details')}
                    className={`flex-1 font-mono text-xs uppercase tracking-[0.15em] px-4 py-3 transition-colors duration-150 ${
                      activeTab === 'details'
                        ? 'text-accent border-b border-accent'
                        : 'text-text-muted hover:text-text-primary'
                    }`}
                  >
                    {'[i]'} Details
                  </button>
                  {canViewImages && (
                    <button
                      onClick={() => setActiveTab('images')}
                      className={`flex-1 font-mono text-xs uppercase tracking-[0.15em] px-4 py-3 transition-colors duration-150 ${
                        activeTab === 'images'
                          ? 'text-accent border-b border-accent'
                          : 'text-text-muted hover:text-text-primary'
                      }`}
                    >
                      {'[~]'} Files ({files.length})
                    </button>
                  )}
                </div>
              </div>

              {activeTab === 'details' && (
                <div className="p-5 md:p-6 space-y-6">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
                    <div>
                      <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Customer</p>
                      <p className="font-mono text-sm text-text-primary">{selectedOrder.customerName}</p>
                      <p className="font-mono text-xs text-text-muted break-all">{selectedOrder.customerEmail}</p>
                      {selectedOrder.customerPhone && (
                        <p className="font-mono text-xs text-text-muted">{selectedOrder.customerPhone}</p>
                      )}
                    </div>
                    <div>
                      <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Payment</p>
                      <p className="font-mono text-sm text-text-primary">RM {selectedOrder.total.toFixed(2)}</p>
                      <p className="font-mono text-xs text-text-muted uppercase tracking-[0.1em]">{selectedOrder.paymentStatus}</p>
                    </div>
                  </div>
                  <div className="border-t border-border pt-5">
                    <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Delivery Address</p>
                    <div className="font-mono text-sm text-text-primary space-y-1">
                      {selectedOrder.customerHouseUnitNo && <p>{selectedOrder.customerHouseUnitNo}</p>}
                      {selectedOrder.customerAddressLine1 && <p>{selectedOrder.customerAddressLine1}</p>}
                      {selectedOrder.customerAddressLine2 && <p>{selectedOrder.customerAddressLine2}</p>}
                      {(selectedOrder.customerPostcode || selectedOrder.customerCity || selectedOrder.customerState) && (
                        <p>
                          {[selectedOrder.customerPostcode, selectedOrder.customerCity, selectedOrder.customerState?.toUpperCase()].filter(Boolean).join(' ')}
                        </p>
                      )}
                      <p>{selectedOrder.customerCountry || 'Malaysia'}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-5">
                    <div>
                      <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Tracking</p>
                      <input
                        type="text"
                        defaultValue={selectedOrder.trackingNumber || ''}
                        onBlur={(e) => handleTrackingUpdate(selectedOrder.id, e.target.value)}
                        placeholder="TRACKING NUMBER"
                        className="w-full font-mono text-sm bg-transparent border-b border-border text-text-primary placeholder:text-text-muted pb-1 focus:outline-none focus:border-text-primary transition-colors duration-150"
                        style={{borderRadius: 0}}
                      />
                    </div>
                    <div>
                      <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Status</p>
                      <select
                        value={selectedOrder.status}
                        onChange={(e) => handleStatusUpdate(selectedOrder.id, e.target.value)}
                        disabled={!canUpdateStatus || updating}
                        className="w-full font-mono text-sm bg-transparent border border-border text-text-primary px-3 py-2 focus:outline-none focus:border-text-primary transition-colors duration-150 disabled:opacity-50"
                        style={{borderRadius: 0}}
                      >
                        {ORDER_STATUSES.map((s) => (
                          <option key={s} value={s}>{s}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                  <div>
                    <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Notes</p>
                    <textarea
                      defaultValue={selectedOrder.notes || ''}
                      onBlur={(e) => orderAPI.addNotes(selectedOrder.id, e.target.value)}
                      placeholder="ADD NOTES..."
                      className="w-full font-mono text-sm bg-transparent border border-border text-text-primary placeholder:text-text-muted px-3 py-2 focus:outline-none focus:border-text-primary transition-colors duration-150"
                      style={{borderRadius: 0}}
                      rows={3}
                    />
                  </div>
                  <hr className="border-none border-t border-border" />
                  <div className="space-y-1">
                    <p className="font-mono text-xs text-text-muted">
                      CREATED: {new Date(selectedOrder.createdAt).toLocaleString()}
                    </p>
                    {selectedOrder.paidAt && (
                      <p className="font-mono text-xs text-text-muted">
                        PAID: {new Date(selectedOrder.paidAt).toLocaleString()}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {canViewImages && activeTab === 'images' && (
                <div className="p-5 md:p-6">
                  {filesLoading ? (
                    <div className="glyph-scan p-8 text-center">
                      <span className="font-mono text-xs text-text-muted uppercase tracking-[0.15em]">LOADING_</span>
                    </div>
                  ) : files.length === 0 ? (
                    <p className="font-mono text-xs text-text-muted text-center py-8 uppercase tracking-[0.15em]">
                      No images uploaded
                    </p>
                  ) : (
                    <>
                      <button
                        onClick={handleDownloadAll}
                        disabled={downloading === 'all'}
                        className="w-full mb-4 font-mono text-xs uppercase tracking-[0.15em] px-4 py-3 border border-border text-text-primary hover:bg-surface transition-all duration-150 disabled:opacity-50"
                        style={{borderRadius: 0, background: 'transparent'}}
                      >
                        {downloading === 'all' ? 'DOWNLOADING...' : `[~] Download ZIP (${files.length})`}
                      </button>

                      <div className="grid grid-cols-2 2xl:grid-cols-3 gap-4">
                        {paginatedFiles.map((file) => (
                          <div key={file.key} className="relative group border border-border" style={{borderRadius: 0}}>
                            <img
                              src={file.url}
                              alt={file.name}
                              className="w-full h-36 md:h-44 object-cover"
                              loading="lazy"
                            />
                            <div className="absolute inset-0 bg-black bg-opacity-0 group-hover:bg-opacity-60 transition-all duration-150 flex items-center justify-center">
                              <button
                                onClick={() => handleDownloadFile(file)}
                                disabled={downloading === file.key}
                                className="opacity-0 group-hover:opacity-100 font-mono text-xs uppercase tracking-[0.15em] px-3 py-1.5 border border-text-primary text-text-primary transition-all duration-150"
                                style={{borderRadius: 0, background: 'rgba(0,0,0,0.6)'}}
                              >
                                {downloading === file.key ? '...' : `[v] Download`}
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>

                      {fileTotalPages > 1 && (
                        <div className="mt-4 flex justify-center items-center gap-3">
                          <button
                            onClick={() => setFilePage(p => Math.max(0, p - 1))}
                            disabled={filePage === 0}
                            className="font-mono text-xs uppercase tracking-[0.1em] px-3 py-1.5 border border-border text-text-muted hover:text-text-primary disabled:opacity-30 transition-all duration-150"
                            style={{borderRadius: 0, background: 'transparent'}}
                          >
                            {'[<]'}
                          </button>
                          <span className="font-mono text-xs text-text-muted">
                            {filePage + 1} / {fileTotalPages}
                          </span>
                          <button
                            onClick={() => setFilePage(p => p + 1)}
                            disabled={filePage >= fileTotalPages - 1}
                            className="font-mono text-xs uppercase tracking-[0.1em] px-3 py-1.5 border border-border text-text-muted hover:text-text-primary disabled:opacity-30 transition-all duration-150"
                            style={{borderRadius: 0, background: 'transparent'}}
                          >
                            {'[>]'}
                          </button>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="p-6 text-center">
              <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em]">
                Select an order
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
