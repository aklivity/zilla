package io.aklivity.zilla.runtime.engine.test.internal.expression;

import io.aklivity.zilla.runtime.engine.expression.ExpressionResolverSpi;

public class TestExpressionResolverSpi implements ExpressionResolverSpi
{

    @Override
    public String name()
    {
        return "test";
    }

    @Override
    public String resolve(
        String var)
    {
        return "PASSWORD".equals(var) ? "ACTUALPASSWORD" : "";
    }
}
