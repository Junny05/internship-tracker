import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "../api";
import ApplicationForm from "../components/ApplicationForm";
import ApplicationTable from "../components/ApplicationTable";
import JobAlerts from "../components/JobAlerts";

const STATUSES = ["WISHLIST", "APPLIED", "OA", "INTERVIEW", "OFFER", "REJECTED"];

export default function Dashboard({ token, email, onLogout }) {
  const [applications, setApplications] = useState([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingApplication, setEditingApplication] = useState(null);
  const [showForm, setShowForm] = useState(false);

  const handleError = useCallback(
    (err) => {
      if (err instanceof ApiError && err.status === 401) {
        onLogout();
        return;
      }
      setError(err.message || "Something went wrong");
    },
    [onLogout]
  );

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listApplications(token, statusFilter || undefined);
      setApplications(data);
    } catch (err) {
      handleError(err);
    } finally {
      setLoading(false);
    }
  }, [token, statusFilter, handleError]);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  async function handleCreateOrUpdate(payload) {
    try {
      if (editingApplication) {
        await api.updateApplication(token, editingApplication.id, payload);
      } else {
        await api.createApplication(token, payload);
      }
      setShowForm(false);
      setEditingApplication(null);
      await loadApplications();
    } catch (err) {
      handleError(err);
    }
  }

  async function handleStatusChange(id, status) {
    const previous = applications;
    setApplications((prev) => prev.map((a) => (a.id === id ? { ...a, status } : a)));
    try {
      await api.updateStatus(token, id, status);
    } catch (err) {
      setApplications(previous);
      handleError(err);
    }
  }

  async function handleDelete(id) {
    if (!window.confirm("Delete this application?")) return;
    try {
      await api.deleteApplication(token, id);
      setApplications((prev) => prev.filter((a) => a.id !== id));
    } catch (err) {
      handleError(err);
    }
  }

  function startEdit(application) {
    setEditingApplication(application);
    setShowForm(true);
  }

  function startCreate() {
    setEditingApplication(null);
    setShowForm(true);
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>Internship Tracker</h1>
          {email && <p className="signed-in-as">Signed in as {email}</p>}
        </div>
        <button className="secondary-button" onClick={onLogout}>
          Log out
        </button>
      </header>

      <JobAlerts token={token} onAuthError={onLogout} />

      <div className="toolbar">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <button onClick={startCreate}>+ Add application</button>
      </div>

      {error && <p className="error-text">{error}</p>}

      {loading ? (
        <p className="empty-state">Loading…</p>
      ) : applications.length === 0 ? (
        <p className="empty-state">No applications yet. Add your first one above.</p>
      ) : (
        <ApplicationTable
          applications={applications}
          statuses={STATUSES}
          onStatusChange={handleStatusChange}
          onEdit={startEdit}
          onDelete={handleDelete}
        />
      )}

      {showForm && (
        <ApplicationForm
          initialValue={editingApplication}
          statuses={STATUSES}
          onSubmit={handleCreateOrUpdate}
          onCancel={() => {
            setShowForm(false);
            setEditingApplication(null);
          }}
        />
      )}
    </div>
  );
}
