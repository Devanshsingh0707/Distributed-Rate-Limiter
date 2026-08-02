import React, { useState, useEffect, useRef } from 'react';
import { api } from './api';
import ControlPanel from './components/ControlPanel';
import LiveStats from './components/LiveStats';
import LiveChart from './components/LiveChart';
import ActivityFeed from './components/ActivityFeed';

export default function App() {
  const [port, setPort] = useState(8080);
  const [algorithm, setAlgorithm] = useState('fixed');
  const [clientId, setClientId] = useState('client_1');
  const [route, setRoute] = useState('/api/login');
  const [count, setCount] = useState(1);
  
  const [summary, setSummary] = useState({
    allowed: 0,
    blocked: 0,
    totalRequests: 0,
    currentRPS: 0.0,
    avgLatencyMs: 0.0
  });
  const [feed, setFeed] = useState([]);
  const [chartData, setChartData] = useState([]);
  const [redisConnected, setRedisConnected] = useState(false);
  const [loading, setLoading] = useState(false);

  // References to keep track of accumulated sums for delta calculations
  const lastAllowedRef = useRef(0);
  const lastBlockedRef = useRef(0);

  // 1. Sync Algorithm when port changes (or on startup)
  useEffect(() => {
    async function syncAlgorithm() {
      try {
        const data = await api.getAlgorithm(port);
        setAlgorithm(data.algorithm);
      } catch (err) {
        console.error(`Failed to sync algorithm for port :${port}`, err);
      }
    }
    syncAlgorithm();
    // Flush chart and references when switching instances
    setChartData([]);
    lastAllowedRef.current = 0;
    lastBlockedRef.current = 0;
  }, [port]);

  // 2. Fetch data (summary, feed, health) helper
  const fetchData = async () => {
    try {
      // Check health (Redis connection status)
      const health = await api.checkHealth(port);
      setRedisConnected(health.redisConnected);

      // Get metrics summary
      const sumData = await api.getSummary(port);
      setSummary(sumData);

      // Get activity feed
      const feedData = await api.getFeed(port);
      setFeed(feedData);

      // Compute charts delta (traffic rate per second)
      const now = new Date();
      const timeStr = now.toTimeString().split(' ')[0];

      const currentAllowed = sumData.allowed;
      const currentBlocked = sumData.blocked;

      // Deltas are computed since the last poll
      const deltaAllowed = Math.max(0, currentAllowed - lastAllowedRef.current);
      const deltaBlocked = Math.max(0, currentBlocked - lastBlockedRef.current);

      lastAllowedRef.current = currentAllowed;
      lastBlockedRef.current = currentBlocked;

      setChartData((prev) => {
        const next = [...prev, { time: timeStr, allowed: deltaAllowed, blocked: deltaBlocked }];
        if (next.length > 30) {
          next.shift(); // Keep last 30 seconds
        }
        return next;
      });
    } catch (err) {
      console.error('Error polling dashboard stats:', err);
    }
  };

  // 3. Poll backend stats every 1 second
  useEffect(() => {
    fetchData(); // Initial load
    const interval = setInterval(fetchData, 1000);
    return () => clearInterval(interval);
  }, [port]);

  // 4. Handle changing the rate limiting algorithm
  const handleAlgorithmChange = async (newAlgo) => {
    setLoading(true);
    try {
      const data = await api.setAlgorithm(port, newAlgo);
      setAlgorithm(data.algorithm);
    } catch (err) {
      alert(`Failed to set algorithm on port :${port}: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // 5. Handle sending requests (client burst simulation or browser direct call)
  const handleSendRequests = async () => {
    setLoading(true);
    try {
      if (count === 1) {
        // Send a single real HTTP request from the browser
        await api.sendRequestDirect(port, { clientId, route });
      } else {
        // Trigger server-side stress simulation for high counts
        await api.stress(port, { clientId, route, count });
      }
      // Immediately pull fresh stats
      await fetchData();
    } catch (err) {
      alert(`Failed to execute requests on port :${port}: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // 6. Handle Resetting stats and Redis keys
  const handleReset = async () => {
    if (!confirm('Are you sure you want to reset all statistics and clear Redis keys?')) return;
    setLoading(true);
    try {
      await api.reset(port);
      lastAllowedRef.current = 0;
      lastBlockedRef.current = 0;
      setSummary({
        allowed: 0,
        blocked: 0,
        totalRequests: 0,
        currentRPS: 0.0,
        avgLatencyMs: 0.0
      });
      setFeed([]);
      setChartData([]);
    } catch (err) {
      alert(`Failed to reset on port :${port}: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-container">
      {/* HEADER SECTION */}
      <header className="app-header">
        <div className="header-title-area">
          <h1>Distributed Rate Limiter Console</h1>
          <p>Real-time atomic rate limiting visualization using Redis & Lua scripting.</p>
        </div>
        <div className="header-status-area">
          <span className={`badge ${redisConnected ? 'badge-connected' : 'badge-disconnected'}`}>
            <span className="status-dot"></span>
            Redis: {redisConnected ? 'CONNECTED' : 'DISCONNECTED'}
          </span>
          <span className="badge badge-algorithm">
            Algorithm: {algorithm}
          </span>
        </div>
      </header>

      {/* DASHBOARD GRID */}
      <main className="dashboard-grid">
        {/* Left Side: Control Panel */}
        <div className="grid-left">
          <div className="card">
            <div className="card-title">
              <span>Traffic & Config Controls</span>
              {loading && <span className="pulse-traffic"></span>}
            </div>
            <ControlPanel
              port={port}
              setPort={setPort}
              algorithm={algorithm}
              onAlgorithmChange={handleAlgorithmChange}
              clientId={clientId}
              setClientId={setClientId}
              route={route}
              setRoute={setRoute}
              count={count}
              setCount={setCount}
              onSend={handleSendRequests}
              onReset={handleReset}
              loading={loading}
            />
          </div>
        </div>

        {/* Right Side: Monitoring Data */}
        <div className="grid-right" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Live Metrics Grid */}
          <LiveStats summary={summary} />

          {/* Real-time Graph */}
          <div className="card">
            <div className="card-title">Traffic Intensity Graph (Deltas / Sec)</div>
            <LiveChart chartData={chartData} />
          </div>

          {/* Monospace Log Feed */}
          <div className="card">
            <div className="card-title">Real-Time Request Decision Log (Last 50)</div>
            <ActivityFeed feed={feed} />
          </div>
        </div>
      </main>
    </div>
  );
}
