import { Bar, BarChart, CartesianGrid, Cell, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { STATUS_TOKEN } from '../config/constants';

const axis = { stroke: 'rgb(var(--color-text-subtle))', fontSize: 12, tickLine: false, axisLine: false };
const tip = { contentStyle: { background: 'rgb(var(--color-surface))', border: '1px solid rgb(var(--color-border))', borderRadius: 9, fontSize: 12 } };

export function FunnelChart({ data }) {
  return (
    <div className="h-64" role="img" aria-label={`Application funnel: ${data.map((d) => `${d.stage} ${d.count}`).join(', ')}`}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ left: 16, right: 16 }}>
          <CartesianGrid horizontal={false} stroke="rgb(var(--color-border))" />
          <XAxis type="number" allowDecimals={false} {...axis} />
          <YAxis type="category" dataKey="stage" width={90} {...axis} />
          <Tooltip {...tip} cursor={{ fill: 'rgb(var(--color-surface-muted))' }} />
          <Bar dataKey="count" radius={[0, 6, 6, 0]} animationDuration={600}>
            {data.map((d) => <Cell key={d.stage} fill={`rgb(var(--${STATUS_TOKEN[d.stage] || 'color-primary'}))`} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function SourcesChart({ data }) {
  return (
    <div className="h-64" role="img" aria-label="Applications, interviews and offers by source">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ left: -16, right: 8 }}>
          <CartesianGrid vertical={false} stroke="rgb(var(--color-border))" />
          <XAxis dataKey="source" {...axis} /><YAxis allowDecimals={false} {...axis} />
          <Tooltip {...tip} cursor={{ fill: 'rgb(var(--color-surface-muted))' }} />
          <Bar dataKey="applications" name="Applications" fill="rgb(var(--status-applied))" radius={[4, 4, 0, 0]} />
          <Bar dataKey="interviews" name="Interviews" fill="rgb(var(--status-interview))" radius={[4, 4, 0, 0]} />
          <Bar dataKey="offers" name="Offers" fill="rgb(var(--status-selected))" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function TrendChart({ data }) {
  return (
    <div className="h-64" role="img" aria-label="Applications submitted over time">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ left: -16, right: 8 }}>
          <CartesianGrid vertical={false} stroke="rgb(var(--color-border))" />
          <XAxis dataKey="period" {...axis} /><YAxis allowDecimals={false} {...axis} />
          <Tooltip {...tip} />
          <Line type="monotone" dataKey="count" name="Applications" stroke="rgb(var(--color-primary))" strokeWidth={2} dot={{ r: 3 }} animationDuration={600} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Current status counts. Colors come from the same status tokens as the badges. */
export function StatusChart({ data }) {
  return (
    <div className="h-64" role="img" aria-label={`Applications by current status: ${data.map((d) => `${d.label} ${d.count}`).join(', ')}`}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ left: -16, right: 8 }}>
          <CartesianGrid vertical={false} stroke="rgb(var(--color-border))" />
          <XAxis dataKey="label" {...axis} /><YAxis allowDecimals={false} {...axis} />
          <Tooltip {...tip} cursor={{ fill: 'rgb(var(--color-surface-muted))' }} />
          <Bar dataKey="count" name="Applications" radius={[4, 4, 0, 0]} animationDuration={600}>
            {data.map((d) => <Cell key={d.label} fill={`rgb(var(--${STATUS_TOKEN[d.label] || 'color-primary'}))`} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Distribution of skill-match scores. The last bucket is applications with no required skills listed. */
export function MatchChart({ data }) {
  return (
    <div className="h-64" role="img" aria-label={`Skill match distribution: ${data.map((d) => `${d.label} ${d.count}`).join(', ')}`}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ left: -16, right: 8 }}>
          <CartesianGrid vertical={false} stroke="rgb(var(--color-border))" />
          <XAxis dataKey="label" {...axis} interval={0} /><YAxis allowDecimals={false} {...axis} />
          <Tooltip {...tip} cursor={{ fill: 'rgb(var(--color-surface-muted))' }} />
          <Bar dataKey="count" name="Applications" fill="rgb(var(--color-primary))" radius={[4, 4, 0, 0]} animationDuration={600} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
