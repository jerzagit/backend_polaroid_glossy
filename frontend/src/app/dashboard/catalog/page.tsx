'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { catalogAPI } from '@/lib/api';
import { CatalogProduct, ProductStyle } from '@/types';

interface SizeDraft {
  name: string;
  displayName: string;
  width: string;
  height: string;
  basePrice: string;
  tag: string;
  isActive: boolean;
}

interface NewSizeDraft {
  code: string;
  name: string;
  displayName: string;
  width: string;
  height: string;
  basePrice: string;
  tag: string;
}

const emptySizeDraft = (): SizeDraft => ({
  name: '',
  displayName: '',
  width: '',
  height: '',
  basePrice: '',
  tag: '',
  isActive: true,
});

const emptyNewSizeDraft = (): NewSizeDraft => ({
  code: '',
  name: '',
  displayName: '',
  width: '',
  height: '',
  basePrice: '',
  tag: '',
});

function errorMessage(error: unknown): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string };
  return err?.response?.data?.message || err?.message || 'Something went wrong';
}

function toNumberOrUndefined(value: string): number | undefined {
  const trimmed = value.trim();
  if (trimmed === '') return undefined;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export default function CatalogPage() {
  const { user } = useAuth();
  const canEdit = user?.role === 'ADMIN';
  const canView = user?.role === 'ADMIN' || user?.role === 'MARKETING';

  const [products, setProducts] = useState<CatalogProduct[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [newSize, setNewSize] = useState<NewSizeDraft>(emptyNewSizeDraft);

  const [sizeDraft, setSizeDraft] = useState<SizeDraft>(emptySizeDraft);
  const [newStyle, setNewStyle] = useState<{ style: ProductStyle; label: string }>({ style: 'FULL', label: '' });

  const [packageFor, setPackageFor] = useState<number | null>(null);
  const [newPackage, setNewPackage] = useState({ quantity: '', price: '' });
  const [priceDrafts, setPriceDrafts] = useState<Record<number, string>>({});

  const selected = useMemo(
    () => products.find((product) => product.id === selectedId) ?? null,
    [products, selectedId]
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await catalogAPI.getProducts();
      const list: CatalogProduct[] = response.data;
      setProducts(list);
      setSelectedId((current) => current ?? list[0]?.id ?? null);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canView) {
      load();
    } else {
      setLoading(false);
    }
  }, [canView, load]);

  useEffect(() => {
    if (!selected) return;
    setSizeDraft({
      name: selected.name ?? '',
      displayName: selected.displayName ?? '',
      width: selected.width != null ? String(selected.width) : '',
      height: selected.height != null ? String(selected.height) : '',
      basePrice: selected.price != null ? String(selected.price) : '',
      tag: selected.tag ?? '',
      isActive: selected.active ?? true,
    });
    setPackageFor(null);
    setNewPackage({ quantity: '', price: '' });
    setPriceDrafts({});
  }, [selected]);

  const applyProduct = (product: CatalogProduct) => {
    setProducts((prev) => prev.map((item) => (item.id === product.id ? product : item)));
  };

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const handleCreateSize = () =>
    run(async () => {
      const code = newSize.code.trim().toLowerCase();
      if (!code || !newSize.name.trim() || !newSize.displayName.trim()) {
        throw new Error('Code, name, and display name are required');
      }
      const payload: Record<string, unknown> = {
        code,
        name: newSize.name.trim(),
        displayName: newSize.displayName.trim(),
      };
      const width = toNumberOrUndefined(newSize.width);
      const height = toNumberOrUndefined(newSize.height);
      const basePrice = toNumberOrUndefined(newSize.basePrice);
      if (width !== undefined) payload.width = width;
      if (height !== undefined) payload.height = height;
      if (basePrice !== undefined) payload.basePrice = basePrice;
      if (newSize.tag.trim()) payload.tag = newSize.tag.trim();

      const response = await catalogAPI.createProduct(payload);
      const created: CatalogProduct = response.data;
      setProducts((prev) => [...prev, created]);
      setSelectedId(created.id);
      setNewSize(emptyNewSizeDraft());
      setShowCreate(false);
      setNotice(`Added size "${created.displayName}".`);
    });

  const handleSaveSize = () =>
    run(async () => {
      if (!selected) return;
      const payload: Record<string, unknown> = {
        name: sizeDraft.name.trim(),
        displayName: sizeDraft.displayName.trim(),
        tag: sizeDraft.tag.trim(),
        isActive: sizeDraft.isActive,
      };
      const width = toNumberOrUndefined(sizeDraft.width);
      const height = toNumberOrUndefined(sizeDraft.height);
      const basePrice = toNumberOrUndefined(sizeDraft.basePrice);
      if (width !== undefined) payload.width = width;
      if (height !== undefined) payload.height = height;
      if (basePrice !== undefined) payload.basePrice = basePrice;
      const response = await catalogAPI.updateProduct(selected.id, payload);
      applyProduct(response.data);
      setNotice('Size details saved.');
    });

  const handleAddStyle = () =>
    run(async () => {
      if (!selected) return;
      const payload: Record<string, unknown> = { style: newStyle.style };
      if (newStyle.label.trim()) payload.label = newStyle.label.trim();
      const response = await catalogAPI.createVariant(selected.id, payload);
      applyProduct(response.data);
      setNewStyle({ style: 'FULL', label: '' });
      setNotice('Style added.');
    });

  const handleToggleStyle = (variantId: number, isActive: boolean) =>
    run(async () => {
      const response = await catalogAPI.updateVariant(variantId, { isActive });
      applyProduct(response.data);
    });

  const handleAddPackage = (variantId: number) =>
    run(async () => {
      const quantity = toNumberOrUndefined(newPackage.quantity);
      const price = toNumberOrUndefined(newPackage.price);
      if (quantity === undefined || price === undefined) {
        throw new Error('Package quantity and price are required');
      }
      const response = await catalogAPI.createPackage(variantId, { quantity, price });
      applyProduct(response.data);
      setPackageFor(null);
      setNewPackage({ quantity: '', price: '' });
      setNotice('Package added.');
    });

  const handleSavePackagePrice = (packageId: number) =>
    run(async () => {
      const price = toNumberOrUndefined(priceDrafts[packageId] ?? '');
      if (price === undefined) {
        throw new Error('Enter a valid price');
      }
      const response = await catalogAPI.updatePackage(packageId, { price });
      applyProduct(response.data);
      setNotice('Price updated.');
    });

  const handleTogglePackage = (packageId: number, isActive: boolean) =>
    run(async () => {
      const response = await catalogAPI.updatePackage(packageId, { isActive });
      applyProduct(response.data);
    });

  if (!canView) {
    return (
      <div className="rounded border border-border bg-surface p-8 text-center shadow-sm">
        <p className="text-sm text-text-muted">You do not have access to the catalog.</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="rounded border border-border bg-surface p-8 text-center shadow-sm">
        <span className="text-sm text-text-muted">Loading catalog...</span>
      </div>
    );
  }

  const inputClass =
    'w-full border border-border bg-bg px-3 py-2 text-sm text-text-primary font-mono placeholder:text-text-muted focus:border-accent focus:outline-none';
  const labelClass = 'text-xs text-text-muted uppercase tracking-[0.15em] mb-1 block';
  const buttonClass =
    'border border-border px-3 py-2 text-xs uppercase tracking-[0.15em] font-mono text-text-muted transition-colors duration-150 hover:border-accent hover:text-accent disabled:opacity-40';
  const primaryClass =
    'border border-accent bg-accent text-bg px-3 py-2 text-xs uppercase tracking-[0.15em] font-mono transition-colors duration-150 hover:opacity-90 disabled:opacity-40';

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-xl text-text-primary font-bold">Catalog</h1>
          <p className="text-sm text-text-muted mt-1">
            One product per size. Choose a size to manage its Full / Border styles and package prices.
          </p>
        </div>
        {canEdit && (
          <button className={primaryClass} onClick={() => setShowCreate((value) => !value)} disabled={busy}>
            {showCreate ? 'Cancel' : '+ New Size'}
          </button>
        )}
      </div>

      {error && (
        <div className="mb-4 border border-danger px-4 py-3 text-sm text-danger">{error}</div>
      )}
      {notice && (
        <div className="mb-4 border border-success px-4 py-3 text-sm text-success">{notice}</div>
      )}

      {canEdit && showCreate && (
        <div className="mb-6 rounded border border-border bg-surface p-6 shadow-sm">
          <h2 className="font-display text-base text-text-primary font-bold mb-4">New Size</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className={labelClass}>Code (slug)</label>
              <input
                className={inputClass}
                value={newSize.code}
                onChange={(event) => setNewSize({ ...newSize, code: event.target.value })}
                placeholder="e.g. 15r"
              />
            </div>
            <div>
              <label className={labelClass}>Name</label>
              <input
                className={inputClass}
                value={newSize.name}
                onChange={(event) => setNewSize({ ...newSize, name: event.target.value })}
                placeholder="e.g. 15R"
              />
            </div>
            <div>
              <label className={labelClass}>Display name</label>
              <input
                className={inputClass}
                value={newSize.displayName}
                onChange={(event) => setNewSize({ ...newSize, displayName: event.target.value })}
                placeholder="e.g. 15R (10 x 15 cm)"
              />
            </div>
            <div>
              <label className={labelClass}>Width (cm)</label>
              <input
                className={inputClass}
                value={newSize.width}
                onChange={(event) => setNewSize({ ...newSize, width: event.target.value })}
                placeholder="optional"
              />
            </div>
            <div>
              <label className={labelClass}>Height (cm)</label>
              <input
                className={inputClass}
                value={newSize.height}
                onChange={(event) => setNewSize({ ...newSize, height: event.target.value })}
                placeholder="optional"
              />
            </div>
            <div>
              <label className={labelClass}>Tag</label>
              <input
                className={inputClass}
                value={newSize.tag}
                onChange={(event) => setNewSize({ ...newSize, tag: event.target.value })}
                placeholder="optional"
              />
            </div>
          </div>
          <div className="mt-4">
            <button className={primaryClass} onClick={handleCreateSize} disabled={busy}>
              Create Size
            </button>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-6 items-start">
        <div className="rounded border border-border bg-surface shadow-sm overflow-hidden">
          <div className="border-b border-border px-4 py-3 text-xs uppercase tracking-[0.15em] text-text-muted font-mono">
            Sizes ({products.length})
          </div>
          <ul>
            {products.map((product) => {
              const packageCount = product.variants.reduce((total, variant) => total + variant.packages.length, 0);
              return (
                <li key={product.id}>
                  <button
                    onClick={() => setSelectedId(product.id)}
                    className={`w-full text-left px-4 py-3 border-b border-border transition-colors duration-150 ${
                      product.id === selectedId ? 'bg-surface-2 border-l-2 border-l-accent' : 'hover:bg-surface-2'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-display text-sm text-text-primary font-bold">{product.name}</span>
                      {!product.active && (
                        <span className="text-xs text-text-muted font-mono uppercase">retired</span>
                      )}
                    </div>
                    <div className="text-xs text-text-muted mt-1 font-mono">
                      {product.code} · {product.variants.length} style{product.variants.length === 1 ? '' : 's'} ·{' '}
                      {packageCount} pkg
                    </div>
                  </button>
                </li>
              );
            })}
            {products.length === 0 && (
              <li className="px-4 py-6 text-sm text-text-muted">No sizes yet.</li>
            )}
          </ul>
        </div>

        {selected ? (
          <div className="rounded border border-border bg-surface p-6 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
              <div>
                <h2 className="font-display text-lg text-text-primary font-bold">{selected.displayName}</h2>
                <p className="text-xs text-text-muted font-mono mt-1">
                  id {selected.id} · code {selected.code}
                </p>
              </div>
              <span
                className={`border px-2 py-0.5 text-xs font-mono uppercase tracking-[0.1em] ${
                  selected.active ? 'border-success text-success' : 'border-border text-text-muted'
                }`}
              >
                {selected.active ? 'active' : 'retired'}
              </span>
            </div>

            <div className="border border-border bg-bg p-4 mb-6">
              <h3 className="text-xs uppercase tracking-[0.15em] text-text-muted font-mono mb-3">Size details</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div>
                  <label className={labelClass}>Name</label>
                  <input
                    className={inputClass}
                    value={sizeDraft.name}
                    disabled={!canEdit}
                    onChange={(event) => setSizeDraft({ ...sizeDraft, name: event.target.value })}
                  />
                </div>
                <div>
                  <label className={labelClass}>Display name</label>
                  <input
                    className={inputClass}
                    value={sizeDraft.displayName}
                    disabled={!canEdit}
                    onChange={(event) => setSizeDraft({ ...sizeDraft, displayName: event.target.value })}
                  />
                </div>
                <div>
                  <label className={labelClass}>Tag</label>
                  <input
                    className={inputClass}
                    value={sizeDraft.tag}
                    disabled={!canEdit}
                    onChange={(event) => setSizeDraft({ ...sizeDraft, tag: event.target.value })}
                  />
                </div>
                <div>
                  <label className={labelClass}>Width (cm)</label>
                  <input
                    className={inputClass}
                    value={sizeDraft.width}
                    disabled={!canEdit}
                    onChange={(event) => setSizeDraft({ ...sizeDraft, width: event.target.value })}
                  />
                </div>
                <div>
                  <label className={labelClass}>Height (cm)</label>
                  <input
                    className={inputClass}
                    value={sizeDraft.height}
                    disabled={!canEdit}
                    onChange={(event) => setSizeDraft({ ...sizeDraft, height: event.target.value })}
                  />
                </div>
                <div>
                  <label className={labelClass}>Base price (legacy, RM)</label>
                  <input
                    className={inputClass}
                    value={sizeDraft.basePrice}
                    disabled={!canEdit}
                    onChange={(event) => setSizeDraft({ ...sizeDraft, basePrice: event.target.value })}
                  />
                </div>
              </div>
              {canEdit && (
                <div className="mt-4 flex items-center gap-3">
                  <button className={primaryClass} onClick={handleSaveSize} disabled={busy}>
                    Save Size
                  </button>
                  <label className="flex items-center gap-2 text-xs text-text-muted font-mono uppercase">
                    <input
                      type="checkbox"
                      checked={sizeDraft.isActive}
                      onChange={(event) => setSizeDraft({ ...sizeDraft, isActive: event.target.checked })}
                    />
                    Active
                  </label>
                </div>
              )}
            </div>

            <h3 className="text-xs uppercase tracking-[0.15em] text-text-muted font-mono mb-3">Styles & Packages</h3>
            <div className="space-y-4">
              {selected.variants.map((variant) => (
                <div key={variant.id} className="border border-border bg-bg p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
                    <div className="flex items-center gap-3">
                      <span className="font-display text-sm text-text-primary font-bold">{variant.label}</span>
                      <span className="border border-border px-2 py-0.5 text-xs font-mono text-text-muted uppercase">
                        {variant.style}
                      </span>
                      {!variant.active && (
                        <span className="text-xs text-text-muted font-mono uppercase">hidden</span>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      {canEdit && (
                        <>
                          <button
                            className={buttonClass}
                            disabled={busy}
                            onClick={() => setPackageFor(packageFor === variant.id ? null : variant.id)}
                          >
                            + Package
                          </button>
                          <button
                            className={buttonClass}
                            disabled={busy}
                            onClick={() => handleToggleStyle(variant.id, !variant.active)}
                          >
                            {variant.active ? 'Hide' : 'Show'}
                          </button>
                        </>
                      )}
                    </div>
                  </div>

                  {variant.packages.length > 0 ? (
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="text-xs text-text-muted uppercase tracking-[0.1em] font-mono">
                            <th className="text-left py-2 pr-4">Prints</th>
                            <th className="text-left py-2 pr-4">Price (RM)</th>
                            <th className="text-left py-2 pr-4">Status</th>
                            {canEdit && <th className="text-left py-2">Actions</th>}
                          </tr>
                        </thead>
                        <tbody>
                          {variant.packages.map((pkg) => (
                            <tr key={pkg.id} className="border-t border-border">
                              <td className="py-2 pr-4 font-mono">{pkg.quantity}</td>
                              <td className="py-2 pr-4">
                                {canEdit ? (
                                  <input
                                    className={`${inputClass} max-w-[140px]`}
                                    value={priceDrafts[pkg.id] ?? pkg.price}
                                    onChange={(event) =>
                                      setPriceDrafts({ ...priceDrafts, [pkg.id]: event.target.value })
                                    }
                                  />
                                ) : (
                                  <span className="font-mono">{pkg.price}</span>
                                )}
                              </td>
                              <td className="py-2 pr-4">
                                <span
                                  className={`text-xs font-mono uppercase ${
                                    pkg.active ? 'text-success' : 'text-text-muted'
                                  }`}
                                >
                                  {pkg.active ? 'active' : 'hidden'}
                                </span>
                              </td>
                              {canEdit && (
                                <td className="py-2">
                                  <div className="flex items-center gap-2">
                                    <button
                                      className={buttonClass}
                                      disabled={busy}
                                      onClick={() => handleSavePackagePrice(pkg.id)}
                                    >
                                      Save
                                    </button>
                                    <button
                                      className={buttonClass}
                                      disabled={busy}
                                      onClick={() => handleTogglePackage(pkg.id, !pkg.active)}
                                    >
                                      {pkg.active ? 'Hide' : 'Show'}
                                    </button>
                                  </div>
                                </td>
                              )}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <p className="text-sm text-text-muted">No packages yet.</p>
                  )}

                  {canEdit && packageFor === variant.id && (
                    <div className="mt-4 flex flex-wrap items-end gap-3 border-t border-border pt-4">
                      <div>
                        <label className={labelClass}>Prints per package</label>
                        <input
                          className={`${inputClass} max-w-[140px]`}
                          value={newPackage.quantity}
                          onChange={(event) => setNewPackage({ ...newPackage, quantity: event.target.value })}
                          placeholder="e.g. 15"
                        />
                      </div>
                      <div>
                        <label className={labelClass}>Package price (RM)</label>
                        <input
                          className={`${inputClass} max-w-[140px]`}
                          value={newPackage.price}
                          onChange={(event) => setNewPackage({ ...newPackage, price: event.target.value })}
                          placeholder="e.g. 9.00"
                        />
                      </div>
                      <button className={primaryClass} disabled={busy} onClick={() => handleAddPackage(variant.id)}>
                        Add Package
                      </button>
                    </div>
                  )}
                </div>
              ))}

              {selected.variants.length === 0 && (
                <p className="text-sm text-text-muted">No styles yet. Add Full or Border below.</p>
              )}
            </div>

            {canEdit && (
              <div className="mt-6 border border-border bg-bg p-4">
                <h3 className="text-xs uppercase tracking-[0.15em] text-text-muted font-mono mb-3">Add style</h3>
                <div className="flex flex-wrap items-end gap-3">
                  <div>
                    <label className={labelClass}>Style</label>
                    <select
                      className={`${inputClass} max-w-[180px]`}
                      value={newStyle.style}
                      onChange={(event) =>
                        setNewStyle({ ...newStyle, style: event.target.value as ProductStyle })
                      }
                    >
                      <option value="FULL">Full (no border)</option>
                      <option value="BORDER">Border (Polaroid)</option>
                    </select>
                  </div>
                  <div>
                    <label className={labelClass}>Label (optional)</label>
                    <input
                      className={`${inputClass} max-w-[220px]`}
                      value={newStyle.label}
                      onChange={(event) => setNewStyle({ ...newStyle, label: event.target.value })}
                      placeholder="e.g. Border (Polaroid)"
                    />
                  </div>
                  <button className={primaryClass} disabled={busy} onClick={handleAddStyle}>
                    Add Style
                  </button>
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="rounded border border-border bg-surface p-8 text-center shadow-sm">
            <p className="text-sm text-text-muted">Select a size to manage its styles and prices.</p>
          </div>
        )}
      </div>
    </div>
  );
}
