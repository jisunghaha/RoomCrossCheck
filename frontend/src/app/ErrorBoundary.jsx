import { Component } from "react";

/**
 * 화면 렌더링 중 예외가 나도 흰 화면 대신 안내와 새로고침 버튼을 보여 준다.
 * 오류 내용은 개발자 도구 콘솔에만 남기고 화면에는 사용자 안내만 표시한다.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { failed: false };
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error, info) {
    console.error("화면 오류", error, info?.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <main className="empty large" role="alert">
        <h2>화면을 표시하는 중 문제가 생겼어요</h2>
        <p>새로고침해도 계속되면 사용 중인 브라우저와 화면을 알려 주세요. 저장한 자료는 서버에 그대로 있습니다.</p>
        <button className="primary" onClick={() => location.reload()}>새로고침</button>
      </main>
    );
  }
}
