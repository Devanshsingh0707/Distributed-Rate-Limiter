import React from 'react';

export default function LiveStats({ summary }) {
  const { allowed = 0, blocked = 0, totalRequests = 0, currentRPS = 0, avgLatencyMs = 0 } = summary || {};
  
  const successRate = totalRequests > 0 
    ? ((allowed / totalRequests) * 100).toFixed(1) 
    : '100.0';

  return (
    <div className="stats-grid">
      <div className="stat-card border-allowed">
        <span className="stat-label">Allowed</span>
        <span className="stat-value text-allowed monospace">{allowed}</span>
      </div>
      
      <div className="stat-card border-blocked">
        <span className="stat-label">Blocked (429)</span>
        <span className="stat-value text-blocked monospace">{blocked}</span>
      </div>

      <div className="stat-card border-total">
        <span className="stat-label">Total Requests</span>
        <span className="stat-value text-total monospace">{totalRequests}</span>
      </div>

      <div className="stat-card border-success">
        <span className="stat-label">Success Rate</span>
        <span className="stat-value text-success monospace">{successRate}%</span>
      </div>

      <div className="stat-card border-rps">
        <span className="stat-label">Current RPS</span>
        <span className="stat-value text-rps monospace">{currentRPS.toFixed(1)}</span>
      </div>

      <div className="stat-card border-latency">
        <span className="stat-label">Avg Latency</span>
        <span className="stat-value text-latency monospace">{avgLatencyMs.toFixed(1)} ms</span>
      </div>
    </div>
  );
}
