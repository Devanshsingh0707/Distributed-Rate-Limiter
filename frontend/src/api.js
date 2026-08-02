const getBaseUrl = (port) => `http://localhost:${port}`;

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
  }
};
