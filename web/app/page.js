'use client'

import { useState, useEffect } from 'react'
import { ShieldAlert, Activity, Navigation } from 'lucide-react'
import { database } from '@/lib/firebase'
import { ref, onValue } from 'firebase/database'
import './dashboard.css'

export default function Dashboard() {
  const [alerts, setAlerts] = useState([])
  const [activeThreats, setActiveThreats] = useState(0)

  useEffect(() => {
    const threatsRef = ref(database, 'threats')
    const unsubscribe = onValue(threatsRef, (snapshot) => {
      const data = snapshot.val()
      if (data) {
        const parsedAlerts = Object.keys(data).map(key => ({
          id: key,
          type: data[key].type || 'Audio Threat',
          confidence: Math.round((data[key].confidence || 0) * 100) + '%',
          time: new Date(data[key].timestamp || Date.now()).toLocaleTimeString(),
          status: data[key].status || 'critical',
          location: `${(data[key].latitude || 0).toFixed(4)}° N, ${(data[key].longitude || 0).toFixed(4)}° W`
        }))
        // Sort by timestamp if possible, otherwise reverse
        parsedAlerts.reverse()
        setAlerts(parsedAlerts.slice(0, 10))
        setActiveThreats(parsedAlerts.filter(a => a.status === 'critical').length)
      }
    })

    return () => unsubscribe()
  }, [])

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <div>
          <h1>Dashboard Overview</h1>
          <p className="subtitle">System Status: Monitoring Active</p>
        </div>
        <div className="status-indicator">
          <div className="pulse-dot"></div>
          <span>SafeGuard Online</span>
        </div>
      </header>

      <section className="metrics-grid">
        <div className="glass-panel card metric-card">
          <div className="metric-icon blue">
            <Activity size={24} />
          </div>
          <div className="metric-info">
            <h3>24</h3>
            <p>Events Monitored (24h)</p>
          </div>
        </div>
        <div className="glass-panel card metric-card">
          <div className={`metric-icon ${activeThreats > 0 ? 'red animate-pulse-danger' : 'green'}`}>
            <ShieldAlert size={24} />
          </div>
          <div className="metric-info">
            <h3>{activeThreats}</h3>
            <p>Active Alert</p>
          </div>
        </div>
        <div className="glass-panel card metric-card">
          <div className="metric-icon green">
            <Navigation size={24} />
          </div>
          <div className="metric-info">
            <h3>Tracking</h3>
            <p>Location Services</p>
          </div>
        </div>
      </section>

      <section className="alerts-section">
        <div className="section-header">
          <h2>Recent Alerts</h2>
          <button className="btn btn-ghost">View All</button>
        </div>
        
        <div className="alerts-list">
          {alerts.map(alert => (
            <div key={alert.id} className="glass-panel card alert-card">
              <div className={`alert-indicator ${alert.status}`}></div>
              <div className="alert-content">
                <div className="alert-header">
                  <h3>{alert.type}</h3>
                  <span className={`badge badge-${alert.status}`}>
                    {alert.confidence} Match
                  </span>
                </div>
                <div className="alert-details">
                  <span className="time">{alert.time}</span>
                  <span className="location">{alert.location}</span>
                </div>
              </div>
              <div className="alert-actions">
                {alert.status === 'critical' && (
                  <button className="btn btn-danger">Verify Status</button>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
