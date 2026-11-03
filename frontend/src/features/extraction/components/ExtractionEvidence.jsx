import { optionsSummary } from "../../../shared/lib/options.js";

const fields = [
  ["address", "주소"], ["depositWon", "보증금"], ["monthlyRentWon", "월세"],
  ["maintenanceFeeWon", "관리비"], ["areaSqm", "면적"], ["areaType", "면적 종류"],
  ["floor", "층수"], ["options", "옵션"],
];
function display(key, value) {
  if (key === "options") return optionsSummary(value);
  if (value == null) return "정보 없음";
  if (["depositWon", "monthlyRentWon", "maintenanceFeeWon"].includes(key)) return `${value.toLocaleString("ko-KR")}원`;
  if (key === "areaSqm") return `${value}㎡`;
  if (key === "floor") return `${value}층`;
  return value;
}

export default function ExtractionEvidence({ draft }) {
  const result = draft.extraction;
  if (!result) return null;
  return <section aria-label="최초 AI 추출값과 근거">
    <h3>최초 AI 추출값과 근거</h3>
    <p>AI가 제시한 근거입니다. 글에 문구가 있어도 추출값의 정확성을 보증하지 않습니다. 수정해도 이 기록은 유지됩니다.</p>
    {draft.originalText && <details><summary>보관된 광고 글</summary><pre style={{ whiteSpace: "pre-wrap" }}>{draft.originalText}</pre></details>}
    <details><summary>{draft.imageName ? "AI가 읽은 광고 글 (이미지 원본 대조 필요)" : "AI가 읽은 광고 글"}</summary>
      <pre style={{ whiteSpace: "pre-wrap" }}>{result.rawText || "읽은 글 기록 없음"}</pre>
    </details>
    <div style={{ overflowX: "auto" }}><table>
      <caption>항목별 AI 값·근거·확인 상태</caption>
      <thead><tr><th scope="col">항목</th><th scope="col">최초 AI 값</th><th scope="col">AI가 제시한 근거</th><th scope="col">확인 상태</th></tr></thead>
      <tbody>{fields.map(([key, label]) => {
        const item = result.evidence?.[key];
        return <tr key={key}><th scope="row">{label}</th><td>{display(key, result[key])}</td>
          <td>{item?.quote ? <><q>{item.quote}</q><p>{item.source === "INPUT_TEXT" ? "보관된 광고 글에서 찾은 문구" : "AI가 읽은 이미지 글에서 찾은 문구 · 원본 대조 필요"}</p></> : "근거 문구 없음"}</td>
          <td>{!item ? "근거 기록 없음" : item.status === "PRESENT" ? "근거 문구 있음" : item.status === "NOT_STATED" ? "AI 판단: 미기재" : "확인 필요"}
            {item?.reason && <p>AI 근거 검토 안내: {item.reason}</p>}</td>
        </tr>;
      })}</tbody>
    </table></div>
  </section>;
}
