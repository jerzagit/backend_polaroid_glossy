'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { userAPI } from '@/lib/api';
import { User, Role, PaginatedResponse } from '@/types';

const roleColors: Record<Role, string> = {
  CUSTOMER: 'text-text-muted border-border',
  AFFILIATE: 'text-text-muted border-border',
  PACKER: 'text-text-primary border-border',
  MARKETING: 'text-text-primary border-border',
  ADMIN: 'text-accent border-accent',
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
      <div className="mb-8">
        <h1 className="font-display text-xl text-text-primary font-bold tracking-tight">Users</h1>
        <hr className="border-none border-t border-border mt-2" />
      </div>

      <div className="mb-6">
        <button
          onClick={fetchUsers}
          className="font-mono text-xs uppercase tracking-[0.15em] px-4 py-2 border border-border text-text-muted hover:text-text-primary hover:bg-surface transition-all duration-150"
          style={{borderRadius: 0, background: 'transparent'}}
        >
          [~] Refresh
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          {loading ? (
            <div className="glyph-scan p-8">
              <span className="font-mono text-xs text-text-muted uppercase tracking-[0.15em]">LOADING_</span>
            </div>
          ) : (
            <div className="border border-border" style={{borderRadius: 0, background: 'var(--color-surface)'}}>
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border">
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Name</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Email</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Phone</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Role</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Status</th>
                    <th className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] px-4 py-3 text-left font-normal">Joined</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => (
                    <tr
                      key={user.id}
                      onClick={() => setSelectedUser(user)}
                      className={`cursor-pointer border-b border-border transition-colors duration-150 hover:bg-surface-2 ${
                        selectedUser?.id === user.id ? 'bg-surface-2 border-l-2 border-l-accent' : ''
                      }`}
                    >
                      <td className="px-4 py-3 font-mono text-sm text-text-primary">{user.name}</td>
                      <td className="px-4 py-3 font-mono text-sm text-text-primary">{user.email}</td>
                      <td className="px-4 py-3 font-mono text-sm text-text-muted">{user.phone || '-'}</td>
                      <td className="px-4 py-3">
                        <span className={`font-mono text-xs uppercase tracking-[0.1em] border px-2 py-0.5 ${roleColors[user.role]}`}>
                          {user.role}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs uppercase tracking-[0.1em]">
                        <span className={user.isActive ? 'text-text-primary' : 'text-accent'}>
                          {user.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-text-muted">
                        {new Date(user.createdAt).toLocaleDateString()}
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

        <div className="border border-border" style={{borderRadius: 0, background: 'var(--color-surface)'}}>
          {selectedUser ? (
            <div className="p-6 space-y-5">
              <div>
                <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Name</p>
                <p className="font-mono text-sm text-text-primary">{selectedUser.name}</p>
              </div>
              <div>
                <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Email</p>
                <p className="font-mono text-sm text-text-primary">{selectedUser.email}</p>
              </div>
              <div>
                <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Phone</p>
                <p className="font-mono text-sm text-text-muted">{selectedUser.phone || '-'}</p>
              </div>
              <div>
                <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Affiliate Code</p>
                <p className="font-mono text-sm text-text-muted">{selectedUser.affiliateCode || '-'}</p>
              </div>
              <div>
                <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Status</p>
                <p className={`font-mono text-sm ${selectedUser.isActive ? 'text-text-primary' : 'text-accent'}`}>
                  {selectedUser.isActive ? 'Active' : 'Inactive'}
                </p>
              </div>
              <div>
                <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Role</p>
                <span className={`font-mono text-xs uppercase tracking-[0.1em] border px-2 py-0.5 ${roleColors[selectedUser.role]}`}>
                  {selectedUser.role}
                </span>
              </div>
              {canManageUsers && (
                <div>
                  <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em] mb-1.5">Update Role</p>
                  <select
                    value={selectedUser.role}
                    onChange={(e) => handleRoleUpdate(selectedUser.id, e.target.value)}
                    disabled={updating || selectedUser.id === currentUser?.id}
                    className="w-full font-mono text-sm bg-transparent border border-border text-text-primary px-3 py-2 focus:outline-none focus:border-text-primary transition-colors duration-150 disabled:opacity-50"
                    style={{borderRadius: 0}}
                  >
                    <option value="CUSTOMER">CUSTOMER</option>
                    <option value="AFFILIATE">AFFILIATE</option>
                    <option value="PACKER">PACKER</option>
                    <option value="MARKETING">MARKETING</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </div>
              )}
              <hr className="border-none border-t border-border" />
              <div className="space-y-1">
                <p className="font-mono text-xs text-text-muted">
                  JOINED: {new Date(selectedUser.createdAt).toLocaleString()}
                </p>
                <p className="font-mono text-xs text-text-muted">
                  UPDATED: {new Date(selectedUser.updatedAt).toLocaleString()}
                </p>
              </div>
            </div>
          ) : (
            <div className="p-6 text-center">
              <p className="font-mono text-xs text-text-muted uppercase tracking-[0.15em]">
                Select a user
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
