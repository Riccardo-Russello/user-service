CREATE TABLE person (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        first_name VARCHAR(30) NOT NULL,
                        last_name VARCHAR(30) NOT NULL,
                        date_of_birth DATE NOT NULL,
                        username VARCHAR(50) NOT NULL,
                        password VARCHAR(100) NOT NULL,
                        role ENUM('admin', 'user') NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_person_username (username)
) ENGINE=InnoDB;