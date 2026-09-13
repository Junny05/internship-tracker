import { useState } from "react";

export default function ApplicationForm({ initialValue, statuses, onSubmit, onCancel }) {
  const [form, setForm] = useState({
    company: initialValue?.company || "",
    role: initialValue?.role || "",
    status: initialValue?.status || "WISHLIST",
    appliedDate: initialValue?.appliedDate || "",
    notes: initialValue?.notes || "",
    link: initialValue?.link || "",
  });
  const [submitting, setSubmitting] = useState(false);

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await onSubmit({
        ...form,
        appliedDate: form.appliedDate || null,
        notes: form.notes || null,
        link: form.link || null,
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <form className="modal-card" onClick={(e) => e.stopPropagation()} onSubmit={handleSubmit}>
        <h2>{initialValue ? "Edit application" : "Add application"}</h2>

        <label>
          Company
          <input value={form.company} onChange={(e) => update("company", e.target.value)} required />
        </label>

        <label>
          Role
          <input value={form.role} onChange={(e) => update("role", e.target.value)} required />
        </label>

        <label>
          Status
          <select value={form.status} onChange={(e) => update("status", e.target.value)}>
            {statuses.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>

        <label>
          Applied date
          <input
            type="date"
            value={form.appliedDate || ""}
            onChange={(e) => update("appliedDate", e.target.value)}
          />
        </label>

        <label>
          Link
          <input
            type="url"
            value={form.link || ""}
            onChange={(e) => update("link", e.target.value)}
            placeholder="https://…"
          />
        </label>

        <label>
          Notes
          <textarea value={form.notes || ""} onChange={(e) => update("notes", e.target.value)} rows={3} />
        </label>

        <div className="modal-actions">
          <button type="button" className="secondary-button" onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" disabled={submitting}>
            {submitting ? "Saving…" : initialValue ? "Save changes" : "Add"}
          </button>
        </div>
      </form>
    </div>
  );
}
