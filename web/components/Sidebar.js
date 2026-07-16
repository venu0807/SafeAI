'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Home, ShieldAlert, Users, Settings, LogOut } from 'lucide-react'
import './Sidebar.css'

export default function Sidebar() {
  const pathname = usePathname()

  const navItems = [
    { name: 'Dashboard', path: '/', icon: Home },
    { name: 'Incident History', path: '/history', icon: ShieldAlert },
    { name: 'Emergency Contacts', path: '/contacts', icon: Users },
    { name: 'Settings', path: '/settings', icon: Settings },
  ]

  return (
    <aside className="sidebar glass-panel">
      <div className="sidebar-header">
        <div className="logo-container">
          <ShieldAlert className="logo-icon" size={28} color="var(--primary-blue)" />
          <h2>SafeGuard AI</h2>
        </div>
        <p className="subtitle">Web Dashboard</p>
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => {
          const Icon = item.icon
          const isActive = pathname === item.path
          
          return (
            <Link 
              key={item.path} 
              href={item.path}
              className={`nav-item ${isActive ? 'active' : ''}`}
            >
              <Icon size={20} />
              <span>{item.name}</span>
            </Link>
          )
        })}
      </nav>

      <div className="sidebar-footer">
        <button className="nav-item logout-btn">
          <LogOut size={20} />
          <span>Logout</span>
        </button>
      </div>
    </aside>
  )
}
