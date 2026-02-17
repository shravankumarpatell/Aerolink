/**
 * AeroLink v2 — Main Application
 * Handles all 3 views: Passenger, Driver, Admin
 * Real-time updates via SSE
 */
import * as api from './api.js';
import { populateLocationSelect, getSelectedLocation, resolveLocationName } from './locations.js';

// ─── State ────────────────────────────────
let state = {
  currentView: 'passenger',
  passengers: [],       // [{id, name}]
  cabs: [],             // from backend
  selectedPassenger: null,
  selectedCab: null,
  passengerSSE: null,
  driverSSE: null,
  countdownInterval: null,
};

// ─── Init ─────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  setupTabs();
  setupButtons();
  setupLocationDropdowns();
  await loadInitialData();
});

// ─── Location Dropdowns ───────────────────
function setupLocationDropdowns() {
  populateLocationSelect('estPickupLoc');
  populateLocationSelect('estDropLoc');
  populateLocationSelect('bookPickupLoc');
  populateLocationSelect('bookDropLoc');
}

// ─── Tab Navigation ───────────────────────
function setupTabs() {
  document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      const view = tab.dataset.view;
      document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
      document.getElementById(`${view}View`).classList.add('active');
      state.currentView = view;

      if (view === 'admin') refreshAdmin();
    });
  });
}

// ─── Button Handlers ──────────────────────
function setupButtons() {
  document.getElementById('btnEstimate').addEventListener('click', handleEstimate);
  document.getElementById('btnBook').addEventListener('click', handleBook);
  document.getElementById('btnStartTrip').addEventListener('click', handleStartTrip);
  document.getElementById('btnCompleteRide').addEventListener('click', handleCompleteRide);
  document.getElementById('btnRefreshAdmin').addEventListener('click', refreshAdmin);

  document.getElementById('passengerSelect').addEventListener('change', (e) => {
    state.selectedPassenger = e.target.value || null;
    if (state.selectedPassenger) {
      refreshPassengerDashboard();
      connectPassengerSSE();
    }
  });

  document.getElementById('driverSelect').addEventListener('change', (e) => {
    state.selectedCab = e.target.value || null;
    if (state.selectedCab) {
      refreshDriverDashboard();
      connectDriverSSE();
    }
  });

  // Refresh nearby cabs when pickup location changes
  document.getElementById('bookPickupLoc').addEventListener('change', refreshNearbyCabs);
}

// ─── Load Initial Data ────────────────────
async function loadInitialData() {
  try {
    // Load cabs to populate both passenger list (via rides) and driver list
    const cabs = await api.getAllCabs();
    state.cabs = cabs;
    populateDriverSelect(cabs);

    // Load rides to get unique passengers from history
    const rides = await api.getAllRides();
    const passengerMap = new Map();

    // Always populate known seed passengers first
    const knownPassengers = [
      { id: 'a0000000-0000-0000-0000-000000000001', name: 'Rahul Sharma' },
      { id: 'a0000000-0000-0000-0000-000000000002', name: 'Priya Patel' },
      { id: 'a0000000-0000-0000-0000-000000000003', name: 'Amit Kumar' },
      { id: 'a0000000-0000-0000-0000-000000000004', name: 'Sneha Reddy' },
      { id: 'a0000000-0000-0000-0000-000000000005', name: 'Vikram Singh' },
      { id: 'a0000000-0000-0000-0000-000000000006', name: 'Anjali Desai' },
      { id: 'a0000000-0000-0000-0000-000000000007', name: 'Karan Mehta' },
      { id: 'a0000000-0000-0000-0000-000000000008', name: 'Divya Iyer' },
      { id: 'a0000000-0000-0000-0000-000000000009', name: 'Arjun Nair' },
      { id: 'a0000000-0000-0000-0000-000000000010', name: 'Meera Joshi' },
      { id: 'a0000000-0000-0000-0000-000000000011', name: 'Rohan Gupta' },
      { id: 'a0000000-0000-0000-0000-000000000012', name: 'Neha Kapoor' },
      { id: 'a0000000-0000-0000-0000-000000000013', name: 'Siddharth Rao' },
      { id: 'a0000000-0000-0000-0000-000000000014', name: 'Pooja Malhotra' },
      { id: 'a0000000-0000-0000-0000-000000000015', name: 'Aditya Verma' },
    ];
    knownPassengers.forEach(p => passengerMap.set(p.id, p.name));

    // Merge any additional passengers found from ride history
    rides.forEach(r => {
      if (r.passengerId && r.passengerName) {
        passengerMap.set(r.passengerId, r.passengerName);
      }
    });

    state.passengers = Array.from(passengerMap, ([id, name]) => ({ id, name }));
    populatePassengerSelect(state.passengers);

    updateStatus('Connected', true);
  } catch (e) {
    updateStatus('Disconnected', false);
    console.error('Failed to load initial data:', e);
  }
}

