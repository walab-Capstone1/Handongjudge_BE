-- MariaDB에 root로 접속
mysql -u root -p

-- 데이터베이스 생성
CREATE DATABASE handongjudge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 생성 및 권한 부여
CREATE USER 'handongjudge_user'@'localhost' IDENTIFIED BY 'your_database_password';
GRANT ALL PRIVILEGES ON handongjudge.* TO 'handongjudge_user'@'localhost';
FLUSH PRIVILEGES;

-- 확인
SHOW DATABASES;
SELECT User, Host FROM mysql.user WHERE User = 'handongjudge_user';