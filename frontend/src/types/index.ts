export type BookingStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED'

export interface Event {
  id: number
  name: string
  venue: string
  description: string | null
  eventDate: string
  ticketPrice: number
  createdAt: string
}

export interface BookingRequest {
  userId: number
  eventId: number
  quantity: number
}

export interface BookingResponse {
  bookingId: number
  userId: number
  eventId: number
  eventName: string
  quantity: number
  totalAmount: number
  status: BookingStatus
  createdAt: string
  expiresAt: string
}

export interface Ticket {
  ticketId: string
  ticketNumber: string
  issuedAt: string
}

export interface BookingConfirmationResponse {
  bookingId: number
  userId: number
  eventId: number
  eventName: string
  quantity: number
  totalAmount: number
  status: BookingStatus
  createdAt: string
  expiresAt: string
  confirmedAt: string
  tickets: Ticket[]
}

export interface ApiError {
  timestamp?: string
  status?: number
  error?: string
  message: string
}
