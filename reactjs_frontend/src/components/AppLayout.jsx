import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import styles from './AppLayout.module.css';

const navTopItems = [
  { label: 'Offerings', route: '/offerings' },
  { label: 'Chronic Disease Management', route: '/offerings' },
  { label: 'AI Voice Bot', route: '/offerings' },
  { label: 'Clinical Decision Support', route: '/offerings' },
  { label: 'DICOM Imaging Intelligence', route: '/dicom' },
  { label: 'View All (13)', route: '/offerings' },
];

const sidebarItems = [
  { label: 'Dashboard',    icon: DashboardIcon, route: '/offerings' },
  { label: 'PACS Devices', icon: DevicesIcon,   route: '/dicom' },
  { label: 'Subjects',     icon: SubjectsIcon,  route: '/dicom/subjects-overview' },  // NEW
  { label: 'Studies',      icon: StudiesIcon,   route: '/dicom/studies-overview' },
  { label: 'Settings',     icon: SettingsIcon,  route: '/offerings' },
];

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);

  const isActive = (item) => {
    if (item.route === '/dicom' && item.label === 'PACS Devices') {
      return location.pathname === '/dicom' || location.pathname.startsWith('/dicom/device');
    }
    if (item.label === 'Subjects') {
      return location.pathname.includes('/subjects') || location.pathname.includes('/subject/');
    }
    if (item.label === 'Studies') {
      return location.pathname.includes('/studies') || location.pathname.includes('/studies/');
    }
    if (item.route !== '/offerings') {
      return location.pathname.startsWith(item.route);
    }
    return location.pathname === item.route && item.label === 'Dashboard';
  };

  return (
    <div className={styles.layout}>
      {/* Sidebar */}
      <aside className={`${styles.sidebar} ${collapsed ? styles.collapsed : ''}`}>
        <div className={styles.logo}>
          <span className={styles.logoIcon}>💙</span>
          {!collapsed && <span className={styles.logoText}><b>Quantix</b>Med</span>}
        </div>

        <nav className={styles.nav}>
          {sidebarItems.map((item) => {
            const Icon = item.icon;
            const active = isActive(item);
            return (
              <button
                key={item.label}
                className={`${styles.navItem} ${active ? styles.active : ''}`}
                onClick={() => {
                  if (item.label === 'Subjects') {
                    navigate('/dicom');
                  } else if (item.label === 'Studies') {
                    navigate('/dicom');
                  } else {
                    navigate(item.route);
                  }
                }}
                title={collapsed ? item.label : ''}
              >
                <Icon />
                {!collapsed && <span>{item.label}</span>}
              </button>
            );
          })}
        </nav>

        <div className={styles.sidebarBottom}>
          <button className={styles.navItem} onClick={logout}>
            <LogoutIcon />
            {!collapsed && <span>Log out</span>}
          </button>
        </div>

        <button className={styles.collapseBtn} onClick={() => setCollapsed(!collapsed)}>
          {collapsed ? '›' : '‹'}
        </button>
      </aside>

      {/* Main */}
      <div className={styles.main}>
        <header className={styles.header}>
          <nav className={styles.topNav}>
            {navTopItems.map((item) => (
              <button
                key={item.label}
                className={`${styles.topNavItem} ${
                  (item.route === '/dicom' && location.pathname.startsWith('/dicom')) ||
                  (item.route === '/offerings' && location.pathname === '/offerings' && item.label === 'Offerings')
                    ? styles.topNavActive : ''
                }`}
                onClick={() => navigate(item.route)}
              >
                {item.label}
              </button>
            ))}
          </nav>
          <div className={styles.headerRight}>
            <button className={styles.iconBtn} title="Refresh">⟳</button>
            <button className={styles.iconBtn} title="Print">🖨</button>
            <button className={styles.iconBtn} title="Upload">↑</button>
            <button className={styles.iconBtn} title="Notifications">🔔</button>
            <div className={styles.avatar}>{user?.name?.[0] || 'U'}</div>
          </div>
        </header>

        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function DashboardIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>;
}
function DevicesIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/></svg>;
}
function SubjectsIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>;
}
function StudiesIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>;
}
function SettingsIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>;
}
function LogoutIcon() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>;
}
