import { useCallback, useEffect, useState } from "react";
import { get } from "./api";

// Load data for a screen: const { data, error, reload } = useApi("/waitlist")
export default function useApi(path) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  const reload = useCallback(() => {
    if (!path) return;
    setError(null);
    get(path).then(setData, setError);
  }, [path]);

  useEffect(() => {
    setData(null);
    reload();
  }, [reload]);

  return { data, error, reload };
}
