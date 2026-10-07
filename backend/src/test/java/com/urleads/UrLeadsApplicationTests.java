package com.urleads;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de carga del contexto basico de la aplicacion (scaffold).
 *
 * Excluye intencionalmente la autoconfiguracion de DataSource y Flyway para verificar
 * la inicializacion de los componentes de Spring sin requerir una base de datos PostgreSQL
 * en ejecucion. Esta prueba no constituye prueba de conectividad con la base de datos
 * (dicha verificacion corresponde a las pruebas de integracion en DatabaseMigrationIT).
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
class UrLeadsApplicationTests {

    @Test
    void contextLoads() {
    }
}
