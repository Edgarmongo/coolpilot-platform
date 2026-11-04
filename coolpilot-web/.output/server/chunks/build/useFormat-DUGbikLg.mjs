function formatDate(value, pattern = "YYYY-MM-DD HH:mm") {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  const pad = (n) => String(n).padStart(2, "0");
  const map = {
    YYYY: String(date.getFullYear()),
    MM: pad(date.getMonth() + 1),
    DD: pad(date.getDate()),
    HH: pad(date.getHours()),
    mm: pad(date.getMinutes())
  };
  return pattern.replace(/YYYY|MM|DD|HH|mm/g, (token) => map[token]);
}

export { formatDate as f };
//# sourceMappingURL=useFormat-DUGbikLg.mjs.map
