import useApi from "../useApi";

// The item details a manager enters when registering or editing equipment
// (the columns of the old Excel tracker). All optional except the name.
export const BLANK_DETAILS = {
  name: "", weightCategory: "", model: "", serialNumber: "", assetNumber: "", assetFlag: "", size: "", maxUserWeight: "",
  hc21Number: "", dmlNumber: "", therapist: "", comments: "", purchaseDate: "", lastServiceDate: "",
};

const TEXT_FIELDS = [
  ["model", "Model"], ["serialNumber", "Serial number"], ["assetNumber", "Asset number"], ["size", "Size (width x depth)"],
  ["maxUserWeight", "Max user weight (as labelled)"], ["hc21Number", "HC21 number"], ["dmlNumber", "DML number"], ["therapist", "Therapist"],
];

// Empty strings become null so the API stores nothing rather than ""
export function detailsForApi(details) {
  return Object.fromEntries(Object.entries(details).map(([key, value]) => [key, value === "" ? null : value]));
}

export function detailsFromApi(item) {
  return Object.fromEntries(Object.keys(BLANK_DETAILS).map(key => [key, item[key] ?? ""]));
}

export default function EquipmentDetailsFields({ details, onChange }) {
  const { data: weights } = useApi("/weight-categories");
  const set = field => e => onChange({ ...details, [field]: e.target.value });
  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="form-grid">
      <label className="field wide"><span>Name *</span>
        <input type="text" required maxLength={100} placeholder="e.g. Transit wheelchair" value={details.name} onChange={set("name")} />
      </label>
      <label className="field wide"><span>Weight category</span>
        <select className="input" value={details.weightCategory} onChange={set("weightCategory")}>
          <option value="">Not recorded</option>
          {weights?.map(w => <option key={w.name} value={w.name}>{w.name} (patients up to {w.maxKg} kg)</option>)}
        </select>
        <span className="hint">The heaviest patients it is rated for. Used by Find equipment.</span>
      </label>
      {TEXT_FIELDS.map(([field, label]) => (
        <label className="field" key={field}><span>{label}</span>
          <input type="text" maxLength={100} value={details[field]} onChange={set(field)} />
        </label>
      ))}
      <label className="field"><span>Asset flag</span>
        <select className="input" value={details.assetFlag} onChange={set("assetFlag")}>
          <option value="">Not set</option><option value="AT+">AT+</option><option value="AT-">AT-</option>
        </select>
      </label>
      <label className="field"><span>Purchase date</span>
        <input type="date" max={today} value={details.purchaseDate} onChange={set("purchaseDate")} />
      </label>
      <label className="field"><span>Last service</span>
        <input type="date" max={today} value={details.lastServiceDate} onChange={set("lastServiceDate")} />
      </label>
      <label className="field wide"><span>Comments</span>
        <textarea className="input" maxLength={2000} value={details.comments} onChange={set("comments")} />
      </label>
    </div>
  );
}
