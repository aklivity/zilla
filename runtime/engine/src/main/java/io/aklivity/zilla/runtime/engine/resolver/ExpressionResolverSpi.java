package io.aklivity.zilla.runtime.engine.resolver;

public interface ExpressionResolverSpi
{
    String name();

    String resolve(String config);

}
