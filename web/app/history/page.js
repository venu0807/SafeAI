'use client'

import { useState, useEffect } from 'react'
import { ShieldAlert, Clock, MapPin, Trash2 } from 'lucide-react'
import { database } from '@/lib/firebase'
import { ref, onValue } from 'firebase/database'

export default function HistoryPage() {
  const [incidents, setIncidents] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const threatsRef = ref(database, 'threats')
    const unsubscribe = onValue(threatsRef, (snapshot) => {
      const data = snapshot.val()
      if (data) {
        const parsed = Object.keys(data).map(key => ({
          id: key,
          type: data[key].type || 'Audio Threat',
          confidence: Math.round((data[key].confidence || 0) * 100) + '%',
          timestamp: data[key].timestamp || Date.now(),
          status: data[key].status || 'critical',
          latitude: data[key].latitude || 0,
          longitude: data[key].longitude || 0
        }))
        parsed.sort((a, b) => b.timestamp - a.timestamp)
        setIncidents(parsed)
      }
      setLoading(false)
    }, (error) => {
      console.error('Firebase error:', error)
      setLoading(false)
    })

    return () => unsubscribe()
  }, [])

  const formatTime = (ts) => {
    const date = new Date(ts)
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString()
  }

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <div>
          <h1>Incident History</h1>
          <p className="subtitle">Past threat events detected by SafeGuard AI</p>
        </div>
        <div className="status-indicator">
          <ShieldAlert size={18} />
          <span>{incidents.length} Total Incidents</span>
        </div>
      </header>

      {loading ? (
        <div className="glass-panel card" style={{ textAlign: 'center', padding: '3rem' }}>
          <p style={{ color: 'var(--text-muted)' }}>Loading incident data...</p>
        </div>
      ) : incidents.length === 0 ? (
        <div className="glass-panel card" style={{ textAlign: 'center', padding: '3rem' }}>
          <ShieldAlert size={48} style={{ color: 'var(--success-green)', marginBottom: '1rem' }} />
          <h2>No Incidents Recorded</h2>
          <p style={{ color: 'var(--text-muted)' }}>Your SafeGuard AI protection is running smoothly.</p>
        </div>
      ) : (
        <div className="alerts-list">
          {incidents.map((incident) => (
            <div key={incident.id} className="glass-panel card alert-card">
              <div className={`alert-indicator ${incident.status}`}></div>
              <div className="alert-content">
                <div className="alert-header">
                  <h3>{incident.type}</h3>
                  <span className={`badge badge-${incident.status}`}>
                    {incident.confidence} Match
                  </span>
                </div>
                <div className="alert-details">
                  <span className="time">
                    <Clock size={14} /> {formatTime(incident.timestamp)}
                  </span>
                  <span className="location">
                    <MapPin size={14} /> {incident.latitude.toFixed(4)}° N, {incident.longitude.toFixed(4)}° W
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
