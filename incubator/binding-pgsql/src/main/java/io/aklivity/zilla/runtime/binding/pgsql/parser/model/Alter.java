package io.aklivity.zilla.runtime.binding.pgsql.parser.model;

import java.util.List;

public record Alter(
    String name,
    List<AlterExpression> alterExpressions)
{
}
