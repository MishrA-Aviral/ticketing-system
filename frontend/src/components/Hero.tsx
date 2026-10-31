import React from 'react'
import { Search, X, ShieldCheck, Zap, Clock } from 'lucide-react'

interface HeroProps {
  searchQuery: string
  onSearchChange: (query: string) => void
  selectedCategory: string
  onCategorySelect: (cat: string) => void
  categories: string[]
}

export const Hero: React.FC<HeroProps> = ({
  searchQuery,
  onSearchChange,
  selectedCategory,
  onCategorySelect,
  categories,
}) => {
  return (
    <section className="hero-section">
      <div className="container hero-container">
        {/* Highlight pill */}
        <div className="hero-pill-badge">
          <Zap size={14} className="hero-pill-icon" />
          <span>High-Concurrency Ticket Engine • Live 2026/2027 Tours</span>
        </div>

        {/* Headline */}
        <h1 className="hero-title">
          Live Stadium Tours &amp; <br />
          <span className="hero-gradient-text">Instant Guaranteed Tickets</span>
        </h1>

        <p className="hero-subtitle">
          Experience world-class concerts with real-time atomic inventory reservation. 
          Protected against race conditions, overselling, and lost seats.
        </p>

        {/* Search Bar */}
        <div className="hero-search-wrapper">
          <div className="search-bar">
            <Search size={20} className="search-icon" />
            <input
              type="text"
              className="search-input"
              placeholder="Search by artist, tour, venue (e.g. Coldplay, Ahmedabad, Delhi)..."
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
            />
            {searchQuery && (
              <button
                type="button"
                className="search-clear-btn"
                onClick={() => onSearchChange('')}
                title="Clear search"
              >
                <X size={16} />
              </button>
            )}
          </div>
        </div>

        {/* Category Filter Chips */}
        <div className="hero-categories">
          {categories.map((cat) => (
            <button
              key={cat}
              type="button"
              className={`category-chip ${selectedCategory === cat ? 'active' : ''}`}
              onClick={() => onCategorySelect(cat)}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Platform Architecture Highlights */}
        <div className="hero-features">
          <div className="feature-item">
            <ShieldCheck size={16} className="feature-icon" />
            <span>Pessimistic Row Locking (No Overselling)</span>
          </div>
          <div className="feature-separator">•</div>
          <div className="feature-item">
            <Clock size={16} className="feature-icon" />
            <span>10-Minute Reservation Expiry Engine</span>
          </div>
          <div className="feature-separator">•</div>
          <div className="feature-item">
            <Zap size={16} className="feature-icon" />
            <span>Instant UUID Ticket Issuance</span>
          </div>
        </div>
      </div>

      <style>{`
        .hero-section {
          padding: 3.5rem 0 2.5rem;
          text-align: center;
          position: relative;
        }

        .hero-container {
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .hero-pill-badge {
          display: inline-flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.35rem 0.95rem;
          border-radius: var(--radius-full);
          background: rgba(245, 158, 11, 0.1);
          border: 1px solid rgba(245, 158, 11, 0.25);
          color: var(--accent-gold);
          font-size: 0.8rem;
          font-weight: 700;
          letter-spacing: 0.02em;
          margin-bottom: 1.5rem;
        }

        .hero-pill-icon {
          color: var(--accent-gold);
        }

        .hero-title {
          font-size: 3.25rem;
          line-height: 1.15;
          margin-bottom: 1.25rem;
          max-width: 900px;
        }

        .hero-gradient-text {
          background: linear-gradient(135deg, #f8fafc 30%, var(--accent-gold) 100%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
        }

        .hero-subtitle {
          font-size: 1.125rem;
          color: var(--text-secondary);
          max-width: 650px;
          line-height: 1.6;
          margin-bottom: 2rem;
        }

        .hero-search-wrapper {
          width: 100%;
          max-width: 640px;
          margin-bottom: 1.25rem;
        }

        .search-bar {
          display: flex;
          align-items: center;
          background: var(--bg-card);
          border: 1px solid var(--border-medium);
          border-radius: var(--radius-full);
          padding: 0.4rem 0.6rem 0.4rem 1.25rem;
          box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.6);
          transition: all var(--transition-normal);
        }

        .search-bar:focus-within {
          border-color: var(--accent-gold);
          box-shadow: 0 0 0 3px var(--accent-gold-glow), 0 15px 35px -10px rgba(0, 0, 0, 0.7);
        }

        .search-icon {
          color: var(--text-muted);
          flex-shrink: 0;
          margin-right: 0.75rem;
        }

        .search-input {
          flex: 1;
          background: transparent;
          border: none;
          outline: none;
          color: var(--text-primary);
          font-size: 0.975rem;
        }

        .search-input::placeholder {
          color: var(--text-muted);
        }

        .search-clear-btn {
          background: rgba(255, 255, 255, 0.08);
          color: var(--text-secondary);
          border-radius: 50%;
          width: 28px;
          height: 28px;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: all var(--transition-fast);
        }

        .search-clear-btn:hover {
          background: rgba(255, 255, 255, 0.15);
          color: var(--text-primary);
        }

        .hero-categories {
          display: flex;
          flex-wrap: wrap;
          justify-content: center;
          gap: 0.5rem;
          margin-bottom: 2.25rem;
        }

        .category-chip {
          padding: 0.4rem 0.95rem;
          border-radius: var(--radius-full);
          background: rgba(255, 255, 255, 0.04);
          border: 1px solid var(--border-subtle);
          color: var(--text-secondary);
          font-size: 0.85rem;
          font-weight: 600;
          transition: all var(--transition-fast);
        }

        .category-chip:hover {
          background: rgba(255, 255, 255, 0.08);
          color: var(--text-primary);
          border-color: var(--border-medium);
        }

        .category-chip.active {
          background: var(--text-primary);
          color: #0b0d13;
          border-color: var(--text-primary);
        }

        .hero-features {
          display: flex;
          align-items: center;
          justify-content: center;
          flex-wrap: wrap;
          gap: 0.85rem;
          font-size: 0.825rem;
          color: var(--text-muted);
        }

        .feature-item {
          display: flex;
          align-items: center;
          gap: 0.4rem;
        }

        .feature-icon {
          color: var(--accent-gold);
        }

        .feature-separator {
          color: var(--border-medium);
        }

        @media (max-width: 768px) {
          .hero-title {
            font-size: 2.25rem;
          }
          .hero-subtitle {
            font-size: 1rem;
          }
          .hero-features {
            display: none;
          }
        }
      `}</style>
    </section>
  )
}
