import React from 'react';
import { useNavigate } from 'react-router-dom';

export default function Breadcrumb({ items }) {
  const navigate = useNavigate();
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#94a3b8', flexWrap: 'wrap' }}>
      {items.map((item, i) => (
        <React.Fragment key={i}>
          {i > 0 && <span style={{ color: '#cbd5e1' }}>»</span>}
          {item.href ? (
            <button
              onClick={() => navigate(item.href)}
              style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: '#94a3b8', fontSize: 13, padding: 0,
                transition: 'color 0.15s'
              }}
              onMouseEnter={e => e.target.style.color = '#1d4ed8'}
              onMouseLeave={e => e.target.style.color = '#94a3b8'}
            >
              {item.label}
            </button>
          ) : (
            <span style={{ color: '#475569', fontWeight: 500 }}>{item.label}</span>
          )}
        </React.Fragment>
      ))}
    </div>
  );
}
