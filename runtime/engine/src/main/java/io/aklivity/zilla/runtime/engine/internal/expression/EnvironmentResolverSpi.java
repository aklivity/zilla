package io.aklivity.zilla.runtime.engine.internal.expression;

import java.util.Optional;
import java.util.function.UnaryOperator;

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
        UnaryOperator<String> env = System::getenv;
        return Optional.ofNullable(env.apply(var)).orElse("");
    }
}
