export function parseOptions(text, confirmedNone = false) {
  if (confirmedNone) return [];
  const lines = text.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
  if (lines.length === 0) return null;
  if (lines.length > 30 || lines.some(value => value.length > 100 || /[\u0000-\u001f\u007f-\u009f]/.test(value))) {
    throw new Error("옵션은 한 줄에 하나씩, 항목당 100자·최대 30개로 입력해 주세요.");
  }
  return [...new Set(lines)];
}

export function optionsText(values) {
  return Array.isArray(values) ? values.join("\n") : "";
}

export function optionsSummary(values) {
  return values == null ? "정보 없음" : values.length === 0 ? "옵션 없음으로 확인" : values.join(", ");
}
