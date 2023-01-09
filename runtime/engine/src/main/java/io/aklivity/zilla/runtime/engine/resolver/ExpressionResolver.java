package io.aklivity.zilla.runtime.engine.resolver;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class ExpressionResolver
{
    private final Map<String, ExpressionResolverSpi> resolverSpis;

    public static ExpressionResolver instantiate()
    {
        return instantiate(load(ExpressionResolverSpi.class));
    }

    public String resolve(
            String name,
            String config)
    {
        requireNonNull(name, "name");

        ExpressionResolverSpi resolverSpi = requireNonNull(resolverSpis.get(name), () -> "Unrecognized resolver name: " + name);

        return resolverSpi.resolve(config);
    }

    private static ExpressionResolver instantiate(ServiceLoader<ExpressionResolverSpi> resolvers)
    {
        Map<String, ExpressionResolverSpi> resolverSpisByName = new HashMap<>();
        resolvers.forEach(resolverSpi -> resolverSpisByName.put(resolverSpi.name(), resolverSpi));

        return new ExpressionResolver(unmodifiableMap(resolverSpisByName));
    }

    public Iterable<String> names()
    {
        return resolverSpis.keySet();
    }

    public ExpressionResolver(Map<String, ExpressionResolverSpi> resolverSpis)
    {
        this.resolverSpis = resolverSpis;
    }

}
