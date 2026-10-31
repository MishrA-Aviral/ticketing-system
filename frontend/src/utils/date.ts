/**
 * Date and time parsing utilities for API responses.
 *
 * <p>The backend Spring Boot service runs in UTC and emits ISO-8601 timestamps
 * using Java's {@code LocalDateTime} (e.g., "2026-10-31T22:55:00" or
 * "2026-10-31T22:55:00.123456").
 *
 * <p>According to ECMAScript specifications, date-time strings without an explicit
 * timezone offset or 'Z' suffix are parsed by {@code new Date(str)} as local time
 * in the browser's timezone. In timezones east of UTC (such as IST, UTC+05:30),
 * this causes a UTC timestamp to be parsed 5.5 hours in the past, making newly
 * created reservations immediately appear expired (00:00).
 *
 * <p>This utility normalizes timestamps so that strings without timezone offsets
 * are correctly interpreted as UTC points in time.
 */

/**
 * Safely parses an API ISO date-time string into epoch milliseconds (UTC).
 *
 * @param dateStr ISO date-time string from the API (with or without 'Z'/offset)
 * @returns epoch milliseconds, or NaN if input is null, undefined, or invalid
 */
export function parseApiDateTime(dateStr: string | null | undefined): number {
  if (!dateStr || typeof dateStr !== 'string') {
    return NaN
  }
  const trimmed = dateStr.trim()
  if (!trimmed) {
    return NaN
  }

  // Normalize sub-millisecond precision (>3 decimal places) to 3 digits (milliseconds)
  // for strict cross-browser Date parser compatibility
  const withMillis = trimmed.replace(/\.(\d{3})\d+/, (_, ms: string) => `.${ms}`)

  // Check if string already contains a timezone designator:
  // 'Z' or 'z' at the end, or offset like '+05:30', '-04:00', '+0000', '+05'
  const hasTimezone = /[Zz]$|[+-]\d{2}(?::?\d{2})?$/.test(withMillis)
  const normalized = hasTimezone ? withMillis : `${withMillis}Z`

  const timestamp = new Date(normalized).getTime()
  return timestamp
}

export interface CountdownState {
  timeLeftStr: string
  isExpired: boolean
  diffMs: number
}

/**
 * Calculates countdown digits and expiration state from an API expiry timestamp.
 *
 * @param expiresAtStr the ISO expiry timestamp string from the backend
 * @param nowMs optional current epoch timestamp (defaults to Date.now())
 * @returns {@link CountdownState} with formatted MM:SS string and expiration flag
 */
export function calculateCountdown(
  expiresAtStr: string | null | undefined,
  nowMs: number = Date.now()
): CountdownState {
  const expiresAt = parseApiDateTime(expiresAtStr)
  if (isNaN(expiresAt)) {
    return { timeLeftStr: '--:--', isExpired: false, diffMs: 0 }
  }

  const diffMs = expiresAt - nowMs
  if (diffMs <= 0) {
    return { timeLeftStr: '00:00', isExpired: true, diffMs }
  }

  const totalSecs = Math.floor(diffMs / 1000)
  const mins = Math.floor(totalSecs / 60)
  const secs = totalSecs % 60
  const timeLeftStr = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`

  return { timeLeftStr, isExpired: false, diffMs }
}

