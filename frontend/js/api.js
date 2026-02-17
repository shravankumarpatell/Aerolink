/**
 * AeroLink API Client v2
 * Batch pooling model with SSE real-time updates.
 */
const BASE = '';

async function request(method, path, body = null) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (body) opts.body = JSON.stringify(body);

  const res = await fetch(`${BASE}${path}`, opts);
  const text = await res.text();

  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }

  if (!res.ok) {
    const msg = data?.message || data?.error || `HTTP ${res.status}`;
    throw new Error(msg);
  }
  return data;
}

// ─── Health ──────────────────────────────
export async function getHealth() {
  return request('GET', '/actuator/health');
}

// ─── Passengers ──────────────────────────
export async function getAllPassengers() {
  // We'll use the rides endpoint to get passenger info
  // In a real app this would be a dedicated endpoint
  return request('GET', '/api/v1/rides');
}

// ─── Pricing ─────────────────────────────
export async function estimatePrice({ pickupLat, pickupLng, dropLat, dropLng }) {
  return request('POST', '/api/v1/pricing/estimate', {
    pickupLat, pickupLng, dropLat, dropLng,
  });
}

// ─── Rides ───────────────────────────────
export async function requestRide({ passengerId, pickupLat, pickupLng, dropLat, dropLng, passengerCount, luggageCount, maxDetourKm, idempotencyKey }) {
  return request('POST', '/api/v1/rides', {
    passengerId, pickupLat, pickupLng, dropLat, dropLng,
    passengerCount, luggageCount, maxDetourKm, idempotencyKey,
  });
}

export async function getRide(id) {
  return request('GET', `/api/v1/rides/${id}`);
}

export async function cancelRide(id, reason = 'Cancelled from dashboard') {
  return request('POST', `/api/v1/rides/${id}/cancel`, { reason });
}

export async function getPool(poolId) {
  return request('GET', `/api/v1/rides/pool/${poolId}`);
}

export async function getAllRides() {
  return request('GET', '/api/v1/rides');
}

// ─── Dashboards ──────────────────────────
export async function getPassengerDashboard(passengerId) {
  return request('GET', `/api/v1/rides/passenger/${passengerId}/dashboard`);
}

export async function getDriverDashboard(cabId) {
  return request('GET', `/api/v1/rides/driver/${cabId}/dashboard`);
}

// ─── Cabs ────────────────────────────────
export async function getCab(id) {
  return request('GET', `/api/v1/cabs/${id}`);
}

export async function getAllCabs() {
  return request('GET', '/api/v1/cabs');
}

export async function updateCabLocation(id, { lat, lng }) {
  return request('PATCH', `/api/v1/cabs/${id}/location`, { lat, lng });
}

export async function startTrip(cabId) {
  return request('POST', `/api/v1/cabs/${cabId}/start-trip`);
}

export async function completeRide(cabId) {
  return request('POST', `/api/v1/cabs/${cabId}/complete-ride`);
}

// ─── SSE Connections ─────────────────────
export function connectPassengerSSE(passengerId, onEvent) {
  const eventSource = new EventSource(`${BASE}/api/v1/sse/passenger/${passengerId}`);

  const events = ['POOL_JOINED', 'POOL_DISPATCHED', 'POOL_WAITING', 'RIDE_STARTED',
    'RIDE_COMPLETED', 'RIDE_CANCELLED', 'RIDER_CANCELLED', 'PRICE_UPDATED', 'POOL_DISSOLVED'];

  events.forEach(evt => {
    eventSource.addEventListener(evt, (e) => {
      let data = e.data;
      try { data = JSON.parse(e.data); } catch {}
      onEvent(evt, data);
    });
  });

  eventSource.onerror = () => {
    console.warn('SSE passenger connection lost, will reconnect...');
  };

  return eventSource;
}

export function connectDriverSSE(cabId, onEvent) {
  const eventSource = new EventSource(`${BASE}/api/v1/sse/driver/${cabId}`);

  const events = ['TRIP_ASSIGNED', 'TRIP_CANCELLED', 'RIDER_CANCELLED'];

  events.forEach(evt => {
    eventSource.addEventListener(evt, (e) => {
      let data = e.data;
      try { data = JSON.parse(e.data); } catch {}
      onEvent(evt, data);
    });
  });

  eventSource.onerror = () => {
    console.warn('SSE driver connection lost, will reconnect...');
  };

  return eventSource;
}
