export function StatCard({ label, value, note, icon, accent }: {
  label: string;
  value: number | string;
  note: string;
  icon: string;
  accent?: boolean;
}) {
  return (
    <article className={`stat-card${accent ? ' stat-card-accent' : ''}`}>
      <div className="stat-card-top"><span>{label}</span><span className="stat-icon" aria-hidden="true">{icon}</span></div>
      <div className="stat-value">{value}</div>
      <div className="stat-note">{note}</div>
    </article>
  );
}
