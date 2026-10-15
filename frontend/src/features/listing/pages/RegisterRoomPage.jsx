import { useState } from "react";
import { useRooms } from "../../../app/state/RoomContext.jsx";
import { Field, PageHeading } from "../../../shared/ui/UI.jsx";
import { money } from "../../../shared/lib/domain.js";
import AdDraftPanel from "../../extraction/components/AdDraftPanel.jsx";
import RoomForm from "../components/RoomForm.jsx";

/**
 * 직접 입력과 서버에 보관한 광고를 하나의 등록 화면으로 연결한다.
 * 광고 확인 값은 마지막 저장 전까지 폼의 초안이며 공식 정보로 취급하지 않는다.
 */
export default function RegisterRoomPage({ navigate }) {
  const { saveRoom, dataMode } = useRooms();
  const [step, setStep] = useState(1);
  const [method, setMethod] = useState("manual");
  const [source, setSource] = useState({ address: "", original: "" });
  const [values, setValues] = useState({});
  const [advertisementId, setAdvertisementId] = useState(null);
  const [error, setError] = useState("");
  const canUseAds = dataMode === "listing-api";

  function preview(input, nextStep) {
    setValues(input);
    setSource({ address: input.address, original: input.original });
    setStep(nextStep);
  }
  function startAdvertisement(input, id) {
    setAdvertisementId(id);
    preview(input, 2);
  }
  async function save() {
    if (await saveRoom(values)) navigate("rooms");
  }

  return <>
    <PageHeading kicker="SAVE YOUR NEXT ROOM" title="광고에서 가져오거나, 직접 입력하거나."
      description="광고 값을 확인하고 수정한 뒤 최종 확인에서 매물을 저장하세요." />
    <ol className="registration-steps" aria-label="매물 등록 단계">
      {["등록 방식·원본", "값 확인·수정", "최종 확인"].map((label, index) => <li key={label}
        className={step === index + 1 ? "active" : ""} aria-current={step === index + 1 ? "step" : undefined}>
        <span>{index + 1}</span>{label}
      </li>)}
    </ol>
    {step === 1 && <>
      <div className="modal-actions" role="group" aria-label="매물 등록 방식">
        <button className="secondary" aria-pressed={method === "manual"} onClick={() => { setMethod("manual"); setError(""); }}>직접 입력</button>
        <button className="secondary" aria-pressed={method === "advertisement"} onClick={() => { setMethod("advertisement"); setError(""); }}>광고 글·사진에서 가져오기</button>
      </div>
      {method === "manual" ? <section className="list-section ad-drafts">
        <h2>사진 없이 직접 등록하세요</h2>
        <form onSubmit={event => {
          event.preventDefault();
          if (!source.address.trim()) { setError("주소를 입력해 주세요."); return; }
          setAdvertisementId(null);
          setValues({ ...values, ...source });
          setStep(2);
        }}>
          <Field label="주소 / 건물명 *"><input required maxLength="160" value={source.address}
            onChange={event => setSource(old => ({ ...old, address: event.target.value }))} /></Field>
          <Field label="광고 글 (선택)"><textarea rows="3" maxLength="10000" value={source.original}
            onChange={event => setSource(old => ({ ...old, original: event.target.value }))} /></Field>
          <p className="helper">직접 입력에는 광고 글·이미지가 필요하지 않습니다. 아직 매물은 저장되지 않습니다.</p>
          {error && <p role="alert">{error}</p>}
          <button className="primary" type="submit">값 확인으로 이동</button>
        </form>
      </section> : canUseAds ? <AdDraftPanel onRegister={startAdvertisement} /> : <section className="list-section ad-drafts">
        <h2>광고 추출은 매물 서버 연결 모드에서 제공합니다.</h2>
        <p>현재 모드에서는 직접 입력을 사용할 수 있습니다. 실제 추출 결과를 가상으로 만들지 않습니다.</p>
      </section>}
    </>}
    {step === 2 && <section className="list-section ad-drafts">
      {advertisementId && <p className="helper">서버에 저장한 광고 확인 값을 불러왔습니다. 수정 후 최종 확인으로 이동하세요.</p>}
      <RoomForm initialValues={values} heading="매물 정보를 확인하고 수정하세요"
        helperText={advertisementId ? "사용자가 확인한 광고 값입니다. 이름과 누락된 주소를 입력하세요." : "직접 확인한 조건을 입력하세요. 모르는 값은 비워두세요."}
        onCancel={input => preview(input, 1)} onPreview={input => preview(input, 3)} />
    </section>}
    {step === 3 && <section className="list-section ad-drafts">
      <h2>이 내용으로 저장할까요?</h2>
      <p className="helper">광고 값은 건축물대장 공식 정보가 아닙니다. 저장 버튼을 눌러야 매물이 생성됩니다.</p>
      <dl className="confirmation-grid">
        {[["매물 이름", values.name], ["주소", values.address], ["보증금", money(values.deposit)],
          ["월세", money(values.rent)], ["관리비", money(values.fee)],
          ["면적", values.area == null ? "정보 없음" : `${values.area}㎡ · ${values.areaType}`],
          ["층수", values.floor == null ? "정보 없음" : `${values.floor}층`]].map(([label, value]) =>
          <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
      </dl>
      <p className="helper">{dataMode === "local" ? "이 브라우저에 저장합니다." : "연결된 매물 서버에 저장합니다."} 광고 원본·AI 추출값·광고 확인 값은 이 폼의 수정과 별도로 유지합니다.</p>
      <div className="modal-actions">
        <button className="secondary" onClick={() => setStep(2)}>내용 수정</button>
        <button className="primary" onClick={save}>확정 저장</button>
      </div>
    </section>}
    <button className="text-button" onClick={() => navigate("rooms")}>등록 취소 · 목록으로</button>
  </>;
}
