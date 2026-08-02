import React from 'react';
import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
);

export default function LiveChart({ chartData }) {
  const data = {
    labels: chartData.map((d) => d.time),
    datasets: [
      {
        label: 'Allowed Requests',
        data: chartData.map((d) => d.allowed),
        borderColor: '#10b981', // emerald 500
        backgroundColor: 'rgba(16, 185, 129, 0.1)',
        borderWidth: 2,
        tension: 0.2,
        pointRadius: 2,
        pointHoverRadius: 4,
      },
      {
        label: 'Blocked Requests (429)',
        data: chartData.map((d) => d.blocked),
        borderColor: '#ef4444', // red 500
        backgroundColor: 'rgba(239, 68, 68, 0.1)',
        borderWidth: 2,
        tension: 0.2,
        pointRadius: 2,
        pointHoverRadius: 4,
      }
    ]
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        grid: {
          color: '#27272a', // zinc-800
        },
        ticks: {
          color: '#71717a', // zinc-500
          font: {
            family: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
            size: 10
          }
        }
      },
      y: {
        beginAtZero: true,
        grid: {
          color: '#27272a',
        },
        ticks: {
          color: '#71717a',
          stepSize: 1,
          font: {
            family: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
            size: 10
          }
        }
      }
    },
    plugins: {
      legend: {
        position: 'top',
        labels: {
          color: '#f4f4f5', // zinc-100
          boxWidth: 12,
          font: {
            family: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
            size: 11
          }
        }
      },
      tooltip: {
        backgroundColor: '#18181b', // zinc-900
        titleColor: '#f4f4f5',
        bodyColor: '#a1a1aa',
        borderColor: '#27272a',
        borderWidth: 1,
        titleFont: { family: 'monospace' },
        bodyFont: { family: 'monospace' }
      }
    }
  };

  return (
    <div className="chart-container">
      <Line data={data} options={options} />
    </div>
  );
}
