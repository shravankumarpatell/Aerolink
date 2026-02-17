/**
 * AeroLink — Mumbai Location Map
 * Maps human-readable location names to lat/lng coordinates.
 * Used in the frontend dropdowns; backend always receives raw lat/lng.
 */

const LOCATIONS = [
  // ── Airport Zone ──────────────────────
  { name: '✈️ Mumbai Airport — Terminal 2 (International)', lat: 19.0896, lng: 72.8656, zone: 'Airport' },
  { name: '✈️ Mumbai Airport — Terminal 1 (Domestic)',      lat: 19.0990, lng: 72.8740, zone: 'Airport' },
  { name: '✈️ Airport — Departure Drop-off',               lat: 19.0905, lng: 72.8665, zone: 'Airport' },
  { name: '✈️ Airport — Arrival Pickup Zone',              lat: 19.0885, lng: 72.8645, zone: 'Airport' },

  // ── Western Suburbs ───────────────────
  { name: '🚉 Andheri Station (West)',                      lat: 19.1197, lng: 72.8464, zone: 'Western Suburbs' },
  { name: '🚉 Andheri Station (East)',                      lat: 19.1190, lng: 72.8530, zone: 'Western Suburbs' },
  { name: '🏢 Andheri — Lokhandwala Complex',               lat: 19.1380, lng: 72.8270, zone: 'Western Suburbs' },
  { name: '🚉 Vile Parle Station',                          lat: 19.1010, lng: 72.8440, zone: 'Western Suburbs' },
  { name: '🚉 Santacruz Station',                           lat: 19.0840, lng: 72.8410, zone: 'Western Suburbs' },
  { name: '🚉 Bandra Station',                              lat: 19.0544, lng: 72.8403, zone: 'Western Suburbs' },
  { name: '🌊 Bandra — Bandstand',                          lat: 19.0425, lng: 72.8190, zone: 'Western Suburbs' },
  { name: '🌉 Bandra-Worli Sea Link (Bandra End)',          lat: 19.0460, lng: 72.8180, zone: 'Western Suburbs' },
  { name: '🚉 Goregaon Station',                            lat: 19.1663, lng: 72.8490, zone: 'Western Suburbs' },
  { name: '🏢 Goregaon — Oberoi Mall',                      lat: 19.1755, lng: 72.8562, zone: 'Western Suburbs' },
  { name: '🚉 Malad Station',                               lat: 19.1866, lng: 72.8484, zone: 'Western Suburbs' },
  { name: '🚉 Borivali Station',                            lat: 19.2300, lng: 72.8567, zone: 'Western Suburbs' },
  { name: '🚉 Jogeshwari Station',                          lat: 19.1365, lng: 72.8490, zone: 'Western Suburbs' },
  { name: '🚉 Juhu Beach',                                  lat: 19.0948, lng: 72.8267, zone: 'Western Suburbs' },

  // ── South Mumbai ──────────────────────
  { name: '🏛️ CST (Chhatrapati Shivaji Terminus)',          lat: 18.9398, lng: 72.8354, zone: 'South Mumbai' },
  { name: '🚉 Churchgate Station',                          lat: 18.9352, lng: 72.8274, zone: 'South Mumbai' },
  { name: '🌊 Marine Drive',                                lat: 18.9442, lng: 72.8234, zone: 'South Mumbai' },
  { name: '🏛️ Gateway of India',                            lat: 18.9220, lng: 72.8347, zone: 'South Mumbai' },
  { name: '🏙️ Nariman Point',                               lat: 18.9257, lng: 72.8242, zone: 'South Mumbai' },
  { name: '🚉 Dadar Station',                               lat: 19.0178, lng: 72.8448, zone: 'South Mumbai' },
  { name: '🚉 Lower Parel',                                 lat: 19.0048, lng: 72.8310, zone: 'South Mumbai' },
  { name: '🏙️ Worli',                                       lat: 19.0160, lng: 72.8150, zone: 'South Mumbai' },

  // ── Business Hubs ─────────────────────
  { name: '🏢 BKC (Bandra-Kurla Complex)',                   lat: 19.0668, lng: 72.8712, zone: 'Business Hub' },
  { name: '🏢 Powai — Hiranandani Gardens',                  lat: 19.1188, lng: 72.9083, zone: 'Business Hub' },
  { name: '🏢 SEEPZ — Andheri East',                         lat: 19.1248, lng: 72.8720, zone: 'Business Hub' },
  { name: '🏢 Mindspace — Malad West',                       lat: 19.1878, lng: 72.8366, zone: 'Business Hub' },

  // ── Navi Mumbai ───────────────────────
  { name: '🚉 Panvel Station',                               lat: 18.9935, lng: 73.1139, zone: 'Navi Mumbai' },
  { name: '🏙️ Vashi — Inorbit Mall',                         lat: 19.0635, lng: 72.9988, zone: 'Navi Mumbai' },
  { name: '🚉 Nerul Station',                                lat: 19.0341, lng: 73.0157, zone: 'Navi Mumbai' },

  // ── Hotels ────────────────────────────
  { name: '🏨 The Lalit — Airport',                          lat: 19.0940, lng: 72.8575, zone: 'Hotels' },
  { name: '🏨 ITC Maratha — Andheri East',                   lat: 19.1075, lng: 72.8685, zone: 'Hotels' },
  { name: '🏨 Taj Lands End — Bandra',                       lat: 19.0435, lng: 72.8196, zone: 'Hotels' },
  { name: '🏨 Trident — BKC',                                lat: 19.0641, lng: 72.8685, zone: 'Hotels' },
  { name: '🏨 Taj Mahal Palace — Colaba',                    lat: 18.9217, lng: 72.8332, zone: 'Hotels' },
];

