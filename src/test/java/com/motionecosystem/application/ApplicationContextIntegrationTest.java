package com.motionecosystem.application;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class ApplicationContextIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    DataSource dataSource;

    @Test
    void startsWithRealPostgres() {
        assertThat(context).isNotNull();
        assertThat(dataSource).isNotNull();
    }
}
