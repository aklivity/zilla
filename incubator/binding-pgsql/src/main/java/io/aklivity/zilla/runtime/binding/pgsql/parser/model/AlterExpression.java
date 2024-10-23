package io.aklivity.zilla.runtime.binding.pgsql.parser.model;

public record AlterExpression(
    Operation operation,
    String columnName,
    String columnType)
{
}
