# 주석 작성 기준

주석은 한국어를 기본으로 사용한다. API, JDBC, CSRF 같은 기술명과 코드 식별자는 필요한 경우 영어를 유지한다. 이는 팀의 언어 선택이며 참고 가이드가 한국어를 요구하는 것은 아니다.

## 언어별 근거

| 대상 | 참고 문서 | 적용 방식 |
|---|---|---|
| Java | [Java 21 Javadoc 명세](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html) | 선언 바로 위에 `/** ... */`, 첫 문장으로 역할 요약, 필요한 `@param`·`@return`·`@throws` 작성 |
| JavaScript | [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html#jsdoc) | 공개 함수는 JSDoc, 설정과 구현 이유는 일반 주석으로 설명 |
| MySQL | [MySQL 8.4 주석 문법](https://dev.mysql.com/doc/refman/8.4/en/comments.html) | `-- ` 뒤 공백을 포함한 줄 주석 또는 `/* ... */` 사용 |

Javadoc과 MySQL 문서는 문법 명세이고 Google 문서는 스타일 가이드다. 목적·단위·예외·실행 범위를 한국어로 자세히 설명하는 것은 이 프로젝트의 팀 규칙이다. SQL에서 `/*! ... */`는 실행될 수 있는 MySQL 확장 문법이므로 일반 설명 주석으로 사용하지 않는다.

앞으로 주석을 수정할 때 해당 언어의 근거 문서를 확인하고 아래 규칙을 적용한다. 이미 적용된 Flyway SQL의 주석을 고치면 체크섬이 바뀌므로 설명 보완은 별도 문서에 기록한다.

## 작성 규칙

- 클래스·공개 계약의 Javadoc은 역할을 짧게 요약하고 필요한 제약·실패 조건을 설명한다.
- 구현 주석은 코드만으로 드러나지 않는 이유, 호환성, 데이터 단위·누락값, 권한·트랜잭션 경계를 설명한다.
- 메서드 이름과 코드 동작을 반복하는 주석, 줄마다 붙이는 설명, 장식용 구분선은 생략한다.
- `@param`, `@return`, `@throws`는 계약 이해에 도움이 될 때 사용한다. 단순 getter에 형식만 채우지 않는다.
- 주석은 설명 대상 바로 위에 두고 코드 변경 시 함께 갱신한다. 개발 예외는 적용 프로필·이유를 명시한다.
- SQL은 테이블의 역할·삭제 관계·단위를 간단히 적고 영어 상태 code는 그대로 둔다.

Javadoc의 요약·API 계약 작성은 [Oracle 가이드](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html)를, 주석 형식과 Javadoc 구분은 [Google Java Style](https://google.github.io/styleguide/javaguide.html#s4.8.6-comments)을 참고했다. 코드의 의도·제약 위주로 간결하게 설명하는 항목은 이 프로젝트의 작성 기준이다. 전체 Google Java Style을 그대로 적용한다는 의미는 아니다.
