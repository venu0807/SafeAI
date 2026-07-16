'use client'

import { useState } from 'react'
import { Settings, Shield, Bell, MapPin, Smartphone } from 'lucide-react'

export default function SettingsPage() {
  const [notifications, setNotifications] = useState(true)

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <div>
          <h1>Settings</h1>
          <p className="subtitle">Configure your SafeGuard AI preferences</p>
        </div>
      </header>

      <div className="alerts-list" style={{ maxWidth: '600px' }}>
        <div className="glass-panel card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <div className="metric-icon blue">
              <Bell size={24} />
            </div>
            <div style={{ flex: 1 }}>
              <h3>Web Notifications</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>
                Receive browser notifications when a threat is detected
              </p>
            </div>
            <button
              onClick={() => setNotifications(!notifications)}
              style={{
                width: '48px',
                height: '28px',
                borderRadius: '14px',
                border: 'none',
                background: notifications ? 'var(--primary-blue)' : 'var(--surface-border)',
                cursor: 'pointer',
                position: 'relative',
                transition: 'background var(--transition-fast)'
              }}
            >
              <span style={{
                position: 'absolute',
                top: '3px',
                left: notifications ? '23px' : '3px',
                width: '22px',
                height: '22px',
                borderRadius: '50%',
                background: 'white',
                transition: 'left var(--transition-fast)',
                boxShadow: '0 1px 3px rgba(0,0,0,0.3)'
              }} />
            </button>
          </div>
        </div>

        <div className="glass-panel card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <div className="metric-icon green">
              <MapPin size={24} />
            </div>
            <div>
              <h3>Location Sharing</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>
                GPS data is processed on-device. Only shared with contacts during emergencies.
              </p>
            </div>
          </div>
        </div>

        <div className="glass-panel card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <div className="metric-icon" style={{ background: 'rgba(245, 124, 0, 0.15)', color: 'var(--warning-orange)' }}>
              <Shield size={24} />
            </div>
            <div>
              <h3>Privacy Mode</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>
                All ML inference runs locally on-device. No audio or video data is ever uploaded to the cloud.
              </p>
            </div>
          </div>
        </div>

        <div className="glass-panel card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <div className="metric-icon blue">
              <Smartphone size={24} />
            </div>
            <div>
              <h3>Mobile App</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>
                Detection sensitivity, safe word, duress PIN, and emergency contacts are managed in the SafeGuard AI mobile app.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
