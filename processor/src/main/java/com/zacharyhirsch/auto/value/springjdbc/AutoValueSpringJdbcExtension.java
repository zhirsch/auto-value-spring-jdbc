package com.zacharyhirsch.auto.value.springjdbc;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Maps.immutableEntry;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

@AutoService(AutoValueExtension.class)
public class AutoValueSpringJdbcExtension extends AutoValueExtension {

  private static final String ABSTRACT_SQL_PARAMETER_SOURCE_METHOD_NAME = "sqlParameterSource";
  private static final ImmutableSet<String> OPTIONAL_CLASS_NAMES =
      ImmutableSet.of(
          "java.util.Optional",
          "java.util.OptionalDouble",
          "java.util.OptionalInt",
          "java.util.OptionalLong");

  @Override
  public boolean applicable(Context context) {
    return context.autoValueClass().getAnnotation(AutoValueSpringJdbc.class) != null;
  }

  @Override
  public Set<ExecutableElement> consumeMethods(Context context) {
    ImmutableSet.Builder<ExecutableElement> builder = ImmutableSet.builder();
    for (ExecutableElement element : context.abstractMethods()) {
      if (element.getSimpleName().contentEquals(ABSTRACT_SQL_PARAMETER_SOURCE_METHOD_NAME)) {
        builder.add(element);
      }
    }
    return builder.build();
  }

  @Override
  public String generateClass(
      Context context, String className, String classToExtend, boolean isFinal) {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(ClassName.get(context.packageName(), className))
            .addModifiers(isFinal ? Modifier.FINAL : Modifier.ABSTRACT)
            .superclass(ClassName.get(context.packageName(), classToExtend))
            .addMethod(generateConstructor(context))
            .addType(generateRowMapper(context));
    context.abstractMethods().stream()
        .filter(x -> x.getSimpleName().contentEquals(ABSTRACT_SQL_PARAMETER_SOURCE_METHOD_NAME))
        .findFirst()
        .ifPresent(x -> builder.addMethod(generateSqlParameterSourceMethod(context, x)));
    return JavaFile.builder(context.packageName(), builder.build()).build().toString();
  }

  private MethodSpec generateConstructor(Context context) {
    ImmutableList<ParameterSpec> params =
        context.properties().entrySet().stream()
            .map(e -> immutableEntry(e.getKey(), TypeName.get(e.getValue().getReturnType())))
            .map(e -> ParameterSpec.builder(e.getValue(), e.getKey()).build())
            .collect(toImmutableList());
    return MethodSpec.constructorBuilder()
        .addParameters(params)
        .addStatement(
            params.stream()
                .map(x -> CodeBlock.of("$N", x))
                .collect(CodeBlock.joining(", ", "super(", ")")))
        .build();
  }

  private MethodSpec generateSqlParameterSourceMethod(
      Context context, ExecutableElement methodToOverride) {
    MethodSpec.Builder builder =
        MethodSpec.overriding(methodToOverride)
            .addStatement("$1T result = new $1T()", MapSqlParameterSource.class);
    for (Map.Entry<String, ExecutableElement> property : context.properties().entrySet()) {
      builder.addStatement(
          isOptional(property.getValue().getReturnType())
              ? "result.addValue($S, $L().orElse(null))"
              : "result.addValue($S, $L())",
          property.getKey(),
          property.getValue().getSimpleName());
    }
    builder.addStatement("return result");
    return builder.build();
  }

  private TypeSpec generateRowMapper(Context context) {
    TypeName autoValueClass = TypeName.get(context.autoValueClass().asType());
    String bodyFmt =
        ""
            + "return new $L("
            + context.properties().values().stream()
                .map(
                    v ->
                        isOptional(v.getReturnType())
                            ? "Optional.ofNullable(rs.getObject($S, $T.class))"
                            : "rs.getObject($S, $T.class)")
                .collect(joining(", "))
            + ")";
    ImmutableList<Object> bodyArgs =
        ImmutableList.builder()
            .add(getFinalClassName(context))
            .addAll(
                context.properties().entrySet().stream()
                    .map(e -> immutableEntry(e.getKey(), e.getValue().getReturnType()))
                    .flatMap(
                        e ->
                            Stream.of(
                                e.getKey(),
                                isOptional(e.getValue())
                                    ? MoreTypes.asDeclared(e.getValue()).getTypeArguments().get(0)
                                    : TypeName.get(e.getValue()).box()))
                    .iterator())
            .build();
    return TypeSpec.classBuilder("RowMapper")
        .addModifiers(Modifier.STATIC, Modifier.FINAL)
        .addSuperinterface(
            ParameterizedTypeName.get(ClassName.get(RowMapper.class), autoValueClass))
        .addMethod(
            MethodSpec.methodBuilder("mapRow")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(ResultSet.class, "rs").build())
                .addParameter(ParameterSpec.builder(TypeName.INT, "rowNum").build())
                .addException(SQLException.class)
                .addStatement(bodyFmt, bodyArgs.toArray())
                .returns(autoValueClass)
                .build())
        .build();
  }

  private boolean isOptional(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType declaredType = MoreTypes.asDeclared(type);
    TypeElement typeElement = MoreElements.asType(declaredType.asElement());
    return OPTIONAL_CLASS_NAMES.contains(typeElement.getQualifiedName().toString())
        && typeElement.getTypeParameters().size() == declaredType.getTypeArguments().size();
  }

  private static String getFinalClassName(Context context) {
    TypeElement autoValueClass = context.autoValueClass();
    StringBuilder name = new StringBuilder(autoValueClass.getSimpleName().toString());

    Element enclosingElement = autoValueClass.getEnclosingElement();
    while (enclosingElement instanceof TypeElement) {
      name.insert(0, enclosingElement.getSimpleName().toString() + "_");
      enclosingElement = enclosingElement.getEnclosingElement();
    }

    return "AutoValue_" + name;
  }
}
