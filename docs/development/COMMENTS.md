# 주석 작성 기준

주석은 한국어를 기본으로 사용한다. API, JDBC, CSRF 같은 기술명과 코드 식별자는 필요한 경우 영어를 유지한다. 이는 팀의 언어 선택이며 참고 가이드가 한국어를 요구하는 것은 아니다.

- 클래스·공개 계약의 Javadoc은 역할을 짧게 요약하고 필요한 제약·실패 조건을 설명한다.
- 구현 주석은 코드만으로 드러나지 않는 이유, 호환성, 데이터 단위·누락값, 권한·트랜잭션 경계를 설명한다.
- 메서드 이름과 코드 동작을 반복하는 주석, 줄마다 붙이는 설명, 장식용 구분선은 생략한다.
- `@param`, `@return`, `@throws`는 계약 이해에 도움이 될 때 사용한다. 단순 getter에 형식만 채우지 않는다.
- 주석은 설명 대상 바로 위에 두고 코드 변경 시 함께 갱신한다. 개발 예외는 적용 프로필·이유를 명시한다.
- SQL은 테이블의 역할·삭제 관계·단위를 간단히 적고 영어 상태 code는 그대로 둔다.

Javadoc의 요약·API 계약 작성은 [Oracle 가이드](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html)를, 주석 형식과 Javadoc 구분은 [Google Java Style](https://google.github.io/styleguide/javaguide.html#s4.8.6-comments)을 참고했다. 코드의 의도·제약 위주로 간결하게 설명하는 항목은 이 프로젝트의 작성 기준이다. 전체 Google Java Style을 그대로 적용한다는 의미는 아니다.
