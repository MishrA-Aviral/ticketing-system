import type { Event, BookingRequest, BookingResponse, BookingConfirmationResponse, ApiError } from '../types'

// Resolve API base URL: custom env var, or empty string (relative) for Vite dev proxy
const envBaseUrl = import.meta.env.VITE_API_BASE_URL || ''
export const API_BASE_URL = envBaseUrl.endsWith('/') ? envBaseUrl.slice(0, -1) : envBaseUrl

export class ApiRequestError extends Error {
  status?: number
  errorTitle?: string
  isCorsError?: boolean

  constructor(message: string, status?: number, errorTitle?: string, isCorsError: boolean = false) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.errorTitle = errorTitle
    this.isCorsError = isCorsError
  }
}

/**
 * Universal request wrapper with comprehensive error handling and CORS detection.
 */
async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`

  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(options.headers as Record<string, string>),
  }

  if (options.body && typeof options.body === 'string' && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(url, { ...options, headers })
  } catch (err: unknown) {
    // If fetch failed completely, it is typically a network failure or a browser CORS block
    const isNetworkOrCors = err instanceof TypeError && (err.message.includes('fetch') || err.message.includes('NetworkError'))
    
    // In dev mode, if direct URL fails, attempt fallback via Vite's proxy
    if (import.meta.env.DEV && API_BASE_URL && isNetworkOrCors) {
      try {
        console.warn(`[Tix-n-Trix] Direct fetch to ${url} failed (likely CORS). Retrying through Vite dev proxy...`)
        const proxyResponse = await fetch(endpoint, { ...options, headers })
        return await handleResponse<T>(proxyResponse)
      } catch (proxyErr) {
        console.error('[Tix-n-Trix] Proxy retry also failed:', proxyErr)
      }
    }

    const message = isNetworkOrCors
      ? `Network or CORS error: Unable to reach backend at ${url}. If testing cross-origin in browser, ensure CORS is enabled on the server or use the local proxy.`
      : (err instanceof Error ? err.message : 'Unknown network error')

    throw new ApiRequestError(message, undefined, 'Network Error', isNetworkOrCors)
  }

  return handleResponse<T>(response)
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.ok) {
    // Handle 204 No Content or empty bodies
    const contentType = response.headers.get('content-type')
    if (contentType && contentType.includes('application/json')) {
      return (await response.json()) as T
    }
    const text = await response.text()
    return (text as unknown) as T
  }

  // Attempt to parse structured ErrorResponse from Spring Boot GlobalExceptionHandler
  let errorMessage = `HTTP error ${response.status}: ${response.statusText}`
  let errorTitle = response.statusText

  try {
    const errorJson = (await response.json()) as ApiError
    if (errorJson.message) {
      errorMessage = errorJson.message
    }
    if (errorJson.error) {
      errorTitle = errorJson.error
    }
  } catch {
    // Response was not JSON, use default status text
  }

  throw new ApiRequestError(errorMessage, response.status, errorTitle)
}

/**
 * Fetch all available concert events
 * GET /events
 */
export async function fetchEvents(): Promise<Event[]> {
  return request<Event[]>('/events', { method: 'GET' })
}

/**
 * Create a new pending booking with safe inventory reservation
 * POST /bookings
 */
export async function createBooking(payload: BookingRequest): Promise<BookingResponse> {
  return request<BookingResponse>('/bookings', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/**
 * Confirm a pending booking and issue tickets
 * POST /bookings/{bookingId}/confirm
 */
export async function confirmBooking(bookingId: number): Promise<BookingConfirmationResponse> {
  return request<BookingConfirmationResponse>(`/bookings/${bookingId}/confirm`, {
    method: 'POST',
  })
}

/**
 * Health check on API root
 * GET /
 */
export async function checkApiHealth(): Promise<string> {
  const url = `${API_BASE_URL}/`
  try {
    const res = await fetch(url)
    return await res.text()
  } catch {
    // Try proxy fallback in dev
    if (import.meta.env.DEV && API_BASE_URL) {
      const res = await fetch('/')
      return await res.text()
    }
    throw new Error('Backend health check failed')
  }
}
