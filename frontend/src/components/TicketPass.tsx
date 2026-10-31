import React, { useState } from 'react'
import { Ticket as TicketIcon, Calendar, MapPin, Copy, Check, QrCode } from 'lucide-react'
import type { Ticket } from '../types'

interface TicketPassProps {
  ticket: Ticket
  eventName: string
  venue?: string
  eventDate?: string
  bookingId: number
}

export const TicketPass: React.FC<TicketPassProps> = ({
  ticket,
  eventName,
  venue,
  eventDate,
  bookingId,
}) => {
  const [copied, setCopied] = useState(false)

  const handleCopyTicketNumber = () => {
    navigator.clipboard.writeText(ticket.ticketNumber)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const formatIssuedTime = (isoStr: string) => {
    try {
      const d = new Date(isoStr)
      return d.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoStr
    }
  }

  return (
    <div className="ticket-pass-root">
      {/* Top Section */}
      <div className="ticket-top">
        <div className="ticket-badge-row">
          <span className="badge badge-gold">
            <TicketIcon size={12} /> Official Admission Pass
          </span>
          <span className="ticket-seq-pill">Pass #{ticket.ticketNumber.split('-').pop()}</span>
        </div>

        <h3 className="ticket-event-name">{eventName}</h3>

        <div className="ticket-meta-grid">
          {eventDate && (
            <div className="meta-box">
              <span className="meta-label">Date &amp; Time</span>
              <span className="meta-val">
                <Calendar size={13} className="text-gold" />
                {new Date(eventDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
              </span>
            </div>
          )}

          {venue && (
            <div className="meta-box">
              <span className="meta-label">Venue</span>
              <span className="meta-val" title={venue}>
                <MapPin size={13} className="text-gold" />
                {venue}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Perforation Divider with Cutout Notches */}
      <div className="ticket-divider">
        <div className="notch notch-left" />
        <div className="dashed-line" />
        <div className="notch notch-right" />
      </div>

      {/* Bottom Section (Stub / Barcode) */}
      <div className="ticket-bottom">
        <div className="ticket-data-row">
          <div className="data-col">
            <span className="stub-label">Ticket Reference</span>
            <div className="ticket-num-row">
              <span className="ticket-number-val">{ticket.ticketNumber}</span>
              <button
                type="button"
                className="copy-btn"
                onClick={handleCopyTicketNumber}
                title="Copy ticket number"
              >
                {copied ? <Check size={14} className="text-success" /> : <Copy size={14} />}
              </button>
            </div>
          </div>

          <div className="data-col right-align">
            <span className="stub-label">Booking ID</span>
            <span className="booking-ref-val">#{bookingId}</span>
          </div>
        </div>

        <div className="uuid-row">
          <span className="uuid-label">UUID:</span>
          <code className="uuid-text" title={ticket.ticketId}>
            {ticket.ticketId}
          </code>
        </div>

        {/* Stylized Barcode */}
        <div className="barcode-container" title="Digital verification barcode">
          <div className="barcode-lines">
            {[4, 2, 6, 1, 3, 5, 2, 4, 1, 6, 3, 2, 5, 1, 4, 2, 6, 3, 1, 5, 2, 4, 3, 6, 1, 2, 5, 3].map(
              (w, idx) => (
                <span
                  key={idx}
                  style={{
                    width: `${w}px`,
                    height: '28px',
                    backgroundColor: 'rgba(255, 255, 255, 0.75)',
                    display: 'inline-block',
                    marginRight: '2px',
                  }}
                />
              )
            )}
          </div>
          <div className="barcode-footer">
            <span>ISSUED: {formatIssuedTime(ticket.issuedAt)}</span>
            <span className="flex-align">
              <QrCode size={13} /> DIGITAL PASS
            </span>
          </div>
        </div>
      </div>

      <style>{`
        .ticket-pass-root {
          background: #141824;
          border: 1px solid var(--border-medium);
          border-radius: var(--radius-lg);
          overflow: hidden;
          box-shadow: 0 12px 35px -8px rgba(0, 0, 0, 0.7);
          margin-bottom: 1.25rem;
          position: relative;
        }

        .ticket-top {
          padding: 1.4rem 1.5rem 1.1rem;
          background: linear-gradient(180deg, #181d2e 0%, #141824 100%);
        }

        .ticket-badge-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 0.75rem;
        }

        .ticket-seq-pill {
          font-size: 0.75rem;
          font-weight: 700;
          color: var(--text-muted);
          background: rgba(255, 255, 255, 0.05);
          padding: 0.2rem 0.55rem;
          border-radius: var(--radius-sm);
        }

        .ticket-event-name {
          font-size: 1.2rem;
          font-weight: 800;
          line-height: 1.3;
          margin-bottom: 1rem;
          color: var(--text-primary);
        }

        .ticket-meta-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
          gap: 0.85rem;
        }

        .meta-box {
          display: flex;
          flex-direction: column;
          gap: 0.2rem;
        }

        .meta-label {
          font-size: 0.7rem;
          color: var(--text-muted);
          text-transform: uppercase;
          font-weight: 600;
          letter-spacing: 0.04em;
        }

        .meta-val {
          font-size: 0.85rem;
          font-weight: 600;
          color: var(--text-secondary);
          display: flex;
          align-items: center;
          gap: 0.35rem;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .text-gold {
          color: var(--accent-gold);
        }

        .text-success {
          color: var(--status-success);
        }

        .ticket-divider {
          position: relative;
          height: 24px;
          display: flex;
          align-items: center;
          background: transparent;
        }

        .dashed-line {
          width: 100%;
          border-bottom: 2px dashed rgba(255, 255, 255, 0.15);
        }

        .notch {
          position: absolute;
          width: 22px;
          height: 22px;
          background-color: var(--bg-card); /* matches modal backdrop */
          border-radius: 50%;
          border: 1px solid var(--border-medium);
          top: 1px;
          z-index: 2;
        }

        .notch-left {
          left: -11px;
        }

        .notch-right {
          right: -11px;
        }

        .ticket-bottom {
          padding: 1rem 1.5rem 1.4rem;
          background: #10131d;
        }

        .ticket-data-row {
          display: flex;
          align-items: flex-end;
          justify-content: space-between;
          margin-bottom: 0.75rem;
        }

        .data-col {
          display: flex;
          flex-direction: column;
        }

        .right-align {
          text-align: right;
        }

        .stub-label {
          font-size: 0.675rem;
          text-transform: uppercase;
          color: var(--text-muted);
          font-weight: 600;
          margin-bottom: 0.2rem;
          letter-spacing: 0.05em;
        }

        .ticket-num-row {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .ticket-number-val {
          font-family: var(--font-display);
          font-size: 1.25rem;
          font-weight: 800;
          letter-spacing: 0.05em;
          color: var(--accent-gold);
        }

        .booking-ref-val {
          font-family: var(--font-display);
          font-size: 1.1rem;
          font-weight: 700;
          color: var(--text-primary);
        }

        .copy-btn {
          background: rgba(255, 255, 255, 0.06);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-sm);
          width: 26px;
          height: 26px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--text-secondary);
          transition: all var(--transition-fast);
        }

        .copy-btn:hover {
          background: rgba(255, 255, 255, 0.12);
          color: var(--text-primary);
        }

        .uuid-row {
          display: flex;
          align-items: center;
          gap: 0.4rem;
          font-size: 0.725rem;
          color: var(--text-muted);
          margin-bottom: 1rem;
        }

        .uuid-label {
          font-weight: 600;
        }

        .uuid-text {
          font-family: monospace;
          background: rgba(0, 0, 0, 0.35);
          padding: 0.15rem 0.45rem;
          border-radius: 4px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          color: var(--text-secondary);
        }

        .barcode-container {
          background: rgba(0, 0, 0, 0.4);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-sm);
          padding: 0.65rem 0.85rem;
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.45rem;
        }

        .barcode-lines {
          display: flex;
          align-items: center;
          justify-content: center;
          height: 28px;
          overflow: hidden;
        }

        .barcode-footer {
          width: 100%;
          display: flex;
          justify-content: space-between;
          font-size: 0.65rem;
          color: var(--text-muted);
          font-family: monospace;
          font-weight: 600;
        }

        .flex-align {
          display: flex;
          align-items: center;
          gap: 0.25rem;
        }
      `}</style>
    </div>
  )
}
