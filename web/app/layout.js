import './globals.css'
import Sidebar from '@/components/Sidebar'

export const metadata = {
  title: 'SafeGuard AI | Web Dashboard',
  description: 'Manage your emergency settings and review threat incidents remotely.',
}

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <div className="app-container">
          <Sidebar />
          <main className="main-content">
            {children}
          </main>
        </div>
      </body>
    </html>
  )
}
