-- DB 생성 권한이 있는 MySQL 계정으로 한 번 실행한다. 기존 DB를 삭제하지 않는다.
-- 테이블은 backend의 mvn -Pdb flyway:migrate로 적용하고 여기서는 생성하지 않는다.
CREATE DATABASE IF NOT EXISTS roomcrosscheck
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE roomcrosscheck;
