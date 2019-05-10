package com.zacharyhirsch.auto.value.springjdbc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestExecutionListeners({
  DependencyInjectionTestExecutionListener.class,
  FlywayTestExecutionListener.class
})
class ExampleTest {

  @Autowired private DataSource dataSource;
  private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @BeforeEach
  @FlywayTest
  void prepareDatabase() {
    namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
  }

  @Test
  void readingAndWritingSucceeds() {
    Example write = Example.builder().setOne(1).setTwo("two").setThree(3.0).build();

    int written =
        namedParameterJdbcTemplate.update(
            "INSERT INTO example(one, two, three) VALUES(:one, :two, :three);",
            write.sqlParameterSource());

    assertEquals(1, written);

    Example read =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT one, two, three FROM example WHERE one = :one",
            write.sqlParameterSource(),
            Example.rowMapper());

    assertNotNull(read);
    assertEquals(1, read.getOne());
    assertThat(read.getTwo()).hasValue("two");
    assertEquals(3.0, read.getThree());
  }

  @Test
  void readingAndWritingNullAndEmptySucceeds() {
    Example write = Example.builder().setOne(1).build();

    int written =
        namedParameterJdbcTemplate.update(
            "INSERT INTO example(one, two, three) VALUES(:one, :two, :three);",
            write.sqlParameterSource());

    assertEquals(1, written);

    Example read =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT one, two, three FROM example WHERE one = :one",
            write.sqlParameterSource(),
            Example.rowMapper());

    assertNotNull(read);
    assertEquals(1, read.getOne());
    assertThat(read.getTwo()).isEmpty();
    assertThat(read.getThree()).isNull();
  }

  @Test
  void readingAndWritingNestedSucceeds() {
    Example.Nested write = Example.Nested.create("one");

    int written =
        namedParameterJdbcTemplate.update(
            "INSERT INTO nested(one) VALUES(:one);", write.sqlParameterSource());

    assertEquals(1, written);

    Example.Nested read =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT one FROM nested WHERE one = :one",
            write.sqlParameterSource(),
            Example.Nested.rowMapper());

    assertNotNull(read);
    assertEquals("one", read.getOne());
  }

  @SpringBootConfiguration
  public static class TestConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
      return new EmbeddedDatabaseBuilder()
          .setType(EmbeddedDatabaseType.H2)
          .setName("testdb")
          .build();
    }

    @Bean
    @Primary
    public Flyway flyway(DataSource dataSource) {
      return Flyway.configure().dataSource(dataSource).load();
    }
  }
}
