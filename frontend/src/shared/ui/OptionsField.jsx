import { useId } from "react";

export default function OptionsField({ text, none, onTextChange, onNoneChange }) {
  const inputId = useId();
  return <>
    <label htmlFor={inputId}>옵션 (한 줄에 하나)</label>
    <textarea id={inputId} name="options" rows="3" maxLength="3029"
      value={text} disabled={none} onChange={event => onTextChange(event.target.value)} />
    <label><input type="checkbox" name="optionsNone" checked={none}
      onChange={event => onNoneChange(event.target.checked)} />옵션 없음으로 확인</label>
    <p className="helper">빈칸은 미확인입니다. 옵션이 없음을 확인한 경우에만 체크하세요. 풀옵션은 구체적인 품목을 추측하지 않고 그대로 적습니다.</p>
  </>;
}
