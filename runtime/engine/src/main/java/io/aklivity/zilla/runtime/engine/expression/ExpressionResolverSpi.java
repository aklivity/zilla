package io.aklivity.zilla.runtime.engine.expression;

public interface ExpressionResolverSpi
{
    String name();

    String resolve(
            String var);

}
