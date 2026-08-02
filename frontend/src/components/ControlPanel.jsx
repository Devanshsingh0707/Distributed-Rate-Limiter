import React from 'react';

const ALGORITHMS = [
  { id: 'fixed', name: 'Fixed Window' },
  { id: 'token', name: 'Token Bucket' },
  { id: 'sliding', name: 'Sliding Window Log' }
];

const ROUTES = [
  { path: '/api/login', label: '/api/login (Limit: 5/min)' },
  { path: '/api/search', label: '/api/search (Limit: 100/min)' },
  { path: '/api/payment', label: '/api/payment (Limit: 20/min)' },
  { path: '/api/products', label: '/api/products (Limit: 50/min)' }
];

const COUNTS = [1, 10, 50, 100];

export default function ControlPanel({
  port,
  setPort,
  algorithm,
  onAlgorithmChange,
  clientId,
  setClientId,
  route,
  setRoute,
  count,
  setCount,
  onSend,
  onReset,
  loading,
  simulatingDown,
  onRedisSimulationToggle,
  failMode,
  onFailModeChange,
  onRunLoadTest,
  loadTestRunning
}) {
  return (
    <div className="control-panel">
      {/* 1. Target Instance Configuration */}
      <div className="panel-section">
        <label className="section-label">Target Instance Port</label>
        <div className="port-selector">
          {[8080, 8081, 8082].map((p) => (
            <label key={p} className={`radio-label ${port === p ? 'active' : ''}`}>
              <input
                type="radio"
                name="targetPort"
                checked={port === p}
                onChange={() => setPort(p)}
              />
              <span>:{p}</span>
            </label>
          ))}
        </div>
      </div>

      {/* 2. Algorithm Selector */}
      <div className="panel-section">
        <label className="section-label">Rate Limiter Algorithm</label>
        <div className="algo-selector">
          {ALGORITHMS.map((algo) => (
            <label key={algo.id} className={`radio-label ${algorithm === algo.id ? 'active' : ''}`}>
              <input
                type="radio"
                name="algorithm"
                value={algo.id}
                checked={algorithm === algo.id}
                onChange={(e) => onAlgorithmChange(e.target.value)}
                disabled={loading}
              />
              <span>{algo.name}</span>
            </label>
          ))}
        </div>
      </div>

      {/* 3. Traffic Config */}
      <div className="panel-section grid-2">
        <div>
          <label className="section-label">Client ID</label>
          <input
            type="text"
            className="text-input"
            value={clientId}
            onChange={(e) => setClientId(e.target.value.trim() || 'client_1')}
            placeholder="e.g. client_1"
            disabled={loading}
          />
        </div>
        <div>
          <label className="section-label">Request Count</label>
          <select
            className="select-input"
            value={count}
            onChange={(e) => setCount(Number(e.target.value))}
            disabled={loading}
          >
            {COUNTS.map((c) => (
              <option key={c} value={c}>
                {c} request{c > 1 ? 's' : ''}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="panel-section">
        <label className="section-label">Target API Endpoint</label>
        <select
          className="select-input"
          value={route}
          onChange={(e) => setRoute(e.target.value)}
          disabled={loading}
        >
          {ROUTES.map((r) => (
            <option key={r.path} value={r.path}>
              {r.label}
            </option>
          ))}
        </select>
      </div>

      {/* 4. Operations */}
      <div className="panel-actions">
        <button
          className="btn btn-primary"
          onClick={onSend}
          disabled={loading || loadTestRunning}
        >
          {loading ? 'Sending...' : 'Send Requests'}
        </button>
        <button
          className="btn btn-secondary"
          onClick={onReset}
          disabled={loading || loadTestRunning}
        >
          Reset Statistics
        </button>
      </div>

      <hr className="divider" />

      {/* 5. Failure Mode - Simulate Redis Outage */}
      <div className="panel-section">
        <label className="section-label">Failure Mode - Simulate Redis Outage</label>
        <button
          className={`btn-outage ${simulatingDown ? 'outage-active' : 'outage-inactive'}`}
          onClick={onRedisSimulationToggle}
          disabled={loading || loadTestRunning}
        >
          {simulatingDown ? 'Redis: DOWN (click to restore)' : 'Redis: UP (click to kill)'}
        </button>
        
        {simulatingDown && (
          <div className="fail-mode-sub">
            <div className="fail-mode-toggles">
              <button
                className={`btn-toggle ${failMode === 'closed' ? 'toggle-active' : ''}`}
                onClick={() => onFailModeChange('closed')}
                disabled={loading || loadTestRunning}
              >
                fail closed (block all)
              </button>
              <button
                className={`btn-toggle ${failMode === 'open' ? 'toggle-active' : ''}`}
                onClick={() => onFailModeChange('open')}
                disabled={loading || loadTestRunning}
              >
                fail open (allow all)
              </button>
            </div>
            <p className="outage-note">
              {failMode === 'closed' 
                ? 'Safe default: with no shared state to check, every request is rejected until Redis is reachable again.'
                : 'Availability first: requests bypass the rate limit checks and are allowed through while Redis is down.'}
            </p>
          </div>
        )}
      </div>

      <hr className="divider" />

      {/* 6. Load Test */}
      <div className="panel-section">
        <label className="section-label">Load Test</label>
        <button
          className="btn btn-run-loadtest"
          onClick={onRunLoadTest}
          disabled={loading || loadTestRunning}
        >
          {loadTestRunning ? 'Running Load Test...' : 'Run Load Test (1,000 req)'}
        </button>
      </div>
    </div>
  );
}
