/**
 * pasService — polls the PAS Connector's plain-HTTP port for live
 * MQTT-derived device status (connected/disconnected based on the last
 * heartbeat timestamp).  Separate from apiService because PAS Connector
 * runs on port 8444 instead of Spring Boot's 8080.
 */

const PAS_URL = import.meta.env.VITE_PAS_URL || 'http://localhost:8444';

export async function fetchPasDeviceStatus() {
  try {
    const res = await fetch(`${PAS_URL}/api/pas/devices/status`, {
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

export async function fetchPasRecentEvents() {
  try {
    const res = await fetch(`${PAS_URL}/api/pas/events/recent`, {
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}
