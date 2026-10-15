import { useState } from "react";
import { useRooms } from "../context/RoomContext.jsx";
import { Field } from "./UI.jsx";
export default function RoomForm({ room, initialValues, onDone, onCancel }) {
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
  async function submit(e) {
    e.preventDefault();
    if (!values.name.trim() || !values.address.trim()) {
      setError("이름과 주소를 입력해 주세요.");
      return;
    }
    const number = (key, scale = 1) =>
      values[key] === "" ? null : Number(values[key]) * scale;
    const saved = await saveRoom(
      {
        ...values,
        name: values.name.trim(),
        address: values.address.trim(),
        deposit: values.deposit === "" ? null : Math.round(number("deposit", 10000)),
        rent: values.rent === "" ? null : Math.round(number("rent", 10000)),
        fee: values.fee === "" ? null : Math.round(number("fee", 10000)),
        area: number("area"),
        floor: number("floor"),
      },
      room?.id,
    );
    if (saved) onDone();
  }
  return (
    <>
      <h2>{room ? "매물 정보 수정" : "새로운 방을 저장하세요"}</h2>
      <p className="helper">
        {initialValues
          ? "사용자가 확인한 광고 값입니다. 이름과 누락된 주소를 입력하고, 값을 수정한 뒤 저장하세요."
          : "직접 확인한 광고 정보를 입력하세요."}
        {" "}광고 값은 건축물대장 공식 정보가 아닙니다. 공식 정보는 별도로 조회·대조해야 합니다.
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
                max={key === "area" ? "100000" : "100000000"}
                step={key === "area" ? "0.01" : "0.0001"}
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
              min="-20"
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
            maxLength="10000"
            placeholder="광고 내용을 그대로 보관해 보세요."
          />
        </Field>
        <p className="helper">
          0원과 미입력을 구분합니다. 매물 수정 시 기존 공식 대조 자료는 재확인을
          위해 해제됩니다.
        </p>
        {error && <p role="alert">{error}</p>}
        <div className="modal-actions">
          <button className="secondary" type="button" onClick={onCancel}>
            취소
          </button>
          <button className="primary" type="submit">
            {room ? "변경 저장" : "매물 저장"}
          </button>
        </div>
      </form>
    </>
  );
}
