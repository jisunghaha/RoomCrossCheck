import { useState } from "react";
import { useRooms } from "../../../app/state/RoomContext.jsx";
import { Field } from "../../../shared/ui/UI.jsx";
export default function RoomForm({ room, initialValues, onPreview, heading, helperText, onDone, onCancel }) {
  const { saveRoom } = useRooms();
  const initial = room || initialValues;
  const [values, setValues] = useState(() => ({
    name: initial?.name || "",
    address: initial?.address || "",
    deposit: initial?.deposit == null ? "" : initial.deposit / 10000,
    rent: initial?.rent == null ? "" : initial.rent / 10000,
    fee: initial?.fee == null ? "" : initial.fee / 10000,
    area: initial?.area ?? "",
    areaType: initial?.areaType || "미상",
    floor: initial?.floor ?? "",
    original: initial?.original || "",
  }));
  const [error, setError] = useState("");
  const bind = (key) => ({
    name: key,
    value: values[key],
    onChange: (e) => setValues((v) => ({ ...v, [key]: e.target.value })),
  });
  function inputValues() {
    const number = (key, scale = 1) =>
      values[key] === "" ? null : Number(values[key]) * scale;
    return {
        ...values,
        name: values.name.trim(),
        address: values.address.trim(),
        deposit: number("deposit", 10000),
        rent: number("rent", 10000),
        fee: number("fee", 10000),
        area: number("area"),
        floor: number("floor"),
      };
  }
  async function submit(e) {
    e.preventDefault();
    if (!values.name.trim() || !values.address.trim()) {
      setError("이름과 주소를 입력해 주세요.");
      return;
    }
    const input = inputValues();
    // 단계형 등록에서는 금액 단위를 변환한 입력만 전달하고 최종 확인 전에는 저장하지 않는다.
    if (onPreview) { onPreview(input); return; }
    const saved = await saveRoom(input, room?.id);
    if (saved) onDone();
  }
  return (
    <>
      <h2>{heading || (room ? "매물 정보 수정" : "새로운 방을 저장하세요")}</h2>
      <p className="helper">
        {helperText || "직접 확인한 광고 정보를 입력하세요. OCR·공식정보 조회는 백엔드 연결 후 사용할 수 있습니다."}
      </p>
      <form onSubmit={submit}>
        <Field label="매물 이름 *">
          <input
            {...bind("name")}
            required
            maxLength="60"
            placeholder="예: 학교 앞 햇살 좋은 원룸"
          />
        </Field>
        <Field label="주소 / 건물명 *">
          <input
            {...bind("address")}
            required
            maxLength="160"
            placeholder="도로명주소와 건물명을 입력하세요"
          />
        </Field>
        <div className="form-grid">
          {[
            ["보증금 (만원)", "deposit"],
            ["월세 (만원)", "rent"],
            ["고정 관리비 (만원)", "fee"],
            ["면적 (㎡)", "area"],
          ].map(([label, key]) => (
            <Field key={key} label={label}>
              <input
                {...bind(key)}
                type="number"
                min="0"
                max="1000000"
                step="0.01"
                placeholder="미상은 비워두세요"
              />
            </Field>
          ))}
          <Field label="면적 종류">
            <select {...bind("areaType")}>
              {["전용", "공급", "미상"].map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </Field>
          <Field label="층수">
            <input
              {...bind("floor")}
              type="number"
              min="-10"
              max="200"
              step="1"
              placeholder="미상은 비워두세요"
            />
          </Field>
        </div>
        <Field label="광고 원문">
          <textarea
            {...bind("original")}
            rows="3"
            maxLength="3000"
            placeholder="광고 내용을 그대로 보관해 보세요."
          />
        </Field>
        <p className="helper">
          0원과 미입력을 구분합니다. 매물 수정 시 기존 공식 대조 자료는 재확인을
          위해 해제됩니다.
        </p>
        {error && <p role="alert">{error}</p>}
        <div className="modal-actions">
          <button className="secondary" type="button" onClick={() => onCancel(inputValues())}>
            {onPreview ? "이전 단계" : "취소"}
          </button>
          <button className="primary" type="submit">
            {onPreview ? "확정 내용 확인" : room ? "변경 저장" : "매물 저장"}
          </button>
        </div>
      </form>
    </>
  );
}
