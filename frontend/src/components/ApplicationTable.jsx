export default function ApplicationTable({ applications, statuses, onStatusChange, onEdit, onDelete }) {
  return (
    <div className="table-wrapper">
      <table className="applications-table">
        <thead>
          <tr>
            <th>Company</th>
            <th>Role</th>
            <th>Status</th>
            <th>Applied</th>
            <th>Link</th>
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {applications.map((app) => (
            <tr key={app.id}>
              <td>{app.company}</td>
              <td>{app.role}</td>
              <td>
                <select
                  value={app.status}
                  onChange={(e) => onStatusChange(app.id, e.target.value)}
                  className={`status-select status-${app.status.toLowerCase()}`}
                >
                  {statuses.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </td>
              <td>{app.appliedDate || "—"}</td>
              <td>
                {app.link ? (
                  <a href={app.link} target="_blank" rel="noreferrer">
                    Link
                  </a>
                ) : (
                  "—"
                )}
              </td>
              <td className="row-actions">
                <button className="link-button" onClick={() => onEdit(app)}>
                  Edit
                </button>
                <button className="link-button danger" onClick={() => onDelete(app.id)}>
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
