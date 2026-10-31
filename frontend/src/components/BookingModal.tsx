import React, { useState, useEffect } from 'react'
import {
  X,
  Plus,
  Minus,
  Clock,
  ShieldCheck,
  CreditCard,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  RefreshCw,
  Printer,
  Sparkles,
} from 'lucide-react'
import type { Event, BookingResponse, BookingConfirmationResponse } from '../types'
import { createBooking, confirmBooking, ApiRequestError } from '../api/client'
import { TicketPass } from './TicketPass'
import { calculateCountdown } from '../utils/date'

interface BookingModalProps {
  event: Event
  defaultUserId: number
  onClose: () => void
  onBookingSuccess?: (confirmation: BookingConfirmationResponse) => void
  existingPendingBooking?: BookingResponse | null
  onClearPendingBooking?: () => void
}

type ModalStep = 'select' | 'reserved' | 'confirmed'

export const BookingModal: React.FC<BookingModalProps> = ({
  event,
  defaultUserId,
  onClose,
  onBookingSuccess,
  existingPendingBooking,
  onClearPendingBooking,
}) => {
  // Step state
  const [step, setStep] = useState<ModalStep>(existingPendingBooking ? 'reserved' : 'select')

  // Form input state
  const [quantity, setQuantity] = useState<number>(1)
  const [userId, setUserId] = useState<number>(defaultUserId)

  // Booking results
  const [pendingBooking, setPendingBooking] = useState<BookingResponse | null>(
    existingPendingBooking || null
  )
  const [confirmedBooking, setConfirmedBooking] = useState<BookingConfirmationResponse | null>(null)

  // In-flight loading & error state
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [errorDetails, setErrorDetails] = useState<string | null>(null)

  // Expiry countdown timer (for PENDING_PAYMENT step)
  const [timeLeftStr, setTimeLeftStr] = useState<string>('10:00')
  const [isExpired, setIsExpired] = useState<boolean>(false)

  // Update countdown timer whenever pendingBooking exists
  useEffect(() => {
    if (step !== 'reserved' || !pendingBooking) return

    const updateCountdown = () => {
      const countdown = calculateCountdown(pendingBooking.expiresAt)
      setTimeLeftStr(countdown.timeLeftStr)
      setIsExpired(countdown.isExpired)
    }

    updateCountdown()
    const interval = setInterval(updateCountdown, 1000)
    return () => clearInterval(interval)
  }, [step, pendingBooking])

  // Total amount calculation
  const totalAmount = (Number(event.ticketPrice) * quantity).toFixed(2)

  // Step 1: Create reservation
  const handleCreateReservation = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)
    setErrorDetails(null)

    if (quantity < 1 || quantity > 6) {
      setErrorMessage('Quantity must be between 1 and 6 tickets per booking.')
      return
    }

    if (!userId || userId < 1) {
      setErrorMessage('A valid positive User ID is required.')
      return
    }

    setIsSubmitting(true)
    try {
      const response = await createBooking({
        userId,
        eventId: event.id,
        quantity,
      })
      setPendingBooking(response)
      setStep('reserved')
    } catch (err: unknown) {
      handleApiError(err)
    } finally {
      setIsSubmitting(false)
    }
  }

  // Step 2: Confirm reservation & issue tickets
  const handleConfirmBooking = async () => {
    if (!pendingBooking) return
    setErrorMessage(null)
    setErrorDetails(null)

    setIsSubmitting(true)
    try {
      const confirmation = await confirmBooking(pendingBooking.bookingId)
      setConfirmedBooking(confirmation)
      setStep('confirmed')
      if (onBookingSuccess) {
        onBookingSuccess(confirmation)
      }
      if (onClearPendingBooking) {
        onClearPendingBooking()
      }
    } catch (err: unknown) {
      handleApiError(err)
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleApiError = (err: unknown) => {
    if (err instanceof ApiRequestError) {
      setErrorMessage(err.message)
      if (err.isCorsError) {
        setErrorDetails(
          'Browser Cross-Origin (CORS) check failed. If running locally, ensure Vite dev proxy is active or backend CORS is enabled.'
        )
      } else if (err.status === 409) {
        setErrorDetails(
          'Booking conflict: Payment deadline may have passed or booking status changed.'
        )
      } else if (err.status === 400) {
        setErrorDetails('Request could not be processed due to validation or inventory constraints.')
      }
    } else if (err instanceof Error) {
      setErrorMessage(err.message)
    } else {
      setErrorMessage('An unexpected error occurred. Please try again.')
    }
  }

  const handlePrintTickets = () => {
    window.print()
  }

  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-content booking-modal-root" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="modal-top-bar">
          <div className="modal-step-indicator">
            <span className={`step-dot ${step === 'select' ? 'active' : 'done'}`}>1</span>
            <span className="step-line" />
            <span className={`step-dot ${step === 'reserved' ? 'active' : step === 'confirmed' ? 'done' : ''}`}>2</span>
            <span className="step-line" />
            <span className={`step-dot ${step === 'confirmed' ? 'active done' : ''}`}>3</span>
          </div>

          <button type="button" className="close-btn" onClick={onClose} aria-label="Close dialog">
            <X size={20} />
          </button>
        </div>

        {/* Error Notification Banner */}
        {errorMessage && (
          <div className="error-banner">
            <AlertTriangle size={18} className="error-icon" />
            <div className="error-text-group">
              <span className="error-title">{errorMessage}</span>
              {errorDetails && <span className="error-sub">{errorDetails}</span>}
            </div>
            <button
              type="button"
              className="error-dismiss-btn"
              onClick={() => {
                setErrorMessage(null)
                setErrorDetails(null)
              }}
            >
              <X size={14} />
            </button>
          </div>
        )}

        {/* ── STEP 1: SELECT QUANTITY ────────────────────────────────────── */}
        {step === 'select' && (
          <div className="step-pane">
            <div className="event-summary-box">
              <span className="badge badge-gold">
                <Sparkles size={11} /> Selected Event
              </span>
              <h2 className="summary-title">{event.name}</h2>
              <p className="summary-venue">{event.venue}</p>
              <div className="summary-price-row">
                <span className="summary-price-label">Price per ticket:</span>
                <span className="summary-unit-price">${Number(event.ticketPrice).toFixed(2)}</span>
              </div>
            </div>

            <form onSubmit={handleCreateReservation} className="booking-form">
              {/* Quantity Picker */}
              <div className="form-group">
                <div className="label-row">
                  <label htmlFor="quantityStepper" className="field-label">
                    Ticket Quantity
                  </label>
                  <span className="quantity-limit-tag">Max 6 per booking</span>
                </div>

                <div className="stepper-wrapper">
                  <button
                    type="button"
                    className="stepper-btn"
                    onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                    disabled={quantity <= 1 || isSubmitting}
                    aria-label="Decrease quantity"
                  >
                    <Minus size={18} />
                  </button>

                  <div className="stepper-display">
                    <span className="quantity-number">{quantity}</span>
                    <span className="quantity-unit">{quantity === 1 ? 'ticket' : 'tickets'}</span>
                  </div>

                  <button
                    type="button"
                    className="stepper-btn"
                    onClick={() => setQuantity((q) => Math.min(6, q + 1))}
                    disabled={quantity >= 6 || isSubmitting}
                    aria-label="Increase quantity"
                  >
                    <Plus size={18} />
                  </button>
                </div>

                {/* Quick chip selector */}
                <div className="chip-selector-row">
                  {[1, 2, 3, 4, 5, 6].map((num) => (
                    <button
                      key={num}
                      type="button"
                      className={`num-chip ${quantity === num ? 'selected' : ''}`}
                      onClick={() => setQuantity(num)}
                      disabled={isSubmitting}
                    >
                      {num}
                    </button>
                  ))}
                </div>
              </div>

              {/* Demo User Selection */}
              <div className="form-group">
                <div className="label-row">
                  <label htmlFor="userIdField" className="field-label">
                    Booking User ID
                  </label>
                  <span className="user-hint">Pre-seeded demo user is ID: 1</span>
                </div>
                <input
                  id="userIdField"
                  type="number"
                  min="1"
                  step="1"
                  value={userId}
                  onChange={(e) => setUserId(parseInt(e.target.value, 10) || 1)}
                  className="form-input-styled"
                  disabled={isSubmitting}
                  required
                />
              </div>

              {/* Price Breakdown */}
              <div className="price-breakdown-card">
                <div className="breakdown-line">
                  <span>${Number(event.ticketPrice).toFixed(2)} × {quantity} {quantity === 1 ? 'seat' : 'seats'}</span>
                  <span>${totalAmount}</span>
                </div>
                <div className="breakdown-line">
                  <span>Booking &amp; Service Fee</span>
                  <span className="text-success">FREE (Demo)</span>
                </div>
                <div className="breakdown-total">
                  <span>Total Amount</span>
                  <span className="total-number">${totalAmount}</span>
                </div>
              </div>

              {/* Concurrency guarantee note */}
              <div className="concurrency-note">
                <ShieldCheck size={16} className="note-icon" />
                <span>
                  Clicking below acquires a database row lock on event inventory. Seats are reserved for 10 minutes.
                </span>
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                className="btn btn-primary submit-reserve-btn"
                disabled={isSubmitting}
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw size={18} className="spin-icon" />
                    <span>Locking Inventory &amp; Reserving...</span>
                  </>
                ) : (
                  <>
                    <span>Hold Seats &amp; Proceed</span>
                    <ArrowRight size={18} />
                  </>
                )}
              </button>
            </form>
          </div>
        )}

        {/* ── STEP 2: PENDING PAYMENT (RESERVED) ─────────────────────────── */}
        {step === 'reserved' && pendingBooking && (
          <div className="step-pane">
            <div className="reservation-header">
              <div className="timer-badge-box">
                <Clock size={16} className={isExpired ? 'text-danger' : 'text-gold'} />
                <span className="timer-label">Payment Window:</span>
                <span className={`timer-digits ${isExpired ? 'expired' : ''}`}>
                  {timeLeftStr}
                </span>
              </div>

              <span className="badge badge-gold">
                STATUS: {pendingBooking.status}
              </span>
            </div>

            <div className="reserved-details-card">
              <h3 className="reserved-title">{pendingBooking.eventName}</h3>
              <div className="reserved-grid">
                <div className="grid-cell">
                  <span className="cell-label">Reservation ID</span>
                  <span className="cell-val">#{pendingBooking.bookingId}</span>
                </div>
                <div className="grid-cell">
                  <span className="cell-label">Quantity</span>
                  <span className="cell-val">{pendingBooking.quantity} Ticket(s)</span>
                </div>
                <div className="grid-cell">
                  <span className="cell-label">Locked Total</span>
                  <span className="cell-val total-color">${Number(pendingBooking.totalAmount).toFixed(2)}</span>
                </div>
                <div className="grid-cell">
                  <span className="cell-label">Reserved For</span>
                  <span className="cell-val">User #{pendingBooking.userId}</span>
                </div>
              </div>
            </div>

            <div className="simulation-notice">
              <CreditCard size={18} className="notice-icon" />
              <div className="notice-text">
                <strong>Simulated Payment Confirmation</strong>
                <p>
                  No real payment gateway is integrated. Clicking below sends a confirmation signal
                  to <code>POST /bookings/{pendingBooking.bookingId}/confirm</code> to issue real tickets.
                </p>
              </div>
            </div>

            {isExpired ? (
              <div className="expired-callout">
                <AlertTriangle size={18} />
                <span>
                  This reservation deadline has passed. The background scheduler returns these seats to inventory.
                </span>
              </div>
            ) : null}

            <div className="modal-actions-row">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={onClose}
                disabled={isSubmitting}
              >
                Cancel &amp; Exit
              </button>

              <button
                type="button"
                className="btn btn-primary confirm-pay-btn"
                onClick={handleConfirmBooking}
                disabled={isSubmitting || isExpired}
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw size={18} className="spin-icon" />
                    <span>Verifying &amp; Issuing Tickets...</span>
                  </>
                ) : (
                  <>
                    <CheckCircle2 size={18} />
                    <span>Confirm Booking &amp; Issue Tickets</span>
                  </>
                )}
              </button>
            </div>
          </div>
        )}

        {/* ── STEP 3: CONFIRMED TICKETS ─────────────────────────────────── */}
        {step === 'confirmed' && confirmedBooking && (
          <div className="step-pane">
            <div className="confirmed-hero-banner">
              <div className="celebration-icon">
                <CheckCircle2 size={36} className="text-success" />
              </div>
              <h2 className="confirmed-title">Booking Confirmed!</h2>
              <p className="confirmed-sub">
                Your reservation #{confirmedBooking.bookingId} is confirmed and{' '}
                {confirmedBooking.tickets.length} official ticket pass(es) have been issued.
              </p>
            </div>

            {/* Issued Passes */}
            <div className="ticket-passes-list">
              {confirmedBooking.tickets.map((t) => (
                <TicketPass
                  key={t.ticketId}
                  ticket={t}
                  eventName={confirmedBooking.eventName}
                  venue={event.venue}
                  eventDate={event.eventDate}
                  bookingId={confirmedBooking.bookingId}
                />
              ))}
            </div>

            <div className="confirmed-actions">
              <button
                type="button"
                className="btn btn-secondary print-btn"
                onClick={handlePrintTickets}
              >
                <Printer size={16} /> Print Passes
              </button>

              <button
                type="button"
                className="btn btn-primary done-btn"
                onClick={onClose}
              >
                <span>Return to Event Discovery</span>
                <ArrowRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>

      <style>{`
        .booking-modal-root {
          padding: 1.75rem 2rem 2rem;
        }

        .modal-top-bar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 1.5rem;
        }

        .modal-step-indicator {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .step-dot {
          width: 26px;
          height: 26px;
          border-radius: 50%;
          background: rgba(255, 255, 255, 0.08);
          border: 1px solid var(--border-subtle);
          color: var(--text-muted);
          font-size: 0.8rem;
          font-weight: 700;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .step-dot.active {
          background: var(--accent-gold);
          border-color: var(--accent-gold);
          color: #0b0d13;
          box-shadow: 0 0 12px var(--accent-gold-glow);
        }

        .step-dot.done {
          background: var(--status-success);
          border-color: var(--status-success);
          color: #0b0d13;
        }

        .step-line {
          width: 24px;
          height: 2px;
          background: var(--border-subtle);
        }

        .close-btn {
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid var(--border-subtle);
          border-radius: 50%;
          width: 32px;
          height: 32px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--text-secondary);
          transition: all var(--transition-fast);
        }

        .close-btn:hover {
          background: rgba(255, 255, 255, 0.12);
          color: var(--text-primary);
        }

        .error-banner {
          display: flex;
          align-items: flex-start;
          gap: 0.75rem;
          background: var(--status-danger-bg);
          border: 1px solid rgba(239, 68, 68, 0.3);
          border-radius: var(--radius-md);
          padding: 0.85rem 1rem;
          margin-bottom: 1.25rem;
          color: #fca5a5;
        }

        .error-icon {
          color: var(--status-danger);
          flex-shrink: 0;
          margin-top: 2px;
        }

        .error-text-group {
          flex: 1;
          display: flex;
          flex-direction: column;
          font-size: 0.85rem;
        }

        .error-title {
          font-weight: 700;
        }

        .error-sub {
          font-size: 0.775rem;
          margin-top: 0.2rem;
          opacity: 0.9;
        }

        .error-dismiss-btn {
          background: transparent;
          color: #fca5a5;
          opacity: 0.7;
        }

        .error-dismiss-btn:hover {
          opacity: 1;
        }

        .event-summary-box {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-md);
          padding: 1.1rem 1.25rem;
          margin-bottom: 1.4rem;
        }

        .summary-title {
          font-size: 1.25rem;
          margin: 0.5rem 0 0.25rem;
        }

        .summary-venue {
          font-size: 0.875rem;
          color: var(--text-secondary);
          margin-bottom: 0.65rem;
        }

        .summary-price-row {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 0.85rem;
        }

        .summary-price-label {
          color: var(--text-muted);
        }

        .summary-unit-price {
          font-weight: 700;
          color: var(--accent-gold);
        }

        .form-group {
          margin-bottom: 1.35rem;
        }

        .label-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 0.5rem;
        }

        .field-label {
          font-size: 0.85rem;
          font-weight: 700;
          color: var(--text-secondary);
        }

        .quantity-limit-tag, .user-hint {
          font-size: 0.725rem;
          color: var(--text-muted);
        }

        .stepper-wrapper {
          display: flex;
          align-items: center;
          justify-content: space-between;
          background: rgba(0, 0, 0, 0.4);
          border: 1px solid var(--border-medium);
          border-radius: var(--radius-md);
          padding: 0.35rem;
          margin-bottom: 0.65rem;
        }

        .stepper-btn {
          width: 44px;
          height: 44px;
          background: rgba(255, 255, 255, 0.05);
          border-radius: var(--radius-sm);
          color: var(--text-primary);
          display: flex;
          align-items: center;
          justify-content: center;
          transition: all var(--transition-fast);
        }

        .stepper-btn:hover:not(:disabled) {
          background: rgba(255, 255, 255, 0.12);
        }

        .stepper-display {
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .quantity-number {
          font-family: var(--font-display);
          font-size: 1.5rem;
          font-weight: 800;
          line-height: 1;
          color: var(--text-primary);
        }

        .quantity-unit {
          font-size: 0.725rem;
          color: var(--text-muted);
        }

        .chip-selector-row {
          display: flex;
          gap: 0.45rem;
        }

        .num-chip {
          flex: 1;
          padding: 0.4rem 0;
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-sm);
          color: var(--text-secondary);
          font-size: 0.85rem;
          font-weight: 700;
          transition: all var(--transition-fast);
        }

        .num-chip:hover:not(:disabled) {
          background: rgba(255, 255, 255, 0.08);
          color: var(--text-primary);
        }

        .num-chip.selected {
          background: var(--accent-gold);
          color: #0b0d13;
          border-color: var(--accent-gold);
        }

        .form-input-styled {
          width: 100%;
          padding: 0.75rem 1rem;
          border-radius: var(--radius-md);
          background: rgba(0, 0, 0, 0.4);
          border: 1px solid var(--border-medium);
          color: var(--text-primary);
          font-size: 0.95rem;
          outline: none;
        }

        .form-input-styled:focus {
          border-color: var(--accent-gold);
        }

        .price-breakdown-card {
          background: rgba(0, 0, 0, 0.3);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-md);
          padding: 1rem 1.25rem;
          margin-bottom: 1rem;
        }

        .breakdown-line {
          display: flex;
          justify-content: space-between;
          font-size: 0.85rem;
          color: var(--text-secondary);
          margin-bottom: 0.4rem;
        }

        .breakdown-total {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding-top: 0.65rem;
          border-top: 1px solid var(--border-subtle);
          font-size: 0.95rem;
          font-weight: 700;
          color: var(--text-primary);
        }

        .total-number {
          font-family: var(--font-display);
          font-size: 1.35rem;
          font-weight: 800;
          color: var(--accent-gold);
        }

        .concurrency-note {
          display: flex;
          align-items: flex-start;
          gap: 0.5rem;
          font-size: 0.775rem;
          color: var(--text-muted);
          margin-bottom: 1.4rem;
          line-height: 1.4;
        }

        .note-icon {
          color: var(--accent-gold);
          flex-shrink: 0;
          margin-top: 1px;
        }

        .submit-reserve-btn {
          width: 100%;
          padding: 0.85rem;
          font-size: 1rem;
        }

        /* Step 2 styling */
        .reservation-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 1.25rem;
          flex-wrap: wrap;
          gap: 0.75rem;
        }

        .timer-badge-box {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          background: rgba(0, 0, 0, 0.4);
          border: 1px solid var(--border-medium);
          padding: 0.4rem 0.85rem;
          border-radius: var(--radius-full);
          font-size: 0.85rem;
        }

        .timer-label {
          color: var(--text-muted);
        }

        .timer-digits {
          font-family: monospace;
          font-size: 1.05rem;
          font-weight: 800;
          color: var(--accent-gold);
        }

        .timer-digits.expired {
          color: var(--status-danger);
        }

        .reserved-details-card {
          background: rgba(255, 255, 255, 0.03);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-md);
          padding: 1.25rem;
          margin-bottom: 1.25rem;
        }

        .reserved-title {
          font-size: 1.15rem;
          margin-bottom: 0.85rem;
        }

        .reserved-grid {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 0.85rem;
        }

        .grid-cell {
          display: flex;
          flex-direction: column;
        }

        .cell-label {
          font-size: 0.7rem;
          color: var(--text-muted);
          text-transform: uppercase;
          font-weight: 600;
        }

        .cell-val {
          font-size: 0.95rem;
          font-weight: 700;
          color: var(--text-primary);
        }

        .total-color {
          color: var(--accent-gold);
          font-size: 1.1rem;
        }

        .simulation-notice {
          display: flex;
          gap: 0.75rem;
          background: rgba(99, 102, 241, 0.1);
          border: 1px solid rgba(99, 102, 241, 0.25);
          border-radius: var(--radius-md);
          padding: 0.85rem 1rem;
          margin-bottom: 1.5rem;
          font-size: 0.825rem;
          color: #c7d2fe;
          line-height: 1.45;
        }

        .notice-icon {
          color: #a5b4fc;
          flex-shrink: 0;
          margin-top: 2px;
        }

        .notice-text strong {
          display: block;
          margin-bottom: 0.2rem;
          color: #e0e7ff;
        }

        .expired-callout {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          background: var(--status-danger-bg);
          border: 1px solid rgba(239, 68, 68, 0.3);
          border-radius: var(--radius-md);
          padding: 0.75rem 1rem;
          color: #fca5a5;
          font-size: 0.825rem;
          margin-bottom: 1.25rem;
        }

        .modal-actions-row {
          display: flex;
          gap: 0.85rem;
          justify-content: flex-end;
        }

        .confirm-pay-btn {
          flex: 1;
        }

        /* Step 3 styling */
        .confirmed-hero-banner {
          text-align: center;
          margin-bottom: 1.75rem;
        }

        .celebration-icon {
          width: 58px;
          height: 58px;
          background: var(--status-success-bg);
          border: 1px solid rgba(16, 185, 129, 0.3);
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          margin: 0 auto 0.75rem;
          box-shadow: 0 0 25px -5px rgba(16, 185, 129, 0.4);
        }

        .confirmed-title {
          font-size: 1.6rem;
          margin-bottom: 0.4rem;
        }

        .confirmed-sub {
          font-size: 0.875rem;
          color: var(--text-secondary);
          max-width: 440px;
          margin: 0 auto;
        }

        .ticket-passes-list {
          max-height: 45vh;
          overflow-y: auto;
          margin-bottom: 1.5rem;
          padding-right: 0.35rem;
        }

        .confirmed-actions {
          display: flex;
          gap: 0.85rem;
        }

        .print-btn {
          flex: 0 0 auto;
        }

        .done-btn {
          flex: 1;
        }

        @media (max-width: 640px) {
          .booking-modal-root {
            padding: 1.25rem;
          }
          .modal-actions-row {
            flex-direction: column;
          }
          .confirmed-actions {
            flex-direction: column;
          }
        }
      `}</style>
    </div>
  )
}
