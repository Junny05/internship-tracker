import { useEffect, useState } from "react";
import { api, ApiError } from "../api";

export default function JobAlerts({ token, onAuthError }) {
  const [searches, setSearches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [keywords, setKeywords] = useState("");
  const [location, setLocation] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    loadSearches();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function loadSearches() {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listSavedSearches(token);
      setSearches(data);
    } catch (err) {
      handleError(err);
    } finally {
      setLoading(false);
    }
  }

  function handleError(err) {
    if (err instanceof ApiError && err.status === 401) {
      onAuthError();
      return;
    }
    setError(err.message || "Something went wrong");
  }

  async function handleAdd(e) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await api.createSavedSearch(token, { keywords, location: location || null });
      setKeywords("");
      setLocation("");
      await loadSearches();
    } catch (err) {
      handleError(err);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    try {
      await api.deleteSavedSearch(token, id);
      setSearches((prev) => prev.filter((s) => s.id !== id));
    } catch (err) {
      handleError(err);
    }
  }

  return (
    <section className="job-alerts">
      <button type="button" className="job-alerts-toggle" onClick={() => setExpanded((v) => !v)}>
        {expanded ? "▾" : "▸"} Job alerts {searches.length > 0 && `(${searches.length})`}
      </button>

      {expanded && (
        <div className="job-alerts-body">
          <p className="job-alerts-hint">
            Get an email whenever a new internship posting matches your keywords. Checked
            automatically in the background.
          </p>

          <form className="job-alerts-form" onSubmit={handleAdd}>
            <input
              value={keywords}
              onChange={(e) => setKeywords(e.target.value)}
              placeholder="Keywords, e.g. software engineering intern"
              required
            />
            <input
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="Location (optional)"
            />
            <button type="submit" disabled={submitting}>
              {submitting ? "Adding…" : "Add"}
            </button>
          </form>

          {error && <p className="error-text">{error}</p>}

          {loading ? (
            <p className="empty-state">Loading…</p>
          ) : searches.length === 0 ? (
            <p className="empty-state">No saved searches yet.</p>
          ) : (
            <ul className="job-alerts-list">
              {searches.map((s) => (
                <li key={s.id}>
                  <span>
                    <strong>{s.keywords}</strong>
                    {s.location && ` — ${s.location}`}
                  </span>
                  <button className="link-button danger" onClick={() => handleDelete(s.id)}>
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
