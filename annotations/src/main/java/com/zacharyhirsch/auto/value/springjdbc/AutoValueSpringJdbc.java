package com.zacharyhirsch.auto.value.springjdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking a {@link @AutoValue} class as supporting Spring JDBC operations.
 *
 * <p>Marking an {@link @AutoValue} class with this annotation will enable generation of a {@link
 * org.springframework.jdbc.core.RowMapper} and {@link
 * org.springframework.jdbc.core.namedparam.SqlParameterSource} that can read and write the contents
 * of the {@link @AutoValue} to a database.
 *
 * <p>In addition to adding this annotation, the {@link @AutoValue} class should add the following
 * methods:
 *
 * <pre>
 *   public abstract SqlParameterSource sqlParameterSource();
 *   public static RowMapper<T> rowMapper() { return new AutoValue_T.RowMapper(); }
 * </pre>
 *
 * This methods expose the necessary functionality. They can then be used like:
 *
 * <pre>
 *   NamedParameterJdbcTemplate jdbc = ...;
 *
 *   // Write
 *   AutoValueT x = ...;
 *   jdbc.update(SQL, x.sqlParameterSource());
 *
 *   // Read
 *   AutoValueT y = jdbc.queryForObject(SQL, AutoValueT.rowMapper());
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AutoValueSpringJdbc {}
