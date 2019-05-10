package com.zacharyhirsch.auto.value.springjdbc;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@AutoValue
@AutoValueSpringJdbc
abstract class Example {

  abstract int getOne();

  abstract Optional<String> getTwo();

  @Nullable
  abstract Double getThree();

  abstract SqlParameterSource sqlParameterSource();

  static RowMapper<Example> rowMapper() {
    return new AutoValue_Example.RowMapper();
  }

  static Builder builder() {
    return new AutoValue_Example.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setOne(int one);

    abstract Builder setTwo(String two);

    abstract Builder setThree(Double three);

    abstract Example build();
  }

  @AutoValue
  @AutoValueSpringJdbc
  abstract static class Nested {

    abstract String getOne();

    static Nested create(String one) {
      return new AutoValue_Example_Nested(one);
    }

    abstract SqlParameterSource sqlParameterSource();

    static RowMapper<Nested> rowMapper() {
      return new AutoValue_Example_Nested.RowMapper();
    }
  }
}
