const API_BASE_URL = import.meta.env.VITE_API_URL;

const getBaseUrl = (port) => {
  if (API_BASE_URL) {
    // In production/deployment, default port 8080 uses the environment variable
    if (port === 8080) {
      return API_BASE_URL;
    }
  }
  return `http://localhost:${port}`;
};

export const api = {
  async getAlgorithm(port) {
    const res = await fetch(`${getBaseUrl(port)}/stats/algorithm`);
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },
  
  async setAlgorithm(port, algorithm) {
    const res = await fetch(`${getBaseUrl(port)}/stats/algorithm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ algorithm })
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async getSummary(port) {
    const res = await fetch(`${getBaseUrl(port)}/stats/summary`);
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async getFeed(port) {
    const res = await fetch(`${getBaseUrl(port)}/stats/feed`);
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async reset(port) {
    const res = await fetch(`${getBaseUrl(port)}/stats/reset`, {
      method: 'POST'
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async checkHealth(port) {
    try {
      const res = await fetch(`${getBaseUrl(port)}/stats/health`);
      if (!res.ok) return { redisConnected: false };
      return await res.json();
    } catch (e) {
      return { redisConnected: false };
    }
  },

  async stress(port, { clientId, route, count }) {
    const res = await fetch(`${getBaseUrl(port)}/stats/stress`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ clientId, route, count })
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async sendRequestDirect(port, { clientId, route }) {
    const startTime = Date.now();
    try {
      const res = await fetch(`${getBaseUrl(port)}${route}`, {
        headers: { 'X-Client-Id': clientId }
      });
      const latency = Date.now() - startTime;
      const data = await res.json();
      return {
        status: res.status,
        allowed: res.status !== 429 && res.status !== 503,
        latency,
        data
      };
    } catch (e) {
      const latency = Date.now() - startTime;
      return {
        status: 500,
        allowed: false,
        latency,
        error: e.message
      };
    }
  },

  async getRedisStatus(port) {
    const res = await fetch(`${getBaseUrl(port)}/stats/redis-status`);
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async setRedisSimulation(port, simulatingDown) {
    const res = await fetch(`${getBaseUrl(port)}/stats/redis-simulation`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ simulatingDown })
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async setFailMode(port, failMode) {
    const res = await fetch(`${getBaseUrl(port)}/stats/fail-mode`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ failMode })
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  },

  async runLoadTest(port, { endpoint, requests, concurrency }) {
    const res = await fetch(`${getBaseUrl(port)}/stats/load-test`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ endpoint, requests, concurrency })
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return res.json();
  }
};
