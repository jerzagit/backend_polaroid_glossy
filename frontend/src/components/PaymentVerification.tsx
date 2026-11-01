'use client';

import { useEffect, useRef, useState } from 'react';
import { isAxiosError } from 'axios';
import { Order } from '@/types';
import { orderAPI } from '@/lib/api';

type Review = { proofKey: string | null; proofUrl: string | null; history: Record<string, string | null>[] };
const button = 'border border-border px-4 py-3 font-mono text-xs uppercase tracking-[0.1em] hover:border-accent disabled:opacity-40';
const input = 'mt-2 w-full border border-border bg-transparent p-3 text-text-primary focus:outline-accent';
const errorMessage = (error: unknown) => isAxiosError(error) && typeof error.response?.data?.message === 'string'
  ? error.response.data.message : 'Unable to complete this action. Please refresh and try again.';

export default function PaymentVerification({ order, isAdmin, onUpdated, onStartProcessing, updating }: {
  order: Order; isAdmin: boolean; onUpdated: (order: Order) => void; onStartProcessing: () => void; updating: boolean;
}) {
  const [review, setReview] = useState<Review | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [file, setFile] = useState<File | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const [reference, setReference] = useState(order.paymentReference || '');
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [action, setAction] = useState<'APPROVE' | 'REJECT' | null>(null);
  const [reviewedProof, setReviewedProof] = useState('');
  const dialog = useRef<HTMLDialogElement>(null);
  const active = order.status === 'PENDING' || (order.status as string) === 'DRAFT';
  const unpaid = order.paymentStatus !== 'PAID' && (order.paymentStatus as string) !== 'REFUNDED';
  const canReview = isAdmin && active && unpaid;

  useEffect(() => {
    if (!isAdmin) return;
    let cancelled = false;
    orderAPI.paymentReview(order.id).then(({ data }) => { if (!cancelled) setReview(data); })
      .catch((e) => { if (!cancelled) setError(errorMessage(e)); });
    return () => { cancelled = true; };
  }, [order.id, order.updatedAt, order.paymentProofUrl, isAdmin]);

  useEffect(() => {
    if (action && !dialog.current?.open) dialog.current?.showModal();
    if (!action && dialog.current?.open) dialog.current.close();
  }, [action]);

  const refresh = async () => {
    const [orderResponse, reviewResponse] = await Promise.all([orderAPI.getById(order.id), orderAPI.paymentReview(order.id)]);
    onUpdated(orderResponse.data);
    setReview(reviewResponse.data);
  };
  const upload = async () => {
    if (!file || saving.current) return;
    saving.current = true; setBusy(true); setError('');
    try {
      await orderAPI.uploadPaymentProof(order.id, file, reference);
      setFile(null);
      if (fileInput.current) fileInput.current.value = '';
      await refresh();
    } catch (e) { setError(errorMessage(e)); }
    finally { saving.current = false; setBusy(false); }
  };
  const openReview = (next: 'APPROVE' | 'REJECT') => {
    if (!review?.proofKey) return;
    setReviewedProof(review.proofKey); setAmount(''); setNotes(''); setConfirmed(false);
    setReference(order.paymentReference || ''); setError(''); setAction(next);
  };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!action || saving.current) return;
    saving.current = true; setBusy(true); setError('');
    try {
      const { data } = await orderAPI.verifyPayment(order.orderNumber, {
        action, proofKey: reviewedProof,
        ...(action === 'APPROVE' ? { amountReceived: Number(amount), reference, notes, fundsConfirmed: confirmed } : { reason: notes }),
      });
      onUpdated(data); setAction(null);
      await refresh();
    } catch (e) { setError(errorMessage(e)); }
    finally { saving.current = false; setBusy(false); }
  };

  return <section aria-label="Payment verification" className="border-t border-border pt-5 space-y-4 font-mono text-sm">
    <h3 className="text-xs uppercase tracking-[0.15em] text-text-muted">Payment verification</h3>
    <div className="flex flex-wrap gap-3 text-xs">
      <span className="border border-border px-3 py-2">Payment: {order.paymentStatus === 'PAID' ? 'Paid' : order.paymentReviewStatus === 'AWAITING_VERIFICATION' ? 'Awaiting verification' : order.paymentReviewStatus === 'REJECTED' ? 'Proof rejected' : 'Awaiting payment'}</span>
      <span className="border border-border px-3 py-2">Photos: {order.photosComplete ? 'Complete' : 'Incomplete'}</span>
    </div>
    {order.paymentProofSource && <p className="text-text-muted">Proof source: {order.paymentProofSource === 'WHATSAPP' ? 'WhatsApp' : 'System upload'}</p>}
    {order.paymentReference && <p className="text-text-muted break-words">Reference: {order.paymentReference}</p>}
    {order.paymentVerifiedAt && <p className="text-text-muted">Verified by {order.paymentVerifiedBy} on {new Date(order.paymentVerifiedAt).toLocaleString()}{order.paymentAmountReceived != null && ` · RM ${order.paymentAmountReceived.toFixed(2)} received`}</p>}
    {isAdmin && review?.proofUrl && <a href={review.proofUrl} target="_blank" rel="noreferrer" className="block w-fit">
      <img src={review.proofUrl} alt="Customer payment receipt" className="max-h-56 max-w-full border border-border object-contain" />
      <span className="mt-2 block text-xs text-accent underline">Open receipt</span>
    </a>}
    {isAdmin && <button type="button" className={button} disabled={busy || updating} onClick={() => { setError(''); refresh().catch(e => setError(errorMessage(e))); }}>Refresh proof</button>}
    {canReview && <>
      <div className="border border-border p-4 space-y-3">
        <p className="text-text-muted">Attach the receipt received through WhatsApp. Uploading proof does not mark the order paid.</p>
        <label className="block">Transaction reference
          <input maxLength={200} value={reference} disabled={busy} onChange={e => setReference(e.target.value)} className={input} />
        </label>
        <label className="block">Receipt image
          <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={e => setFile(e.target.files?.[0] || null)} className="mt-2 block w-full text-xs" />
        </label>
        <button type="button" className={button} disabled={!file || busy || updating} onClick={upload}>{busy ? 'Please wait...' : 'Upload WhatsApp proof'}</button>
      </div>
      {review?.proofKey && order.paymentReviewStatus !== 'REJECTED' && <div className="flex flex-wrap gap-3">
        <button type="button" className={button + ' border-accent text-accent'} disabled={busy || updating} onClick={() => openReview('APPROVE')}>Verify payment</button>
        <button type="button" className={button} disabled={busy || updating} onClick={() => openReview('REJECT')}>Reject proof</button>
      </div>}
    </>}
    {error && !action && <p role="alert" className="text-danger">{error}</p>}
    {active && order.paymentStatus === 'PAID' && !order.photosComplete && <p className="text-text-muted">Payment confirmed. Waiting for all required photos before processing.</p>}
    {active && order.paymentStatus === 'PAID' && order.photosComplete && <button type="button" className={button + ' border-accent text-accent'} disabled={busy || updating} onClick={onStartProcessing}>Start processing</button>}
    {isAdmin && !!review?.history.length && <details className="border border-border p-4">
      <summary className="cursor-pointer">Payment review history</summary>
      <ol className="mt-4 space-y-4">{review.history.map((entry, index) => <li key={index} className="border-t border-border pt-3 text-xs space-y-1 break-words">
        <p>{entry.action?.replace(/_/g, ' ')} · {entry.at && new Date(entry.at).toLocaleString()}</p>
        <p className="text-text-muted">{entry.actor} · {entry.source?.replace(/_/g, ' ')}</p>
        {entry.reference && <p>Reference: {entry.reference}</p>}{entry.amount && <p>Received: RM {entry.amount}</p>}
        {entry.notes && <p>{entry.notes}</p>}
        {entry.proofUrl && <a href={entry.proofUrl} target="_blank" rel="noreferrer" className="text-accent underline">View receipt</a>}
      </li>)}</ol>
    </details>}
    <dialog ref={dialog} aria-labelledby="payment-review-title" onCancel={e => { e.preventDefault(); if (!saving.current) setAction(null); }}
      className="m-auto w-[calc(100%-2rem)] max-w-lg border border-border p-0 text-text-primary backdrop:bg-black/70 backdrop:backdrop-blur-sm" style={{ borderRadius: 0, background: 'var(--color-surface)' }}>
      <form onSubmit={submit} className="p-6 space-y-4 font-mono text-sm">
        <h2 id="payment-review-title" className="font-display text-xl">{action === 'APPROVE' ? 'Verify payment received' : 'Reject payment proof'}</h2>
        <p className="text-text-muted">{order.orderNumber} · Total due RM {order.total.toFixed(2)} (including delivery)</p>
        {action === 'APPROVE' && <>
          <label className="block">Amount received (RM)<input required type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} disabled={busy} className={input} /></label>
          <label className="block">Transaction reference<input required maxLength={200} value={reference} onChange={e => setReference(e.target.value)} disabled={busy} className={input} /></label>
        </>}
        <label className="block">{action === 'REJECT' ? 'Rejection reason' : 'Notes (optional)'}<textarea required={action === 'REJECT'} maxLength={1000} value={notes} onChange={e => setNotes(e.target.value)} disabled={busy} className={input} /></label>
        {action === 'APPROVE' && <label className="flex items-start gap-3"><input required type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} disabled={busy} className="mt-1 accent-accent" /><span>I have checked the payment and confirmed funds were received.</span></label>}
        {error && <p role="alert" className="text-danger">{error}</p>}
        <div className="flex flex-wrap justify-end gap-3">
          <button type="button" autoFocus disabled={busy} className={button} onClick={() => setAction(null)}>Cancel</button>
          <button type="submit" disabled={busy || (action === 'APPROVE' && !confirmed)} className={button + ' border-accent text-accent'}>{busy ? 'Saving...' : action === 'APPROVE' ? 'Confirm payment' : 'Reject proof'}</button>
        </div>
      </form>
    </dialog>
  </section>;
}
