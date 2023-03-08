package io.aklivity.zilla.runtime.engine.metrics;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class MetricsFactory
{
    private final Map<String, MetricsFactorySpi> factorySpis;

    public static MetricsFactory instantiate()
    {
        return instantiate(load(MetricsFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public Metrics create(
        String type,
        Configuration config)
    {
        requireNonNull(type, "type");

        MetricsFactorySpi factorySpi = requireNonNull(factorySpis.get(type), () -> "Unrecognized metrics type: " + type);

        return factorySpi.create(config);
    }

    private static MetricsFactory instantiate(
        ServiceLoader<MetricsFactorySpi> factories)
    {
        Map<String, MetricsFactorySpi> factorySpisByName = new HashMap<>();
        factories.forEach(factorySpi -> factorySpisByName.put(factorySpi.type(), factorySpi));

        return new MetricsFactory(unmodifiableMap(factorySpisByName));
    }

    private MetricsFactory(
        Map<String, MetricsFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
