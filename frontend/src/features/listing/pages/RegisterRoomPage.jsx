import { useEffect, useState } from "react";
import { useRooms } from "../../../app/state/RoomContext.jsx";
import { Field, PageHeading } from "../../../shared/ui/UI.jsx";
import { money } from "../../../shared/lib/money.js";
import RoomForm from "../components/RoomForm.jsx";

/**
 * 사진 등록과 직접 입력을 나누고, 확인한 조건을 최종 단계에서 저장한다.
 * 직접 입력은 사진·광고 글을 요구하지 않는다. 사진은 미리보기만 제공한다.
 */
export default function RegisterRoomPage({ room, navigate }) {
  const { saveRoom, dataMode } = useRooms();
  const [step, setStep] = useState(1);
  const [method, setMethod] = useState(
    room?.registrationMethod || (room?.attachmentName ? "photo" : "manual"),
  );
  const [source, setSource] = useState({
    original: room?.original || "",
    address: room?.address || "",
    buildingDong: room?.buildingDong || "",
    unitNumber: room?.unitNumber || "",
    sourceUrl: room?.sourceUrl || "",
  });
  const [values, setValues] = useState(room || {});
  const [image, setImage] = useState(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");
  const photo = method === "photo";

  useEffect(() => {
    if (!image) { setPreview(""); return; }
    const url = URL.createObjectURL(image);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [image]);

  const bind = key => ({
    value: source[key],
    onChange: e => setSource(s => ({ ...s, [key]: e.target.value })),
  });

  function changeMethod(next) {
    setMethod(next);
    setError("");
    // 직접 입력으로 전환한 뒤에는 이전 사진이나 첨부명을 저장에 포함하지 않는다.
    // 공통 주소·입력값은 유지하여 방식을 바꾸어도 다시 작성하지 않게 한다.
    if (next === "manual") setImage(null);
  }

  function selectImage(event) {
    const file = event.target.files?.[0];
    if (file && (!["image/jpeg", "image/png"].includes(file.type) || file.size > 10 * 1024 * 1024)) {
      setError("JPG·PNG 파일을 10MB 이하로 선택해 주세요.");
      setImage(null);
      event.target.value = "";
      return;
    }
    setError("");
    setImage(file || null);
  }

  function validateSource() {
    if (!source.address.trim()) {
      setError("주소를 입력해 주세요.");
      return false;
    }
    // 파일 이름만 저장된 임시 매물도 실제 사진을 다시 선택해야 사진 경로로 진행한다.
    if (photo && !image) {
      setError("사진으로 등록하려면 광고 이미지를 선택해 주세요. 사진 없이 진행하려면 직접 입력을 선택하세요.");
      return false;
    }
    if (source.sourceUrl && !/^https?:\/\//i.test(source.sourceUrl)) {
      setError("출처 URL은 http:// 또는 https://로 시작해야 합니다.");
      return false;
    }
    setError("");
    return true;
  }

  function keepValues(input, nextStep) {
    setValues(input);
    setSource(s => ({ ...s, original: input.original, address: input.address }));
    setStep(nextStep);
  }

  async function save(status) {
    if (status === "DRAFT" && !validateSource()) return;
    const input = {
      ...values,
      ...source,
      name: values.name || "확인 중인 매물",
      address: source.address.trim(),
      status,
      registrationMethod: method,
      attachmentName: photo ? image?.name || "" : "",
    };
    if (await saveRoom(input, room?.id)) navigate("rooms");
  }

  return <>
    <PageHeading
      kicker="SAVE YOUR NEXT ROOM"
      title="사진으로 가져오거나, 직접 입력하거나."
      description="사진이 없어도 확인한 매물 조건을 직접 입력하고 저장할 수 있습니다."
    />
    <ol className="registration-steps" aria-label="매물 등록 단계">
      {[photo ? "사진·기본 정보" : "기본 정보", photo ? "값 확인·수정" : "매물 정보 입력", "최종 확인"].map((label, i) => (
        <li key={label} className={step === i + 1 ? "active" : ""} aria-current={step === i + 1 ? "step" : undefined}>
          <span>{i + 1}</span>{label}
        </li>
      ))}
    </ol>
    {dataMode !== "local" ? (
      <section className="panel">
        <h2>단계형 등록은 현재 로컬 시연에서 제공합니다.</h2>
        <p>서버의 임시·확정 API를 연결한 뒤 서버 모드에서도 사용할 수 있습니다.</p>
      </section>
    ) : (
      <section className="panel registration-panel">
        {step === 1 && <>
          <div className="section-tabs" role="group" aria-label="매물 등록 방식">
            <button className={!photo ? "selected" : ""} aria-pressed={!photo} onClick={() => changeMethod("manual")}>직접 입력</button>
            <button className={photo ? "selected" : ""} aria-pressed={photo} onClick={() => changeMethod("photo")}>사진으로 등록</button>
          </div>
          <form onSubmit={e => { e.preventDefault(); if (validateSource()) setStep(2); }}>
            <h2>{photo ? "광고 사진을 선택하세요" : "사진 없이 직접 등록하세요"}</h2>
            <p className="helper">{photo ? "사진을 보면서 매물 조건을 확인하는 등록 방식입니다." : "주소를 입력한 다음 금액·면적·층수를 직접 작성하세요. 사진과 광고 글은 필요하지 않습니다."}</p>
            {photo ? <>
              <Field label="광고 이미지"><input type="file" accept="image/jpeg,image/png" onChange={selectImage} /></Field>
              <p className="helper">JPG·PNG / 파일당 10MB 이하. 이미지는 이번 등록 화면에서만 미리보기하며 저장되지 않습니다.</p>
              {preview && <img className="ad-preview" src={preview} alt="등록할 광고 원본 미리보기" />}
              {room?.attachmentName && !preview && <p className="helper">이전 첨부명: {room.attachmentName} · 이미지는 다시 선택해 주세요.</p>}
            </> : <>
              <Field label="광고 글"><textarea {...bind("original")} rows={4} maxLength={3000} placeholder="선택 사항입니다. 광고 글이 없어도 다음 단계로 갈 수 있습니다." /></Field>
              <p className="helper">광고 글은 보관하고 싶을 때만 입력하세요.</p>
            </>}
            <Field label="주소 / 건물명 *"><input {...bind("address")} required maxLength={160} /></Field>
            <div className="form-grid">
              <Field label="동"><input {...bind("buildingDong")} maxLength={100} placeholder="확인한 경우 입력" /></Field>
              <Field label="호수"><input {...bind("unitNumber")} maxLength={100} placeholder="모르면 비워두세요" /></Field>
            </div>
            <Field label="출처 URL"><input {...bind("sourceUrl")} type="url" maxLength={1000} placeholder="https://" /></Field>
            {error && <p role="alert" className="form-error">{error}</p>}
            <div className="modal-actions">
              <button className="secondary" type="button" onClick={() => save("DRAFT")}>임시 저장</button>
              <button className="primary" type="submit">값 확인으로 이동</button>
            </div>
          </form>
        </>}
        {step === 2 && <div className={photo ? "registration-review" : "manual-registration"}>
          {photo && <aside className="source-summary">
            <span className="eyebrow">ORIGINAL AD</span><h3>광고 원본</h3>
            {preview && <img className="ad-preview" src={preview} alt="확인 중인 광고 원본" />}
            {source.original && <p className="original">{source.original}</p>}
            <div className="notice">OCR·AI는 연동 준비 중입니다. 자동 추출된 값은 없으며 사진을 보고 직접 입력합니다.</div>
          </aside>}
          <div><RoomForm
            initialValues={{ ...values, ...source }}
            heading={photo ? "광고 값을 직접 확인하세요" : "매물 정보를 직접 입력하세요"}
            helperText={photo ? "선택한 사진과 대조해 값을 입력하세요. 모르는 값은 비워두세요." : "사진 없이 확인한 조건을 입력하세요. 광고 원문은 선택 사항이며 모르는 값은 비워두세요."}
            onCancel={input => keepValues(input, 1)}
            onPreview={input => keepValues(input, 3)}
          /></div>
        </div>}
        {step === 3 && <>
          <h2>이 내용으로 저장할까요?</h2>
          <p className="helper">입력하지 않은 값은 정보 없음으로 유지합니다. 공식 건물 조회는 아직 수행하지 않습니다.</p>
          <dl className="confirmation-grid">
            {[
              ["등록 방식", photo ? "사진으로 등록" : "직접 입력"],
              ["매물 이름", values.name], ["주소", source.address],
              ["동·호수", [source.buildingDong, source.unitNumber].filter(Boolean).join(" / ") || "정보 없음"],
              ["보증금", money(values.deposit)], ["월세", money(values.rent)], ["관리비", money(values.fee)],
              ["면적", values.area == null ? "정보 없음" : `${values.area}㎡ · ${values.areaType}`],
              ["층수", values.floor == null ? "정보 없음" : `${values.floor}층`],
            ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
          </dl>
          <div className="notice">{photo ? "로컬 시연 데이터로 이 브라우저에 저장합니다. 첨부 이미지는 저장하지 않습니다." : "직접 입력한 조건을 이 브라우저에 저장합니다. 사진이나 광고 원문이 없어도 저장할 수 있습니다."}</div>
          <div className="modal-actions">
            <button className="secondary" onClick={() => setStep(2)}>내용 수정</button>
            <button className="primary" onClick={() => save("CONFIRMED")}>확정 저장</button>
          </div>
        </>}
      </section>
    )}
  </>;
}
