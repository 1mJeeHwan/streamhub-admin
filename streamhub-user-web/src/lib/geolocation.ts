// Browser geolocation helper with a deterministic fallback. No external API keys.

export interface Coords {
  lat: number;
  lng: number;
}

/** Seoul City Hall — fallback when the user denies/lacks geolocation. */
export const SEOUL_CITY_HALL: Coords = { lat: 37.5663, lng: 126.9779 };

export interface PositionResult {
  coords: Coords;
  /** false when we fell back (permission denied, timeout, or unsupported). */
  granted: boolean;
}

/**
 * Resolve the user's current position. Never rejects — on any failure it
 * resolves with the Seoul City Hall fallback and `granted: false`, so the
 * caller can show a "using default location" notice.
 */
export function getCurrentPosition(timeoutMs = 8000): Promise<PositionResult> {
  return new Promise((resolve) => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      resolve({ coords: SEOUL_CITY_HALL, granted: false });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) =>
        resolve({
          coords: { lat: pos.coords.latitude, lng: pos.coords.longitude },
          granted: true,
        }),
      () => resolve({ coords: SEOUL_CITY_HALL, granted: false }),
      { enableHighAccuracy: false, timeout: timeoutMs, maximumAge: 60_000 },
    );
  });
}
