'use client';

import { useRef, useState } from 'react';
import { catalogAPI } from '@/lib/api';
import { CatalogProduct } from '@/types';

export default function ProductImages({ product, canEdit, onChange }: {
  product: CatalogProduct;
  canEdit: boolean;
  onChange: (product: CatalogProduct) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);
  const images = product.images ?? [];
  const button = 'border border-border px-3 py-2 text-xs font-mono text-text-primary hover:border-accent disabled:opacity-50 disabled:cursor-not-allowed';

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError('');
    setNotice('');
    try { await action(); }
    catch (err) {
      const failure = err as { response?: { data?: { message?: string } }; message?: string };
      setError(failure.response?.data?.message || failure.message || 'Could not update product images');
    } finally { setBusy(false); }
  };

  const update = (next: string[], message: string) => run(async () => {
    const response = await catalogAPI.updateImages(product.id, next);
    onChange(response.data);
    setNotice(message);
  });

  return (
    <section className="mb-6 border border-border bg-bg p-4" aria-label="Product images">
      <h3 className="font-display text-sm font-bold text-text-primary">Product images</h3>
      <p className="mt-1 mb-4 text-xs text-text-muted">The cover image appears first in the product gallery and on catalog cards. Changes save immediately.</p>
      {error && <p role="alert" className="mb-3 text-sm text-red-400">{error}</p>}
      {notice && <p role="status" className="mb-3 text-sm text-text-muted">{notice}</p>}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3" aria-busy={busy}>
        {images.map((image, index) => (
          <div key={image} className="border border-border bg-surface p-2">
            <img src={image} alt={`${product.displayName} image ${index + 1}`} className="h-36 w-full object-contain" />
            <div className="mt-2 min-h-6 text-xs font-mono text-text-muted">{index === 0 ? 'Cover image' : `Image ${index + 1}`}</div>
            {canEdit && <div className="mt-2 flex flex-wrap gap-2">
              {index > 0 && <button type="button" className={button} disabled={busy}
                onClick={() => update([image, ...images.filter((_, i) => i !== index)], 'Cover image updated.')}>
                Set as cover
              </button>}
              <button type="button" className={button} disabled={busy}
                aria-label={`Remove image ${index + 1}`}
                onClick={() => {
                  if (window.confirm(index === 0 && images.length > 1
                    ? 'Remove this cover image? The next image will become the cover.'
                    : 'Remove this image from the product?')) {
                    update(images.filter((_, i) => i !== index), 'Image removed from product.');
                  }
                }}>Remove</button>
            </div>}
          </div>
        ))}
      </div>
      {images.length === 0 && <p className="py-5 text-sm text-text-muted">No images yet. Add an image to use it as the cover.</p>}
      {canEdit && <div className="mt-4 space-y-3">
        <p className="text-xs text-text-muted">{images.length}/12 images · JPEG, PNG or WebP · Up to 10 MB and 20 megapixels</p>
        <label className="block text-sm text-text-primary">
          Upload image
          <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy || images.length >= 12}
            className="mt-2 block w-full text-xs text-text-muted disabled:opacity-50"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (!file) return;
              void run(async () => {
                try {
                  if (file.size > 10 * 1024 * 1024) throw new Error('Choose an image up to 10 MB');
                  const response = await catalogAPI.uploadImage(product.id, file);
                  onChange(response.data);
                  setNotice('Image uploaded. Use Set as cover to show it first.');
                } finally { if (fileInput.current) fileInput.current.value = ''; }
              });
            }} />
        </label>
        <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
          event.preventDefault();
          void run(async () => {
            const next = url.trim();
            if (!next.startsWith('https://')) throw new Error('Enter an HTTPS image URL');
            const response = await catalogAPI.updateImages(product.id, [...images, next]);
            onChange(response.data);
            setUrl('');
            setNotice('Image added.');
          });
        }}>
          <label className="flex-1 min-w-[180px] text-xs text-text-muted">
            Or add an image URL
            <input type="url" value={url} onChange={(event) => setUrl(event.target.value)} required
              disabled={busy || images.length >= 12} placeholder="https://..."
              className="mt-1 w-full border border-border bg-bg px-3 py-2 text-sm text-text-primary" />
          </label>
          <button className={button} disabled={busy || images.length >= 12 || !url.trim()} type="submit">Add image</button>
        </form>
        {busy && <p role="status" className="text-xs text-text-muted">Saving images…</p>}
      </div>}
    </section>
  );
}