// ─── Populate Selects ─────────────────────
function populatePassengerSelect(passengers) {
  const sel = document.getElementById('passengerSelect');
  sel.innerHTML = '<option value="">— Choose a passenger —</option>';
  passengers.forEach(p => {
    sel.innerHTML += `<option value="${p.id}">${p.name}</option>`;
  });
}

function populateDriverSelect(cabs) {
  const sel = document.getElementById('driverSelect');
  sel.innerHTML = '<option value="">— Choose a driver —</option>';
  cabs.forEach(c => {
    const statusIcon = c.status === 'AVAILABLE' ? '🟢' : c.status === 'ASSIGNED' ? '🟡' : '🔵';
    sel.innerHTML += `<option value="${c.id}">${statusIcon} ${c.driverName} (${c.licensePlate})</option>`;
  });
}

// ─── SSE Connections ──────────────────────
function connectPassengerSSE() {
  if (state.passengerSSE) state.passengerSSE.close();

  state.passengerSSE = api.connectPassengerSSE(state.selectedPassenger, (event, data) => {
    console.log('SSE Passenger Event:', event, data);

    const messages = {
      'POOL_JOINED': '👥 A new rider joined your pool!',
      'POOL_DISPATCHED': '🚗 Driver assigned! Your ride is confirmed.',
      'POOL_WAITING': '⏳ Looking for a driver...',
      'RIDE_STARTED': '🚀 Your driver is on the way!',
      'RIDE_COMPLETED': '✅ Ride completed! Thank you.',
      'RIDE_CANCELLED': '❌ Your ride was cancelled.',
      'RIDER_CANCELLED': '👤 A rider left the pool.',
      'PRICE_UPDATED': '💰 Your fare has been updated.',
      'POOL_DISSOLVED': '💨 Pool dissolved.',
    };

    showToast(messages[event] || event, event === 'RIDE_CANCELLED' || event === 'POOL_DISSOLVED' ? 'warning' : 'info');
    refreshPassengerDashboard();
  });
}

function connectDriverSSE() {
  if (state.driverSSE) state.driverSSE.close();

  state.driverSSE = api.connectDriverSSE(state.selectedCab, (event, data) => {
    console.log('SSE Driver Event:', event, data);

    const messages = {
      'TRIP_ASSIGNED': '📍 New trip assigned!',
      'TRIP_CANCELLED': '❌ Trip cancelled — all riders left.',
      'RIDER_CANCELLED': '👤 A rider cancelled.',
    };

    showToast(messages[event] || event, event === 'TRIP_CANCELLED' ? 'warning' : 'info');
    refreshDriverDashboard();
  });
}

// ─── Fare Estimator ───────────────────────
async function handleEstimate() {
  const btn = document.getElementById('btnEstimate');
  const result = document.getElementById('estimateResult');

  try {
    btn.disabled = true;
    btn.textContent = 'Calculating…';

    const pickup = getSelectedLocation('estPickupLoc');
    const drop = getSelectedLocation('estDropLoc');

    if (!pickup || !drop) {
      result.classList.remove('hidden');
      result.classList.add('error');
      result.textContent = 'Please select both pickup and drop locations';
      btn.disabled = false;
      btn.textContent = 'Estimate Fare';
      return;
    }

    const data = await api.estimatePrice({
      pickupLat: pickup.lat,
      pickupLng: pickup.lng,
      dropLat: drop.lat,
      dropLng: drop.lng,
    });

    result.classList.remove('hidden', 'error');
    result.innerHTML = `
      <div style="margin-bottom:12px;color:var(--text-muted)">
        Distance: <strong style="color:var(--text-primary)">${data.distanceKm?.toFixed(2)} km</strong> •
        Surge: <strong style="color:var(--text-primary)">${data.surgeFactor?.toFixed(2)}x</strong> •
        Demand: <strong style="color:var(--text-primary)">${data.demandMultiplier?.toFixed(2)}x</strong>
      </div>
      <div class="price-grid">
        ${(data.prices || []).map(p => `
          <div class="price-option">
            <div class="price-label">${p.label}</div>
            <div class="price-value">₹${p.price?.toFixed(0)}</div>
            <div class="price-discount">${p.sharingDiscount > 0 ? `-${(p.sharingDiscount * 100).toFixed(0)}%` : 'Full price'}</div>
          </div>
        `).join('')}
      </div>
      <div style="margin-top:10px;font-size:11px;color:var(--text-muted)">${data.notes || ''}</div>
    `;
  } catch (e) {
    result.classList.remove('hidden');
    result.classList.add('error');
    result.textContent = `Error: ${e.message}`;
  } finally {
    btn.disabled = false;
    btn.textContent = 'Estimate Fare';
  }
}

