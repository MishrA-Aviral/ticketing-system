import { describe, it } from 'node:test'
import assert from 'node:assert'
import { parseApiDateTime, calculateCountdown } from './date.ts'

describe('parseApiDateTime', () => {
  it('parses API ISO timestamp without timezone as UTC', () => {
    // Backend emits LocalDateTime without timezone, e.g. "2026-10-31T22:55:00"
    const timestamp = parseApiDateTime('2026-10-31T22:55:00')
    assert.strictEqual(isNaN(timestamp), false)
    assert.strictEqual(new Date(timestamp).toISOString(), '2026-10-31T22:55:00.000Z')
  })

  it('parses API ISO timestamp with microseconds without timezone as UTC', () => {
    // Jackson LocalDateTime with nanosecond/microsecond precision
    const timestamp = parseApiDateTime('2026-10-31T22:55:00.123456')
    assert.strictEqual(isNaN(timestamp), false)
    assert.strictEqual(new Date(timestamp).toISOString(), '2026-10-31T22:55:00.123Z')
  })

  it('parses timestamp that already includes Z suffix as UTC', () => {
    const timestamp = parseApiDateTime('2026-10-31T22:55:00Z')
    assert.strictEqual(isNaN(timestamp), false)
    assert.strictEqual(new Date(timestamp).toISOString(), '2026-10-31T22:55:00.000Z')
  })

  it('parses timestamp with explicit numeric timezone offset', () => {
    const timestampUtc = parseApiDateTime('2026-10-31T22:55:00+00:00')
    assert.strictEqual(new Date(timestampUtc).toISOString(), '2026-10-31T22:55:00.000Z')

    const timestampIst = parseApiDateTime('2026-11-01T04:25:00+05:30')
    assert.strictEqual(new Date(timestampIst).toISOString(), '2026-10-31T22:55:00.000Z')
  })

  it('returns NaN for invalid or empty input safely', () => {
    assert.strictEqual(isNaN(parseApiDateTime(null)), true)
    assert.strictEqual(isNaN(parseApiDateTime(undefined)), true)
    assert.strictEqual(isNaN(parseApiDateTime('')), true)
    assert.strictEqual(isNaN(parseApiDateTime('   ')), true)
    assert.strictEqual(isNaN(parseApiDateTime('not-a-date')), true)
  })
})

describe('calculateCountdown', () => {
  it('calculates positive countdown for newly created reservation in API format (no Z)', () => {
    // Created at 2026-10-31T22:45:00Z, expires 10 mins later at 2026-10-31T22:55:00
    const apiExpiryStr = '2026-10-31T22:55:00'
    const creationTimeMs = new Date('2026-10-31T22:45:00Z').getTime()

    const state = calculateCountdown(apiExpiryStr, creationTimeMs)
    assert.strictEqual(state.isExpired, false)
    assert.strictEqual(state.timeLeftStr, '10:00')
    assert.strictEqual(state.diffMs, 10 * 60 * 1000)
  })

  it('calculates countdown accurately when partially elapsed', () => {
    const apiExpiryStr = '2026-10-31T22:55:00'
    // 2 minutes after creation -> 8 minutes remaining
    const nowMs = new Date('2026-10-31T22:47:00Z').getTime()

    const state = calculateCountdown(apiExpiryStr, nowMs)
    assert.strictEqual(state.isExpired, false)
    assert.strictEqual(state.timeLeftStr, '08:00')
  })

  it('calculates seconds accurately down to last few seconds', () => {
    const apiExpiryStr = '2026-10-31T22:55:00'
    // 5 seconds remaining
    const nowMs = new Date('2026-10-31T22:54:55Z').getTime()

    const state = calculateCountdown(apiExpiryStr, nowMs)
    assert.strictEqual(state.isExpired, false)
    assert.strictEqual(state.timeLeftStr, '00:05')
  })

  it('marks reservation expired at exact deadline boundary (diffMs == 0)', () => {
    const apiExpiryStr = '2026-10-31T22:55:00'
    const nowMs = new Date('2026-10-31T22:55:00Z').getTime()

    const state = calculateCountdown(apiExpiryStr, nowMs)
    assert.strictEqual(state.isExpired, true)
    assert.strictEqual(state.timeLeftStr, '00:00')
  })

  it('marks reservation expired when deadline has passed (diffMs < 0)', () => {
    const apiExpiryStr = '2026-10-31T22:55:00'
    // 10 seconds past deadline
    const nowMs = new Date('2026-10-31T22:55:10Z').getTime()

    const state = calculateCountdown(apiExpiryStr, nowMs)
    assert.strictEqual(state.isExpired, true)
    assert.strictEqual(state.timeLeftStr, '00:00')
  })

  it('returns graceful fallback state for null or invalid expiry timestamp', () => {
    const stateNull = calculateCountdown(null)
    assert.strictEqual(stateNull.isExpired, false)
    assert.strictEqual(stateNull.timeLeftStr, '--:--')

    const stateInvalid = calculateCountdown('invalid')
    assert.strictEqual(stateInvalid.isExpired, false)
    assert.strictEqual(stateInvalid.timeLeftStr, '--:--')
  })
})
