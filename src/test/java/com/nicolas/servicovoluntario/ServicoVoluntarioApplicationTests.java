package com.nicolas.servicovoluntario;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Sobe o contexto inteiro e aplica as migrations Flyway num PostgreSQL real; é ignorado sem Docker. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ServicoVoluntarioApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Test
    void contextLoads() {
    }

}