/**
 * Build an <option> list grouped by zone.
 * @param {string} selectId  The DOM id of the <select> to populate
 * @param {string} defaultValue  Optional default location name
 */
export function populateLocationSelect(selectId, defaultValue) {
  const sel = document.getElementById(selectId);
  if (!sel) return;

  sel.innerHTML = '<option value="">— Select a location —</option>';

  // Group by zone
  const zones = {};
  LOCATIONS.forEach(loc => {
    if (!zones[loc.zone]) zones[loc.zone] = [];
    zones[loc.zone].push(loc);
  });

  for (const [zone, locations] of Object.entries(zones)) {
    const group = document.createElement('optgroup');
    group.label = zone;
    locations.forEach(loc => {
      const opt = document.createElement('option');
      opt.value = JSON.stringify({ lat: loc.lat, lng: loc.lng });
      opt.textContent = loc.name;
      if (defaultValue && loc.name.includes(defaultValue)) opt.selected = true;
      group.appendChild(opt);
    });
    sel.appendChild(group);
  }
}

/**
 * Parse a location select's value into { lat, lng }.
 * @param {string} selectId  The DOM id of the <select>
 * @returns {{ lat: number, lng: number } | null}
 */
export function getSelectedLocation(selectId) {
  const sel = document.getElementById(selectId);
  if (!sel || !sel.value) return null;
  try {
    return JSON.parse(sel.value);
  } catch {
    return null;
  }
}

/**
 * Resolve lat/lng to the nearest named location (for display).
 * @param {number} lat
 * @param {number} lng
 * @returns {string}
 */
export function resolveLocationName(lat, lng) {
  if (lat == null || lng == null) return 'Unknown';

  let closest = null;
  let minDist = Infinity;

  LOCATIONS.forEach(loc => {
    const d = Math.pow(loc.lat - lat, 2) + Math.pow(loc.lng - lng, 2);
    if (d < minDist) {
      minDist = d;
      closest = loc;
    }
  });

  // Only match if within ~1km (approx 0.01 degrees)
  if (closest && minDist < 0.0001) {
    return closest.name;
  }

  return `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
}

export { LOCATIONS };
