import { useEffect, useRef } from "react";
export default function Modal({ title, onClose, children }) {
  const dialog = useRef();
  useEffect(() => {
    const el = dialog.current;
    el.showModal();
    return () => el.close();
  }, []);
  return (
    <dialog
      ref={dialog}
      aria-label={title}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="modal-top">
        <span className="eyebrow">ONEROOM CHECK</span>
        <button className="close" onClick={onClose} aria-label="닫기">
          ×
        </button>
      </div>
      {children}
    </dialog>
  );
}
