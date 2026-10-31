import React, { useState, useEffect } from 'react'
import { Ticket, Sparkles, User, CheckCircle2, AlertCircle, RefreshCw } from 'lucide-react'
import { checkApiHealth, API_BASE_URL } from '../api/client'

interface NavbarProps {
  userId: number
  onUserIdChange: (newId: number) => void
  activeReservationCount?: number
  onOpenActiveBooking?: () => void
}

export const Navbar: React.FC<NavbarProps> = ({
  userId,
  onUserIdChange,
  activeReservationCount = 0,
  onOpenActiveBooking,
}) => {
  const [apiStatus, setApiStatus] = useState<'checking' | 'online' | 'offline'>('checking')
  const [showUserModal, setShowUserModal] = useState(false)
  const [tempUserId, setTempUserId] = useState(userId.toString())

  useEffect(() => {
    let mounted = true
    const verifyApi = async () => {
      try {
        await checkApiHealth()
        if (mounted) setApiStatus('online')
      } catch {
        if (mounted) setApiStatus('offline')
      }
    }
    verifyApi()
    const interval = setInterval(verifyApi, 45000)
    return () => {
      mounted = false
      clearInterval(interval)
    }
  }, [])

  const handleSaveUserId = (e: React.FormEvent) => {
    e.preventDefault()
    const parsed = parseInt(tempUserId, 10)
    if (!isNaN(parsed) && parsed > 0) {
      onUserIdChange(parsed)
      setShowUserModal(false)
    }
  }

  return (
    <>
      <header className="navbar-root">
        <div className="container navbar-inner">
          {/* Logo & Brand */}
          <div className="navbar-brand">
            <div className="logo-icon-box">
              <Ticket className="logo-icon" size={24} />
            </div>
            <div className="brand-text-group">
              <span className="brand-name">
                Tix-n-Trix
                <Sparkles size={14} className="brand-sparkle" />
              </span>
              <span className="brand-tagline">High-Concurrency Booking Platform</span>
            </div>
          </div>

          {/* Right controls */}
          <div className="navbar-actions">
            {/* API Status Pill */}
            <div
              className={`status-pill ${
                apiStatus === 'online' ? 'status-online' : apiStatus === 'offline' ? 'status-offline' : 'status-checking'
              }`}
              title={`Backend: ${API_BASE_URL || 'Local / Proxied'}`}
            >
              {apiStatus === 'online' ? (
                <>
                  <span className="live-dot" />
                  <span className="status-label">API Connected</span>
                </>
              ) : apiStatus === 'offline' ? (
                <>
                  <AlertCircle size={13} className="text-danger" />
                  <span className="status-label">API Unreachable</span>
                </>
              ) : (
                <>
                  <RefreshCw size={12} className="spin-icon" />
                  <span className="status-label">Connecting...</span>
                </>
              )}
            </div>

            {/* Active reservation indicator */}
            {activeReservationCount > 0 && onOpenActiveBooking && (
              <button
                type="button"
                className="active-reservation-btn"
                onClick={onOpenActiveBooking}
                title="View your pending reservation"
              >
                <span className="live-dot" style={{ backgroundColor: 'var(--accent-gold)' }} />
                <span>1 Pending Reservation</span>
              </button>
            )}

            {/* Demo User Pill */}
            <button
              type="button"
              className="user-pill-btn"
              onClick={() => setShowUserModal(true)}
              title="Click to configure Demo User ID"
            >
              <div className="user-avatar">
                <User size={14} />
              </div>
              <div className="user-info">
                <span className="user-title">User #{userId}</span>
                <span className="user-sub">Demo Account</span>
              </div>
            </button>
          </div>
        </div>
      </header>

      {/* User ID Dialog */}
      {showUserModal && (
        <div className="modal-backdrop" onClick={() => setShowUserModal(false)}>
          <div className="modal-content user-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Configure Demo User</h3>
              <p className="modal-desc">
                The backend requires a valid User ID to create bookings. The database is pre-seeded with Demo User (ID: 1).
              </p>
            </div>
            <form onSubmit={handleSaveUserId} className="user-form">
              <label htmlFor="userIdInput" className="form-label">
                User ID
              </label>
              <input
                id="userIdInput"
                type="number"
                min="1"
                step="1"
                value={tempUserId}
                onChange={(e) => setTempUserId(e.target.value)}
                className="form-input"
                required
              />
              <div className="form-actions">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setShowUserModal(false)}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  <CheckCircle2 size={16} /> Save User ID
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <style>{`
        .navbar-root {
          position: sticky;
          top: 0;
          z-index: 50;
          background: rgba(10, 12, 18, 0.85);
          backdrop-filter: blur(16px);
          border-bottom: 1px solid var(--border-subtle);
          padding: 0.9rem 0;
        }

        .navbar-inner {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 1rem;
        }

        .navbar-brand {
          display: flex;
          align-items: center;
          gap: 0.75rem;
        }

        .logo-icon-box {
          width: 42px;
          height: 42px;
          background: linear-gradient(135deg, rgba(245, 158, 11, 0.2) 0%, rgba(99, 102, 241, 0.2) 100%);
          border: 1px solid var(--border-accent);
          border-radius: var(--radius-md);
          display: flex;
          align-items: center;
          justify-content: center;
          color: var(--accent-gold);
          box-shadow: 0 0 20px -3px var(--accent-gold-glow);
        }

        .brand-text-group {
          display: flex;
          flex-direction: column;
        }

        .brand-name {
          font-family: var(--font-display);
          font-size: 1.35rem;
          font-weight: 800;
          letter-spacing: -0.02em;
          color: var(--text-primary);
          display: flex;
          align-items: center;
          gap: 0.25rem;
        }

        .brand-sparkle {
          color: var(--accent-gold);
        }

        .brand-tagline {
          font-size: 0.75rem;
          color: var(--text-muted);
          font-weight: 500;
        }

        .navbar-actions {
          display: flex;
          align-items: center;
          gap: 0.85rem;
        }

        .status-pill {
          display: flex;
          align-items: center;
          gap: 0.45rem;
          padding: 0.35rem 0.75rem;
          border-radius: var(--radius-full);
          font-size: 0.775rem;
          font-weight: 600;
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid var(--border-subtle);
        }

        .status-online {
          color: var(--status-success);
          border-color: rgba(16, 185, 129, 0.25);
        }

        .status-offline {
          color: var(--status-danger);
          border-color: rgba(239, 68, 68, 0.25);
        }

        .status-checking {
          color: var(--text-secondary);
        }

        .spin-icon {
          animation: spin 1s linear infinite;
        }

        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }

        .active-reservation-btn {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.4rem 0.85rem;
          border-radius: var(--radius-full);
          background: rgba(245, 158, 11, 0.12);
          border: 1px solid rgba(245, 158, 11, 0.35);
          color: var(--accent-gold);
          font-size: 0.8rem;
          font-weight: 600;
          transition: all var(--transition-fast);
        }

        .active-reservation-btn:hover {
          background: rgba(245, 158, 11, 0.2);
          transform: translateY(-1px);
        }

        .user-pill-btn {
          display: flex;
          align-items: center;
          gap: 0.6rem;
          padding: 0.35rem 0.85rem 0.35rem 0.45rem;
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-full);
          color: var(--text-primary);
          transition: all var(--transition-fast);
        }

        .user-pill-btn:hover {
          background: rgba(255, 255, 255, 0.08);
          border-color: var(--border-medium);
        }

        .user-avatar {
          width: 26px;
          height: 26px;
          border-radius: 50%;
          background: var(--bg-elevated);
          color: #a5b4fc;
          display: flex;
          align-items: center;
          justify-content: center;
        }

        .user-info {
          display: flex;
          flex-direction: column;
          text-align: left;
          line-height: 1.1;
        }

        .user-title {
          font-size: 0.775rem;
          font-weight: 700;
        }

        .user-sub {
          font-size: 0.675rem;
          color: var(--text-muted);
        }

        .user-modal-content {
          padding: 1.75rem;
        }

        .modal-header h3 {
          margin-bottom: 0.4rem;
        }

        .modal-desc {
          font-size: 0.85rem;
          color: var(--text-secondary);
          margin-bottom: 1.25rem;
        }

        .form-label {
          display: block;
          font-size: 0.825rem;
          font-weight: 600;
          color: var(--text-secondary);
          margin-bottom: 0.4rem;
        }

        .form-input {
          width: 100%;
          padding: 0.75rem 1rem;
          border-radius: var(--radius-md);
          background: rgba(0, 0, 0, 0.3);
          border: 1px solid var(--border-medium);
          color: var(--text-primary);
          font-size: 1rem;
          outline: none;
          margin-bottom: 1.5rem;
          transition: border-color var(--transition-fast);
        }

        .form-input:focus {
          border-color: var(--accent-gold);
          box-shadow: 0 0 0 2px var(--accent-gold-glow);
        }

        .form-actions {
          display: flex;
          justify-content: flex-end;
          gap: 0.75rem;
        }

        @media (max-width: 640px) {
          .brand-tagline {
            display: none;
          }
          .status-label {
            display: none;
          }
          .user-sub {
            display: none;
          }
        }
      `}</style>
    </>
  )
}
