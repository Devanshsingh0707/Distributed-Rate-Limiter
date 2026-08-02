import React from 'react';

export default function ActivityFeed({ feed }) {
  return (
    <div className="activity-feed">
      <div className="table-wrapper">
        <table className="feed-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>Endpoint</th>
              <th>Client ID</th>
              <th>Status</th>
              <th>HTTP Code</th>
              <th>Latency</th>
            </tr>
          </thead>
          <tbody>
            {feed.length === 0 ? (
              <tr>
                <td colSpan="6" className="empty-row">
                  No request activity logged yet. Send requests to see live logs.
                </td>
              </tr>
            ) : (
              feed.map((entry, idx) => {
                const isSuccess = entry.allowed;
                const statusClass = isSuccess ? 'status-allowed' : 'status-blocked';
                const codeClass = entry.statusCode === 200 ? 'text-allowed' : 
                                  entry.statusCode === 429 ? 'text-blocked' : 'text-warning';
                
                return (
                  <tr key={idx} className={isSuccess ? 'row-allowed' : 'row-blocked'}>
                    <td className="monospace font-12 text-muted">{entry.timestamp}</td>
                    <td className="monospace font-12 font-bold">{entry.route}</td>
                    <td className="monospace font-12">{entry.clientId}</td>
                    <td>
                      <span className={`status-badge ${statusClass}`}>
                        {isSuccess ? 'Allowed' : 'Blocked'}
                      </span>
                    </td>
                    <td className={`monospace font-12 font-bold ${codeClass}`}>
                      {entry.statusCode}
                    </td>
                    <td className="monospace font-12 text-latency">
                      {entry.latencyMs}ms
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
