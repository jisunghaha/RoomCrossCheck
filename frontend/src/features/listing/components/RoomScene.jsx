export function RoomScene({ theme }) {
  return (
    <div
      className={`room-scene ${theme}`}
      role="img"
      aria-label="가상 원룸 공간 일러스트"
    >
      <div className="window">
        <i />
        <i />
      </div>
      <div className="poster" />
      <div className="lamp" />
      <div className="bed">
        <i />
        <b />
      </div>
      <div className="rug" />
      <div className="plant">
        <i />
        <b />
      </div>
      <span className="scene-label">CONCEPT ROOM · 가상 이미지</span>
    </div>
  );
}
