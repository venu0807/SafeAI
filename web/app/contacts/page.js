'use client'

import { useState, useEffect } from 'react'
import { Users, Phone, UserPlus, ShieldAlert } from 'lucide-react'
import { database } from '@/lib/firebase'
import { ref, onValue } from 'firebase/database'

export default function ContactsPage() {
  const [contacts, setContacts] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const contactsRef = ref(database, 'emergency_contacts')
    const unsubscribe = onValue(contactsRef, (snapshot) => {
      const data = snapshot.val()
      if (data) {
        const parsed = Object.keys(data).map(key => ({
          id: key,
          name: data[key].name || 'Unknown',
          phone: data[key].phone || '',
          relationship: data[key].relationship || 'Contact'
        }))
        parsed.sort((a, b) => a.name.localeCompare(b.name))
        setContacts(parsed)
      }
      setLoading(false)
    }, (error) => {
      console.error('Firebase error:', error)
      setLoading(false)
    })

    return () => unsubscribe()
  }, [])

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <div>
          <h1>Emergency Contacts</h1>
          <p className="subtitle">Who gets notified when a threat is detected</p>
        </div>
        <div className="status-indicator">
          <Users size={18} />
          <span>{contacts.length} Contact{contacts.length !== 1 ? 's' : ''}</span>
        </div>
      </header>

      <div className="section-header">
        <h2>Your Trusted Contacts</h2>
      </div>

      {contacts.length === 0 ? (
        <div className="glass-panel card" style={{ textAlign: 'center', padding: '3rem' }}>
          <UserPlus size={48} style={{ color: 'var(--text-muted)', marginBottom: '1rem' }} />
          <h2>No Contacts Added</h2>
          <p style={{ color: 'var(--text-muted)' }}>Add emergency contacts from the mobile app.</p>
        </div>
      ) : (
        <div className="alerts-list">
          {contacts.map((contact) => (
            <div key={contact.id} className="glass-panel card alert-card">
              <div className="alert-indicator safe"></div>
              <div className="alert-content">
                <div className="alert-header">
                  <h3>{contact.name}</h3>
                  <span className="badge badge-safe">{contact.relationship}</span>
                </div>
                <div className="alert-details">
                  <span style={{ display: 'flex', alignItems: 'center', gap: '0.375rem' }}>
                    <Phone size={14} /> {contact.phone}
                  </span>
                </div>
              </div>
              <div className="alert-actions">
                <a href={`tel:${contact.phone.replace(/[^0-9+]/g, '')}`} className="btn btn-primary">
                  <Phone size={16} /> Call
                </a>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="glass-panel card" style={{ marginTop: '2rem', padding: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1rem' }}>
          <ShieldAlert size={24} style={{ color: 'var(--primary-blue)', flexShrink: 0, marginTop: '0.25rem' }} />
          <div>
            <h3 style={{ marginBottom: '0.5rem' }}>How It Works</h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', lineHeight: 1.6 }}>
              When SafeGuard AI detects a verified threat, SMS alerts with your GPS location 
              and a Google Maps link are sent to all emergency contacts simultaneously. 
              Add or update contacts directly from the SafeGuard AI mobile app.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
