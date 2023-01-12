package io.aklivity.zilla.runtime.engine.internal.expression;

import io.aklivity.zilla.runtime.engine.expression.ExpressionResolverSpi;

public class EnvironmentResolverSpi implements ExpressionResolverSpi
{

    @Override
    public String name()
    {
        return "env";
    }

    @Override
    public String resolve(
        String var)
    {
        return System.getenv(var);
    }
}
