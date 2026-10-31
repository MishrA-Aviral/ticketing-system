import React from 'react'
import { Calendar, MapPin, Tag, ArrowRight, Music2, Sparkles } from 'lucide-react'
import type { Event } from '../types'

interface EventCardProps {
  event: Event
  onSelect: (event: Event) => void
}

export const EventCard: React.FC<EventCardProps> = ({ event, onSelect }) => {
  // Format the ISO date
  const parseEventDate = (dateStr: string) => {
    try {
      const date = new Date(dateStr)
      if (isNaN(date.getTime())) return { dateText: dateStr, timeText: '', countdown: '' }

      const dateOptions: Intl.DateTimeFormatOptions = {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      }
      const timeOptions: Intl.DateTimeFormatOptions = {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true,
      }

      const dateText = date.toLocaleDateString('en-US', dateOptions)
      const timeText = date.toLocaleTimeString('en-US', timeOptions)

      // Calculate days remaining
      const now = new Date()
      const diffMs = date.getTime() - now.getTime()
      const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24))
      const countdown = diffDays > 0 ? `${diffDays} days away` : 'Past event'

      return { dateText, timeText, countdown }
    } catch {
      return { dateText: dateStr, timeText: '', countdown: '' }
    }
  }

  const { dateText, timeText, countdown } = parseEventDate(event.eventDate)

  // Curated theme gradients by event ID or name for high resilience (never breaks even if remote images fail)
  const getEventVisualTheme = (id: number, name: string) => {
    const themes = [
      {
        bg: 'linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%)',
        accent: '#818cf8',
        tag: 'World Tour',
      },
      {
        bg: 'linear-gradient(135deg, #451a03 0%, #78350f 50%, #b45309 100%)',
        accent: '#f59e0b',
        tag: 'Stadium Tour',
      },
      {
        bg: 'linear-gradient(135deg, #831843 0%, #9d174d 50%, #be185d 100%)',
        accent: '#f472b6',
        tag: 'Eras Tour',
      },
      {
        bg: 'linear-gradient(135deg, #064e3b 0%, #065f46 50%, #047857 100%)',
        accent: '#34d399',
        tag: 'Live Symphony',
      },
      {
        bg: 'linear-gradient(135deg, #18181b 0%, #27272a 50%, #3f3f46 100%)',
        accent: '#a1a1aa',
        tag: 'Concert Series',
      },
    ]

    const lower = name.toLowerCase()
    if (lower.includes('coldplay')) return themes[0]
    if (lower.includes('ed sheeran')) return themes[1]
    if (lower.includes('taylor swift')) return themes[2]
    if (lower.includes('rahman')) return themes[3]
    return themes[(id || 0) % themes.length]
  }

  const theme = getEventVisualTheme(event.id, event.name)

  return (
    <article className="event-card">
      {/* Visual Header */}
      <div className="card-visual-header" style={{ background: theme.bg }}>
        <div className="visual-top-row">
          <span className="badge badge-gold">
            <Sparkles size={11} /> {theme.tag}
          </span>
          {countdown && <span className="countdown-pill">{countdown}</span>}
        </div>
        <div className="visual-center-icon">
          <Music2 size={36} style={{ color: theme.accent, opacity: 0.8 }} />
        </div>
        <div className="visual-bottom-fade" />
      </div>

      {/* Card Content */}
      <div className="card-content">
        {/* Title */}
        <h2 className="event-title" title={event.name}>
          {event.name}
        </h2>

        {/* Date & Time */}
        <div className="event-meta-item">
          <Calendar size={15} className="meta-icon" />
          <span className="meta-text">
            {dateText} {timeText && <span className="time-sub">• {timeText}</span>}
          </span>
        </div>

        {/* Venue */}
        <div className="event-meta-item">
          <MapPin size={15} className="meta-icon" />
          <span className="meta-text" title={event.venue}>
            {event.venue}
          </span>
        </div>

        {/* Description snippet */}
        {event.description && (
          <p className="event-description" title={event.description}>
            {event.description}
          </p>
        )}

        {/* Footer: Price & CTA */}
        <div className="card-footer">
          <div className="price-block">
            <span className="price-label">Starts at</span>
            <div className="price-value-row">
              <Tag size={15} className="price-icon" />
              <span className="price-number">
                ${Number(event.ticketPrice).toFixed(2)}
              </span>
              <span className="price-unit">/ ticket</span>
            </div>
          </div>

          <button
            type="button"
            className="btn btn-primary book-btn"
            onClick={() => onSelect(event)}
            aria-label={`Book tickets for ${event.name}`}
          >
            <span>Book Tickets</span>
            <ArrowRight size={16} />
          </button>
        </div>
      </div>

      <style>{`
        .event-card {
          background: var(--bg-card);
          border: 1px solid var(--border-subtle);
          border-radius: var(--radius-lg);
          overflow: hidden;
          display: flex;
          flex-direction: column;
          box-shadow: var(--shadow-card);
          transition: all var(--transition-normal);
          position: relative;
        }

        .event-card:hover {
          transform: translateY(-4px);
          border-color: var(--border-medium);
          box-shadow: 0 18px 40px -10px rgba(0, 0, 0, 0.7), var(--shadow-glow);
          background: var(--bg-card-hover);
        }

        .card-visual-header {
          height: 130px;
          position: relative;
          padding: 1rem;
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          overflow: hidden;
        }

        .visual-top-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          z-index: 2;
        }

        .countdown-pill {
          font-size: 0.725rem;
          font-weight: 600;
          color: rgba(255, 255, 255, 0.85);
          background: rgba(0, 0, 0, 0.4);
          backdrop-filter: blur(4px);
          padding: 0.2rem 0.55rem;
          border-radius: var(--radius-full);
          border: 1px solid rgba(255, 255, 255, 0.1);
        }

        .visual-center-icon {
          position: absolute;
          right: 1.5rem;
          bottom: 1rem;
          z-index: 1;
          filter: drop-shadow(0 4px 10px rgba(0, 0, 0, 0.3));
          transition: transform var(--transition-normal);
        }

        .event-card:hover .visual-center-icon {
          transform: scale(1.1) rotate(5deg);
        }

        .visual-bottom-fade {
          position: absolute;
          bottom: 0;
          left: 0;
          right: 0;
          height: 40px;
          background: linear-gradient(to top, var(--bg-card), transparent);
          z-index: 2;
        }

        .card-content {
          padding: 1.25rem 1.4rem 1.4rem;
          display: flex;
          flex-direction: column;
          flex: 1;
        }

        .event-title {
          font-size: 1.25rem;
          line-height: 1.3;
          margin-bottom: 0.85rem;
          color: var(--text-primary);
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
          min-height: 2.6rem;
        }

        .event-meta-item {
          display: flex;
          align-items: center;
          gap: 0.55rem;
          font-size: 0.875rem;
          color: var(--text-secondary);
          margin-bottom: 0.45rem;
        }

        .meta-icon {
          color: var(--accent-gold);
          flex-shrink: 0;
        }

        .meta-text {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .time-sub {
          color: var(--text-muted);
        }

        .event-description {
          font-size: 0.825rem;
          color: var(--text-muted);
          line-height: 1.5;
          margin: 0.75rem 0 1.25rem;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
          flex: 1;
        }

        .card-footer {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding-top: 1rem;
          border-top: 1px solid var(--border-subtle);
          margin-top: auto;
          gap: 0.75rem;
        }

        .price-block {
          display: flex;
          flex-direction: column;
        }

        .price-label {
          font-size: 0.7rem;
          text-transform: uppercase;
          color: var(--text-muted);
          font-weight: 600;
          letter-spacing: 0.05em;
        }

        .price-value-row {
          display: flex;
          align-items: baseline;
          gap: 0.25rem;
        }

        .price-icon {
          color: var(--accent-gold);
        }

        .price-number {
          font-size: 1.35rem;
          font-weight: 800;
          font-family: var(--font-display);
          color: var(--text-primary);
        }

        .price-unit {
          font-size: 0.75rem;
          color: var(--text-muted);
        }

        .book-btn {
          padding: 0.6rem 1.1rem;
          font-size: 0.875rem;
        }
      `}</style>
    </article>
  )
}