// ─── Book Ride ────────────────────────────
async function handleBook() {
  if (!state.selectedPassenger) {
    showToast('Please select a passenger first', 'warning');
    return;
  }

  const btn = document.getElementById('btnBook');
  const result = document.getElementById('bookResult');

  try {
    btn.disabled = true;
    btn.textContent = 'Booking…';

    const pickup = getSelectedLocation('bookPickupLoc');
    const drop = getSelectedLocation('bookDropLoc');

    if (!pickup || !drop) {
      showToast('Please select both pickup and drop locations', 'warning');
      btn.disabled = false;
      btn.textContent = '🚀 Book Ride';
      return;
    }

    const ride = await api.requestRide({
      passengerId: state.selectedPassenger,
      pickupLat: pickup.lat,
      pickupLng: pickup.lng,
      dropLat: drop.lat,
      dropLng: drop.lng,
      passengerCount: parseInt(document.getElementById('bookPassCount').value),
      luggageCount: parseInt(document.getElementById('bookLuggage').value),
      maxDetourKm: parseFloat(document.getElementById('bookDetour').value),
      idempotencyKey: crypto.randomUUID(),
    });

    result.classList.remove('hidden', 'error');
    result.innerHTML = `
      <strong style="color:var(--success)">✅ Ride booked!</strong><br>
      Ride ID: <code>${ride.id}</code><br>
      Status: ${badge(ride.status)}<br>
      Pool: <code>${ride.ridePoolId || 'Pending'}</code><br>
      <em style="color:var(--text-muted)">Waiting for pool window to close (60s) or pool to fill…</em>
    `;

    showToast('Ride booked! Waiting for pool dispatch…', 'success');
    refreshPassengerDashboard();
  } catch (e) {
    result.classList.remove('hidden');
    result.classList.add('error');
    result.textContent = `Error: ${e.message}`;
    showToast(e.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = '🚀 Book Ride';
  }
}

// ─── Passenger Dashboard ──────────────────
async function refreshPassengerDashboard() {
  if (!state.selectedPassenger) return;

  try {
    const data = await api.getPassengerDashboard(state.selectedPassenger);
    renderActiveRide(data.activeRide, data.activePool);
    renderRideHistory(data.rideHistory);
    refreshNearbyCabs();
  } catch (e) {
    console.error('Dashboard refresh failed:', e);
  }
}

function renderActiveRide(ride, pool) {
  const panel = document.getElementById('activeRidePanel');

  if (!ride) {
    panel.innerHTML = '<div class="empty-state">No active ride. Book one above!</div>';
    clearCountdown();
    return;
  }

  let html = '';

  // Countdown timer for FORMING pools
  if (pool && pool.status === 'FORMING' && pool.windowExpiresAt) {
    // Backend runs in UTC; Java LocalDateTime serializes without timezone suffix,
    // so we append 'Z' to ensure JavaScript parses it as UTC, not local time.
    const tsStr = pool.windowExpiresAt.endsWith('Z') ? pool.windowExpiresAt : pool.windowExpiresAt + 'Z';
    const expiresAt = new Date(tsStr);
    const now = new Date();
    const secsLeft = Math.max(0, Math.floor((expiresAt - now) / 1000));

    html += `
      <div class="countdown" id="poolCountdown">
        <div class="countdown-ring">
          <svg width="40" height="40">
            <circle class="track" cx="20" cy="20" r="17"/>
            <circle class="progress" cx="20" cy="20" r="17"
              stroke-dasharray="${2 * Math.PI * 17}"
              stroke-dashoffset="${2 * Math.PI * 17 * (1 - secsLeft/60)}"
            />
          </svg>
        </div>
        <div>
          <div class="countdown-text" id="countdownValue">${secsLeft}s</div>
          <div class="countdown-label">until dispatch</div>
        </div>
      </div>
    `;

    startCountdown(expiresAt);
  }

  // Pool info
  if (pool) {
    html += `
      <div class="pool-info">
        <div class="pool-stat">
          <div class="value">${pool.totalOccupiedSeats}/4</div>
          <div class="label">Seats Filled</div>
        </div>
        <div class="pool-stat">
          <div class="value">${pool.totalLuggage}/4</div>
          <div class="label">Luggage</div>
        </div>
        <div class="pool-stat">
          <div class="value">${pool.totalRouteDistanceKm?.toFixed(1)} km</div>
          <div class="label">Route Distance</div>
        </div>
        <div class="pool-stat">
          <div class="value">${badge(pool.status)}</div>
          <div class="label">Pool Status</div>
        </div>
      </div>
    `;

    // Driver info (after dispatch)
    if (pool.cabId) {
      html += `
        <div style="margin-bottom:12px;padding:12px;border-radius:var(--radius-md);background:rgba(34,197,94,0.08);border:1px solid rgba(34,197,94,0.2)">
          <strong style="color:var(--success)">🚗 Driver Assigned</strong><br>
          <span style="color:var(--text-secondary)">${pool.driverName || 'Unknown'} • ${pool.cabLicensePlate || ''}</span>
        </div>
      `;
    }

    // Riders in pool
    if (pool.riders && pool.riders.length > 0) {
      html += '<div class="rider-list">';
      pool.riders.forEach(r => {
        html += `
          <div class="rider-item">
            <div>
              <span class="rider-name">${r.passengerName || 'Rider'}</span>
              <span class="rider-meta"> • ${r.passengerCount} pax, ${r.luggageCount} bags</span>
            </div>
            <div>
              ${r.estimatedPrice ? `<strong style="color:var(--success)">₹${r.estimatedPrice.toFixed(0)}</strong>` : '<span style="color:var(--text-muted)">Pending</span>'}
              ${badge(r.status)}
            </div>
          </div>
        `;
      });
      html += '</div>';
    }
  }

  // Cancel button
  if (ride.status !== 'COMPLETED' && ride.status !== 'CANCELLED') {
    html += `<button class="btn btn-danger" style="margin-top:16px" onclick="window.cancelActiveRide('${ride.id}')">✕ Cancel Ride</button>`;
  }

  // Your ride details
  html += `
    <div style="margin-top:12px;font-size:12px;color:var(--text-muted)">
      Your ride: ${ride.id.substring(0,8)}… • Status: ${badge(ride.status)}
      ${ride.estimatedPrice ? ` • Fare: <strong style="color:var(--success)">₹${ride.estimatedPrice.toFixed(0)}</strong>` : ''}
    </div>
  `;

  panel.innerHTML = html;
}

// Cancel ride global handler
window.cancelActiveRide = async function(rideId) {
  try {
    await api.cancelRide(rideId, 'Cancelled by passenger from dashboard');
    showToast('Ride cancelled', 'warning');
    refreshPassengerDashboard();
  } catch (e) {
    showToast(`Cancel failed: ${e.message}`, 'error');
  }
};

function renderRideHistory(rides) {
  const panel = document.getElementById('rideHistory');

  if (!rides || rides.length === 0) {
    panel.innerHTML = '<div class="empty-state">No rides yet</div>';
    return;
  }

  // Filter to only non-active rides for history
  const history = rides.filter(r => r.status === 'COMPLETED' || r.status === 'CANCELLED');

  if (history.length === 0) {
    panel.innerHTML = '<div class="empty-state">No completed rides yet</div>';
    return;
  }

  let html = `<table class="data-table">
    <thead><tr><th>Date</th><th>Status</th><th>Pickup</th><th>Drop</th><th>Fare</th></tr></thead>
    <tbody>`;

  history.slice(0, 20).forEach(r => {
    const date = r.createdAt ? new Date(r.createdAt).toLocaleString() : '-';
    html += `<tr>
      <td>${date}</td>
      <td>${badge(r.status)}</td>
      <td>${resolveLocationName(r.pickupLat, r.pickupLng)}</td>
      <td>${resolveLocationName(r.dropLat, r.dropLng)}</td>
      <td>${r.estimatedPrice ? `₹${r.estimatedPrice.toFixed(0)}` : '-'}</td>
    </tr>`;
  });

  html += '</tbody></table>';
  panel.innerHTML = html;
}

// ─── Nearby Available Cabs ────────────────
async function refreshNearbyCabs() {
  const panel = document.getElementById('nearbyCabsPanel');
  if (!panel) return;

  const pickup = getSelectedLocation('bookPickupLoc');
  if (!pickup) {
    panel.innerHTML = '<div class="empty-state">Select a pickup location to see nearby cabs</div>';
    return;
  }

  try {
    const cabs = await api.getAllCabs();
    const available = cabs.filter(c => c.status === 'AVAILABLE');

    if (available.length === 0) {
      panel.innerHTML = '<div class="empty-state">No available cabs right now</div>';
      return;
    }

    // Calculate distance from pickup and sort
    const withDist = available.map(c => {
      const dLat = c.currentLat - pickup.lat;
      const dLng = c.currentLng - pickup.lng;
      const distKm = Math.sqrt(dLat * dLat + dLng * dLng) * 111.32; // rough km
      return { ...c, distKm };
    }).sort((a, b) => a.distKm - b.distKm);

    // Show nearby cabs (within 10km)
    const nearby = withDist.filter(c => c.distKm <= 10);

    if (nearby.length === 0) {
      panel.innerHTML = '<div class="empty-state">No cabs available within 10km of pickup</div>';
      return;
    }

    let html = `<div class="nearby-cabs-list">`;
    nearby.forEach(c => {
      html += `
        <div class="nearby-cab-item">
          <div class="cab-main-info">
            <span class="cab-driver">🚗 ${c.driverName}</span>
            <span class="cab-plate">${c.licensePlate}</span>
          </div>
          <div class="cab-sub-info">
            <span>📍 ${resolveLocationName(c.currentLat, c.currentLng)}</span>
            <span class="cab-distance">${c.distKm.toFixed(1)} km away</span>
          </div>
          <div class="cab-capacity">
            <span>💺 ${c.remainingSeats ?? c.totalSeats} seats</span>
            <span>🧳 ${c.remainingLuggage ?? c.luggageCapacity} luggage</span>
          </div>
        </div>`;
    });
    html += '</div>';
    panel.innerHTML = html;
  } catch (e) {
    panel.innerHTML = `<div class="empty-state">Error: ${e.message}</div>`;
  }
}

// ─── Countdown Timer ──────────────────────
function startCountdown(expiresAt) {
  clearCountdown();

  state.countdownInterval = setInterval(() => {
    const now = new Date();
    const secs = Math.max(0, Math.floor((expiresAt - now) / 1000));
    const el = document.getElementById('countdownValue');
    if (el) el.textContent = `${secs}s`;

    const progress = document.querySelector('.countdown-ring .progress');
    if (progress) {
      const circumference = 2 * Math.PI * 17;
      progress.setAttribute('stroke-dashoffset', circumference * (1 - secs / 60));
    }

    if (secs <= 0) {
      clearCountdown();
      refreshPassengerDashboard();
    }
  }, 1000);
}

function clearCountdown() {
  if (state.countdownInterval) {
    clearInterval(state.countdownInterval);
    state.countdownInterval = null;
  }
}

// ─── Driver Dashboard ─────────────────────
async function refreshDriverDashboard() {
  if (!state.selectedCab) return;

  try {
    const data = await api.getDriverDashboard(state.selectedCab);
    renderDriverAssignment(data);

    // Also refresh driver info card
    const cab = state.cabs.find(c => c.id === state.selectedCab);
    renderDriverInfo(cab);
  } catch (e) {
    console.error('Driver dashboard refresh failed:', e);
  }
}

function renderDriverAssignment(data) {
  const panel = document.getElementById('driverAssignment');
  const btnStart = document.getElementById('btnStartTrip');
  const btnComplete = document.getElementById('btnCompleteRide');

  if (!data.activePool) {
    panel.innerHTML = '<div class="empty-state">No active trip. Waiting for dispatch…</div>';
    btnStart.disabled = true;
    btnComplete.disabled = true;
    return;
  }

  const pool = data.activePool;

  // Enable buttons based on pool status
  btnStart.disabled = pool.status !== 'CONFIRMED';
  btnComplete.disabled = pool.status !== 'IN_PROGRESS';

  let html = `
    <div class="pool-info">
      <div class="pool-stat">
        <div class="value">${pool.totalOccupiedSeats}</div>
        <div class="label">Riders</div>
      </div>
      <div class="pool-stat">
        <div class="value">${pool.totalLuggage}</div>
        <div class="label">Luggage</div>
      </div>
      <div class="pool-stat">
        <div class="value">${pool.totalRouteDistanceKm?.toFixed(1)} km</div>
        <div class="label">Route</div>
      </div>
      <div class="pool-stat">
        <div class="value">${badge(pool.status)}</div>
        <div class="label">Status</div>
      </div>
    </div>
  `;

  // Rider details
  if (pool.riders && pool.riders.length > 0) {
    html += '<h3 style="margin:16px 0 8px;font-size:14px;color:var(--text-secondary)">Riders</h3>';
    html += '<div class="rider-list">';
    pool.riders.forEach(r => {
      html += `
        <div class="rider-item">
          <div>
            <span class="rider-name">${r.passengerName || 'Rider'}</span>
            <span class="rider-meta"> • ${r.passengerCount} pax, ${r.luggageCount} bags</span>
          </div>
          <div>
            <span style="color:var(--text-muted);font-size:11px">
              📍 ${resolveLocationName(r.pickupLat, r.pickupLng)} → ${resolveLocationName(r.dropLat, r.dropLng)}
            </span>
          </div>
        </div>
      `;
    });
    html += '</div>';
  }

  panel.innerHTML = html;
}

function renderDriverInfo(cab) {
  const panel = document.getElementById('driverInfo');

  if (!cab) {
    panel.innerHTML = '<div class="empty-state">Select a driver</div>';
    return;
  }

  panel.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:10px">
      <div style="display:flex;justify-content:space-between">
        <span style="color:var(--text-muted)">Name</span>
        <strong>${cab.driverName}</strong>
      </div>
      <div style="display:flex;justify-content:space-between">
        <span style="color:var(--text-muted)">License</span>
        <strong>${cab.licensePlate}</strong>
      </div>
      <div style="display:flex;justify-content:space-between">
        <span style="color:var(--text-muted)">Status</span>
        ${badge(cab.status)}
      </div>
      <div style="display:flex;justify-content:space-between">
        <span style="color:var(--text-muted)">Seats</span>
        <strong>${cab.totalSeats}</strong>
      </div>
      <div style="display:flex;justify-content:space-between">
        <span style="color:var(--text-muted)">Location</span>
        <span>${resolveLocationName(cab.currentLat, cab.currentLng)}</span>
      </div>
    </div>
  `;
}

// ─── Driver Actions ───────────────────────
async function handleStartTrip() {
  if (!state.selectedCab) return;

  try {
    document.getElementById('btnStartTrip').disabled = true;
    await api.startTrip(state.selectedCab);
    showToast('Trip started! 🚗', 'success');
    refreshDriverDashboard();

    // Refresh cabs list to update status
    state.cabs = await api.getAllCabs();
    populateDriverSelect(state.cabs);
  } catch (e) {
    showToast(`Error: ${e.message}`, 'error');
    document.getElementById('btnStartTrip').disabled = false;
  }
}

async function handleCompleteRide() {
  if (!state.selectedCab) return;

  try {
    document.getElementById('btnCompleteRide').disabled = true;
    await api.completeRide(state.selectedCab);
    showToast('Ride completed! ✅', 'success');
    refreshDriverDashboard();

    state.cabs = await api.getAllCabs();
    populateDriverSelect(state.cabs);
  } catch (e) {
    showToast(`Error: ${e.message}`, 'error');
    document.getElementById('btnCompleteRide').disabled = false;
  }
}

// ─── Admin View ───────────────────────────
async function refreshAdmin() {
  try {
    const [cabs, rides] = await Promise.all([
      api.getAllCabs(),
      api.getAllRides(),
    ]);

    state.cabs = cabs;

    // Stats
    const available = cabs.filter(c => c.status === 'AVAILABLE').length;
    const assigned = cabs.filter(c => c.status === 'ASSIGNED').length;
    const onTrip = cabs.filter(c => c.status === 'ON_TRIP').length;
    const activeRides = rides.filter(r => ['PENDING', 'POOLED', 'CONFIRMED', 'IN_PROGRESS'].includes(r.status)).length;
    const completed = rides.filter(r => r.status === 'COMPLETED').length;

    document.getElementById('adminStats').innerHTML = `
      <div class="stat-card stat-available"><div class="stat-value">${available}</div><div class="stat-label">Available Cabs</div></div>
      <div class="stat-card stat-forming"><div class="stat-value">${assigned}</div><div class="stat-label">Assigned Cabs</div></div>
      <div class="stat-card stat-progress"><div class="stat-value">${onTrip}</div><div class="stat-label">On Trip</div></div>
      <div class="stat-card stat-confirmed"><div class="stat-value">${activeRides}</div><div class="stat-label">Active Rides</div></div>
      <div class="stat-card stat-total"><div class="stat-value">${completed}</div><div class="stat-label">Completed</div></div>
    `;

    // Cabs table
    let cabsHtml = `<table class="data-table">
      <thead><tr><th>Driver</th><th>Plate</th><th>Status</th><th>Seats</th><th>Location</th></tr></thead>
      <tbody>`;

    cabs.forEach(c => {
      cabsHtml += `<tr>
        <td><strong>${c.driverName}</strong></td>
        <td>${c.licensePlate}</td>
        <td>${badge(c.status)}</td>
        <td>${c.remainingSeats ?? c.totalSeats}/${c.totalSeats}</td>
        <td>${resolveLocationName(c.currentLat, c.currentLng)}</td>
      </tr>`;
    });
    cabsHtml += '</tbody></table>';
    document.getElementById('adminCabs').innerHTML = cabsHtml;

    // Rides table
    let ridesHtml = `<table class="data-table">
      <thead><tr><th>Passenger</th><th>Status</th><th>Pickup</th><th>Drop</th><th>Fare</th><th>Date</th></tr></thead>
      <tbody>`;

    rides.slice(0, 50).forEach(r => {
      ridesHtml += `<tr>
        <td>${r.passengerName || r.passengerId?.substring(0, 8)}</td>
        <td>${badge(r.status)}</td>
        <td>${resolveLocationName(r.pickupLat, r.pickupLng)}</td>
        <td>${resolveLocationName(r.dropLat, r.dropLng)}</td>
        <td>${r.estimatedPrice ? `₹${r.estimatedPrice.toFixed(0)}` : '-'}</td>
        <td>${r.createdAt ? new Date(r.createdAt).toLocaleString() : '-'}</td>
      </tr>`;
    });
    ridesHtml += '</tbody></table>';
    document.getElementById('adminRides').innerHTML = ridesHtml;

    // Pools — get unique pools from rides
    const poolIds = [...new Set(rides.filter(r => r.ridePoolId).map(r => r.ridePoolId))];
    let poolsHtml = '';

    if (poolIds.length === 0) {
      poolsHtml = '<div class="empty-state">No pools yet</div>';
    } else {
      poolsHtml = `<table class="data-table">
        <thead><tr><th>Pool ID</th><th>Riders</th><th>Status</th></tr></thead>
        <tbody>`;

      // Group rides by pool
      const poolMap = new Map();
      rides.forEach(r => {
        if (r.ridePoolId) {
          if (!poolMap.has(r.ridePoolId)) poolMap.set(r.ridePoolId, []);
          poolMap.get(r.ridePoolId).push(r);
        }
      });

      for (const [poolId, poolRides] of poolMap) {
        const active = poolRides.filter(r => r.status !== 'CANCELLED');
        const statuses = [...new Set(active.map(r => r.status))];
        poolsHtml += `<tr>
          <td><code>${poolId.substring(0, 8)}…</code></td>
          <td>${active.length} riders</td>
          <td>${statuses.map(s => badge(s)).join(' ')}</td>
        </tr>`;
      }

      poolsHtml += '</tbody></table>';
    }
    document.getElementById('adminPools').innerHTML = poolsHtml;

  } catch (e) {
    console.error('Admin refresh failed:', e);
  }
}

// ─── Utilities ────────────────────────────
function badge(status) {
  const s = (status || 'unknown').toLowerCase().replace('_', '-');
  return `<span class="badge badge-${s}">${status}</span>`;
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(30px)';
    toast.style.transition = '0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

function updateStatus(text, connected) {
  document.getElementById('statusText').textContent = text;
  const pulse = document.querySelector('.pulse');
  pulse.style.background = connected ? 'var(--success)' : 'var(--danger)';
}
