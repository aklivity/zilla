package io.aklivity.zilla.runtime.engine.expression;

import java.util.Optional;
import java.util.function.UnaryOperator;

public class EnvironmentResolverSpi implements ExpressionResolverSpi
{

    @Override
    public String name()
    {
        return EnvironmentResolverSpi.class.getName();
    }

    @Override
    public String resolve(
            String var)
    {
        UnaryOperator<String> env = System::getenv;
        return Optional.ofNullable(env.apply(var)).orElse("");
    }
}
