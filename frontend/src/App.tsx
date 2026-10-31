import { useState, useEffect, useMemo } from 'react'
import {
  Sparkles,
  RefreshCw,
  AlertCircle,
  HelpCircle,
  Layers,
  Database,
  Server,
  Lock,
} from 'lucide-react'
import type { Event, BookingResponse, BookingConfirmationResponse } from './types'
import { fetchEvents, ApiRequestError, API_BASE_URL } from './api/client'
import { Navbar } from './components/Navbar'
import { Hero } from './components/Hero'
import { EventCard } from './components/EventCard'
import { BookingModal } from './components/BookingModal'
import { TicketPass } from './components/TicketPass'

export function App() {
  const [events, setEvents] = useState<Event[]>([])
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isCorsIssue, setIsCorsIssue] = useState<boolean>(false)

  // Search and filter state
  const [searchQuery, setSearchQuery] = useState<string>('')
  const [selectedCategory, setSelectedCategory] = useState<string>('All Events')

  // User configuration
  const [userId, setUserId] = useState<number>(() => {
    const envUser = import.meta.env.VITE_DEMO_USER_ID
    return envUser ? parseInt(envUser, 10) || 1 : 1
  })

  // Selected event for modal
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null)

  // Active in-progress reservation state
  const [activePendingBooking, setActivePendingBooking] = useState<BookingResponse | null>(null)

  // Confirmed orders list
  const [confirmedOrders, setConfirmedOrders] = useState<BookingConfirmationResponse[]>([])

  const categories = ['All Events', 'Stadium Tours', 'Concerts', 'Festivals']

  // Load events from backend
  const loadEvents = async () => {
    setIsLoading(true)
    setErrorMessage(null)
    setIsCorsIssue(false)

    try {
      const data = await fetchEvents()
      setEvents(data)
    } catch (err: unknown) {
      if (err instanceof ApiRequestError) {
        setErrorMessage(err.message)
        setIsCorsIssue(Boolean(err.isCorsError))
      } else if (err instanceof Error) {
        setErrorMessage(err.message)
      } else {
        setErrorMessage('Failed to connect to backend service.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    loadEvents()
  }, [])

  // Filter events based on search query and category
  const filteredEvents = useMemo(() => {
    return events.filter((ev) => {
      const q = searchQuery.toLowerCase().trim()
      const matchesSearch =
        !q ||
        ev.name.toLowerCase().includes(q) ||
        ev.venue.toLowerCase().includes(q) ||
        (ev.description && ev.description.toLowerCase().includes(q))

      if (!matchesSearch) return false

      if (selectedCategory === 'All Events') return true
      if (selectedCategory === 'Stadium Tours') {
        return (
          ev.venue.toLowerCase().includes('stadium') ||
          ev.name.toLowerCase().includes('tour') ||
          ev.name.toLowerCase().includes('eras')
        )
      }
      if (selectedCategory === 'Concerts') {
        return (
          ev.name.toLowerCase().includes('concert') ||
          ev.name.toLowerCase().includes('sheeran') ||
          ev.name.toLowerCase().includes('coldplay')
        )
      }
      if (selectedCategory === 'Festivals') {
        return (
          ev.venue.toLowerCase().includes('grounds') ||
          ev.name.toLowerCase().includes('live')
        )
      }

      return true
    })
  }, [events, searchQuery, selectedCategory])

  const handleOpenBooking = (event: Event) => {
    setSelectedEvent(event)
  }

  const handleBookingSuccess = (confirmation: BookingConfirmationResponse) => {
    setConfirmedOrders((prev) => [confirmation, ...prev])
    setActivePendingBooking(null)
  }

  const handleOpenActiveReservation = () => {
    if (!activePendingBooking) return
    const matchingEvent = events.find((e) => e.id === activePendingBooking.eventId)
    if (matchingEvent) {
      setSelectedEvent(matchingEvent)
    }
  }

  return (
    <div className="app-layout">
      {/* Navbar */}
      <Navbar
        userId={userId}
        onUserIdChange={setUserId}
        activeReservationCount={activePendingBooking ? 1 : 0}
        onOpenActiveBooking={handleOpenActiveReservation}
      />

      {/* Hero Header */}
      <Hero
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        selectedCategory={selectedCategory}
        onCategorySelect={setSelectedCategory}
        categories={categories}
      />

      {/* Main Content Area */}
      <main className="container main-content">
        {/* Section Header */}
        <div className="section-header">
          <div className="section-title-box">
            <h2 className="section-title">
              Featured Events
              <span className="event-count-badge">
                {isLoading ? '...' : filteredEvents.length}
              </span>
            </h2>
            <p className="section-sub">
              Browse live tours and select seats directly with database-level inventory safety.
            </p>
          </div>

          <div className="section-actions">
            <button
              type="button"
              className="btn btn-secondary reload-btn"
              onClick={loadEvents}
              disabled={isLoading}
              title="Refresh events from server"
            >
              <RefreshCw size={15} className={isLoading ? 'spin-icon' : ''} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {/* Loading State: Skeletons */}
        {isLoading && (
          <div className="events-grid">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="event-card-skeleton">
                <div className="skeleton-visual-header skeleton" />
                <div className="skeleton-body">
                  <div className="skeleton skeleton-title" />
                  <div className="skeleton skeleton-meta" />
                  <div className="skeleton skeleton-meta" />
                  <div className="skeleton skeleton-desc" />
                  <div className="skeleton skeleton-footer" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error State with Diagnostic */}
        {!isLoading && errorMessage && (
          <div className="error-state-card">
            <div className="error-icon-circle">
              <AlertCircle size={32} />
            </div>
            <h3 className="error-heading">Unable to Load Events</h3>
            <p className="error-message-text">{errorMessage}</p>

            {isCorsIssue && (
              <div className="cors-help-box">
                <div className="cors-help-title">
                  <HelpCircle size={15} />
                  <span>CORS Cross-Origin Policy Notice</span>
                </div>
                <p>
                  The backend at <code>{API_BASE_URL}</code> did not include an{' '}
                  <code>Access-Control-Allow-Origin</code> header in its response.
                </p>
                <ul>
                  <li>
                    <strong>Local Development:</strong> Vite dev proxy is configured in{' '}
                    <code>vite.config.ts</code>. If you clear <code>VITE_API_BASE_URL</code>, requests
                    route through the local dev server without CORS restrictions.
                  </li>
                  <li>
                    <strong>Production Fix:</strong> Add <code>@CrossOrigin</code> or a{' '}
                    <code>WebMvcConfigurer</code> CORS bean on the Spring Boot backend.
                  </li>
                </ul>
              </div>
            )}

            <button type="button" className="btn btn-primary retry-btn" onClick={loadEvents}>
              <RefreshCw size={16} />
              <span>Retry Connection</span>
            </button>
          </div>
        )}

        {/* Empty Search Results */}
        {!isLoading && !errorMessage && filteredEvents.length === 0 && (
          <div className="empty-state-card">
            <Layers size={36} className="empty-icon" />
            <h3>No Events Found</h3>
            <p>We couldn't find any events matching "{searchQuery}".</p>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setSearchQuery('')
                setSelectedCategory('All Events')
              }}
            >
              Clear Filters
            </button>
          </div>
        )}

        {/* Events Grid */}
        {!isLoading && !errorMessage && filteredEvents.length > 0 && (
          <div className="events-grid">
            {filteredEvents.map((event) => (
              <EventCard
                key={event.id}
                event={event}
                onSelect={handleOpenBooking}
              />
            ))}
          </div>
        )}

        {/* Confirmed Passes Section */}
        {confirmedOrders.length > 0 && (
          <section className="confirmed-orders-section">
            <div className="section-header">
              <div className="section-title-box">
                <h2 className="section-title">
                  Your Issued Passes
                  <span className="event-count-badge">
                    {confirmedOrders.reduce((sum, o) => sum + o.tickets.length, 0)}
                  </span>
                </h2>
                <p className="section-sub">
                  Official ticket passes confirmed and recorded in PostgreSQL during this session.
                </p>
              </div>
            </div>
            <div className="orders-grid">
              {confirmedOrders.map((order) => (
                <div key={order.bookingId} className="order-group">
                  {order.tickets.map((t) => (
                    <TicketPass
                      key={t.ticketId}
                      ticket={t}
                      eventName={order.eventName}
                      bookingId={order.bookingId}
                    />
                  ))}
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Architecture & Verification Panel */}
        <section className="architecture-panel">
          <div className="panel-header">
            <div className="panel-title-group">
              <span className="badge badge-indigo">
                <Sparkles size={11} /> System Architecture
              </span>
              <h3>High-Concurrency Engineering Guarantees</h3>
            </div>
            <span className="backend-tag">Spring Boot 4.1.1 + Neon PostgreSQL</span>
          </div>

          <div className="architecture-grid">
            <div className="arch-card">
              <div className="arch-icon-box">
                <Lock size={18} />
              </div>
              <h4>Pessimistic Inventory Locking</h4>
              <p>
                Every booking executes <code>SELECT ... FOR UPDATE</code> on the event inventory row,
                preventing concurrent overselling even under heavy traffic spikes.
              </p>
            </div>

            <div className="arch-card">
              <div className="arch-icon-box">
                <Database size={18} />
              </div>
              <h4>Database Check Constraints</h4>
              <p>
                PostgreSQL enforces <code>reserved_count + sold_count &lt;= total_capacity</code> at the
                engine level, ensuring data integrity is never violated.
              </p>
            </div>

            <div className="arch-card">
              <div className="arch-icon-box">
                <Server size={18} />
              </div>
              <h4>30s Background Expiry Engine</h4>
              <p>
                An automated scheduler releases unpaid reservations back to the open ticket pool every 30 seconds
                using transactional row locks.
              </p>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="footer-root">
        <div className="container footer-inner">
          <div className="footer-left">
            <span className="footer-brand">Tix-n-Trix</span>
            <span className="footer-copy">
              © 2026 High-Concurrency Ticket Booking Platform. Built with Java 21, Spring Boot, and React.
            </span>
          </div>
          <div className="footer-right">
            <span className="footer-status-pill">
              Backend API: <code>{API_BASE_URL || 'Local / Proxied'}</code>
            </span>
          </div>
        </div>
      </footer>

      {/* Booking Modal */}
      {selectedEvent && (
        <BookingModal
          event={selectedEvent}
          defaultUserId={userId}
          onClose={() => setSelectedEvent(null)}
          onBookingSuccess={handleBookingSuccess}
          existingPendingBooking={
            activePendingBooking?.eventId === selectedEvent.id ? activePendingBooking : null
          }
          onClearPendingBooking={() => setActivePendingBooking(null)}
        />
      )}

      <style>{`
        .app-layout {
          min-height: 100vh;
          display: flex;
          flex-direction: column;
        }

        .main-content {
          flex: 1;
          padding-bottom: 4rem;
        }

        .section-header {
          display: flex;
          align-items: flex-end;
          justify-content: space-between;
          margin-bottom: 1.75rem;
          gap: 1rem;
          flex-wrap: wrap;
        }

        .section-title {
          font-size: 1.85rem;
          display: flex;
          align-items: center;
          gap: 0.65rem;
        }

        .event-count-badge {
          font-size: 0.8rem;
          font-weight: 700;
          color: var(--accent-gold);
          background: rgba(245, 158, 11, 0.12);
          border: 1px solid rgba(245, 158, 11, 0.3);
          border-radius: var(--radius-full);
          padding: 0.15rem 0.6rem;
        }

        .section-sub {
          font-size: 0.9rem;
          color: var(--text-secondary);
          margin-top: 0.25rem;
        }

        .reload-btn {
          font-size: 0.85rem;
          padding: 0.5rem 1rem;
        }

        .events-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
          gap: 1.75rem;
          margin-bottom: 3.5rem;
        }

        /* Skeleton Styles */
        .event-card-skeleton {
          background: var(--bg-card);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-lg);
          overflow: hidden;
          height: 380px;
          display: flex;
          flex-direction: column;
        }

        .skeleton-visual-header {
          height: 130px;
          width: 100%;
        }

        .skeleton-body {
          padding: 1.25rem;
          flex: 1;
          display: flex;
          flex-direction: column;
        }

        .skeleton-title {
          height: 24px;
          width: 80%;
          margin-bottom: 1rem;
        }

        .skeleton-meta {
          height: 16px;
          width: 60%;
          margin-bottom: 0.5rem;
        }

        .skeleton-desc {
          height: 36px;
          width: 100%;
          margin: 1rem 0;
        }

        .skeleton-footer {
          height: 40px;
          width: 100%;
          margin-top: auto;
        }

        /* Error state card */
        .error-state-card {
          background: var(--bg-card);
          border: 1px solid rgba(239, 68, 68, 0.25);
          border-radius: var(--radius-lg);
          padding: 2.5rem 2rem;
          text-align: center;
          max-width: 620px;
          margin: 2rem auto 3rem;
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .error-icon-circle {
          width: 58px;
          height: 58px;
          border-radius: 50%;
          background: var(--status-danger-bg);
          color: var(--status-danger);
          display: flex;
          align-items: center;
          justify-content: center;
          margin-bottom: 1rem;
        }

        .error-heading {
          font-size: 1.35rem;
          margin-bottom: 0.5rem;
        }

        .error-message-text {
          font-size: 0.95rem;
          color: var(--text-secondary);
          margin-bottom: 1.5rem;
        }

        .cors-help-box {
          background: rgba(0, 0, 0, 0.35);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-md);
          padding: 1.1rem;
          text-align: left;
          font-size: 0.825rem;
          color: var(--text-secondary);
          margin-bottom: 1.5rem;
          width: 100%;
          line-height: 1.5;
        }

        .cors-help-title {
          display: flex;
          align-items: center;
          gap: 0.45rem;
          font-weight: 700;
          color: var(--accent-gold);
          margin-bottom: 0.5rem;
        }

        .cors-help-box ul {
          margin-top: 0.5rem;
          padding-left: 1.25rem;
        }

        .cors-help-box li {
          margin-bottom: 0.35rem;
        }

        .retry-btn {
          padding: 0.75rem 1.75rem;
        }

        /* Empty state */
        .empty-state-card {
          background: var(--bg-card);
          border: 1px dashed var(--border-medium);
          border-radius: var(--radius-lg);
          padding: 3rem 2rem;
          text-align: center;
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.75rem;
          margin: 2rem 0;
        }

        .empty-icon {
          color: var(--text-muted);
        }

        /* Architecture Panel */
        .architecture-panel {
          background: linear-gradient(180deg, #121522 0%, #0d101a 100%);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-lg);
          padding: 2rem 2.25rem;
          margin-top: 2rem;
        }

        .panel-header {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          margin-bottom: 1.75rem;
          flex-wrap: wrap;
          gap: 1rem;
        }

        .panel-title-group h3 {
          font-size: 1.35rem;
          margin-top: 0.5rem;
        }

        .backend-tag {
          font-size: 0.775rem;
          font-family: monospace;
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid var(--border-subtle);
          padding: 0.3rem 0.65rem;
          border-radius: var(--radius-sm);
          color: var(--text-secondary);
        }

        .architecture-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
          gap: 1.5rem;
        }

        .arch-card {
          background: rgba(255, 255, 255, 0.02);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-md);
          padding: 1.25rem;
        }

        .arch-icon-box {
          width: 36px;
          height: 36px;
          border-radius: var(--radius-sm);
          background: rgba(99, 102, 241, 0.15);
          color: #a5b4fc;
          display: flex;
          align-items: center;
          justify-content: center;
          margin-bottom: 0.85rem;
        }

        .arch-card h4 {
          font-size: 1.025rem;
          margin-bottom: 0.45rem;
          color: var(--text-primary);
        }

        .arch-card p {
          font-size: 0.825rem;
          color: var(--text-secondary);
          line-height: 1.5;
        }

        .arch-card code {
          background: rgba(0, 0, 0, 0.35);
          padding: 0.1rem 0.35rem;
          border-radius: 4px;
          color: var(--accent-gold);
        }

        /* Footer */
        .footer-root {
          background: #07090e;
          border-top: 1px solid var(--border-subtle);
          padding: 1.5rem 0;
          font-size: 0.825rem;
          color: var(--text-muted);
        }

        .footer-inner {
          display: flex;
          align-items: center;
          justify-content: space-between;
          flex-wrap: wrap;
          gap: 1rem;
        }

        .footer-left {
          display: flex;
          flex-direction: column;
          gap: 0.25rem;
        }

        .footer-brand {
          font-family: var(--font-display);
          font-size: 1rem;
          font-weight: 800;
          color: var(--text-primary);
        }

        .footer-status-pill {
          font-size: 0.75rem;
        }

        .footer-status-pill code {
          color: var(--accent-gold);
          background: rgba(0, 0, 0, 0.4);
          padding: 0.2rem 0.45rem;
          border-radius: 4px;
        }

        @media (max-width: 640px) {
          .events-grid {
            grid-template-columns: 1fr;
          }
          .footer-inner {
            flex-direction: column;
            align-items: flex-start;
          }
        }
      `}</style>
    </div>
  )
}

export default App
