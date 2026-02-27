'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { userAPI } from '@/lib/api';
import { User, Role, PaginatedResponse } from '@/types';

const roleColors: Record<Role, string> = {
  CUSTOMER: 'bg-gray-100 text-gray-800',
  AFFILIATE: 'bg-yellow-100 text-yellow-800',
  PACKER: 'bg-blue-100 text-blue-800',
  MARKETING: 'bg-purple-100 text-purple-800',
  ADMIN: 'bg-red-100 text-red-800',
};

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [updating, setUpdating] = useState(false);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await userAPI.getAll(page, 20);
      const data = response.data as PaginatedResponse<User>;
      setUsers(data.content);
      setTotalPages(data.totalPages);
    } catch (error) {
      console.error('Failed to fetch users', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, [page]);

  const handleRoleUpdate = async (userId: string, newRole: string) => {
    setUpdating(true);
    try {
      await userAPI.updateRole(userId, newRole);
      fetchUsers();
      if (selectedUser?.id === userId) {
        const response = await userAPI.getById(userId);
        setSelectedUser(response.data);
      }
    } catch (error) {
      console.error('Failed to update role', error);
    } finally {
      setUpdating(false);
    }
  };

  const canManageUsers = currentUser?.role === 'ADMIN';

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Users</h1>

      <div className="mb-4 flex justify-end">
        <button
          onClick={fetchUsers}
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
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Phone</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Role</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Joined</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {users.map((user) => (
                    <tr
                      key={user.id}
                      onClick={() => setSelectedUser(user)}
                      className={`cursor-pointer hover:bg-gray-50 ${selectedUser?.id === user.id ? 'bg-blue-50' : ''}`}
                    >
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">{user.name}</td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">{user.email}</td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm">{user.phone || '-'}</td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`px-2 py-1 text-xs rounded-full ${roleColors[user.role]}`}>
                          {user.role}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={user.isActive ? 'text-green-600' : 'text-red-600'}>
                          {user.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {new Date(user.createdAt).toLocaleDateString()}
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
          {selectedUser ? (
            <div>
              <h2 className="text-lg font-bold mb-4">User Details</h2>
              <div className="space-y-3">
                <div>
                  <label className="text-sm text-gray-500">Name</label>
                  <p className="font-medium">{selectedUser.name}</p>
                </div>
                <div>
                  <label className="text-sm text-gray-500">Email</label>
                  <p className="font-medium">{selectedUser.email}</p>
                </div>
                <div>
                  <label className="text-sm text-gray-500">Phone</label>
                  <p className="font-medium">{selectedUser.phone || '-'}</p>
                </div>
                <div>
                  <label className="text-sm text-gray-500">Affiliate Code</label>
                  <p className="font-medium">{selectedUser.affiliateCode || '-'}</p>
                </div>
                {canManageUsers && (
                  <div>
                    <label className="text-sm text-gray-500">Role</label>
                    <select
                      value={selectedUser.role}
                      onChange={(e) => handleRoleUpdate(selectedUser.id, e.target.value)}
                      disabled={updating || selectedUser.id === currentUser?.id}
                      className="w-full mt-1 px-3 py-2 border rounded-lg"
                    >
                      <option value="CUSTOMER">Customer</option>
                      <option value="AFFILIATE">Affiliate</option>
                      <option value="PACKER">Packer</option>
                      <option value="MARKETING">Marketing</option>
                      <option value="ADMIN">Admin</option>
                    </select>
                  </div>
                )}
                <div>
                  <label className="text-sm text-gray-500">Status</label>
                  <p className={`font-medium ${selectedUser.isActive ? 'text-green-600' : 'text-red-600'}`}>
                    {selectedUser.isActive ? 'Active' : 'Inactive'}
                  </p>
                </div>
                <div className="pt-4 border-t">
                  <p className="text-xs text-gray-500">
                    Joined: {new Date(selectedUser.createdAt).toLocaleString()}
                  </p>
                  <p className="text-xs text-gray-500">
                    Updated: {new Date(selectedUser.updatedAt).toLocaleString()}
                  </p>
                </div>
              </div>
            </div>
          ) : (
            <p className="text-gray-500 text-center">Select a user to view details</p>
          )}
        </div>
      </div>
    </div>
  );
}
