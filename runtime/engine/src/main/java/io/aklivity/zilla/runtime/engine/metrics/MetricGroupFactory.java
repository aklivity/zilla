package io.aklivity.zilla.runtime.engine.metrics;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class MetricGroupFactory
{
    private final Map<String, MetricGroupFactorySpi> factorySpis;

    public static MetricGroupFactory instantiate()
    {
        return instantiate(load(MetricGroupFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public MetricGroup create(
        String type,
        Configuration config)
    {
        requireNonNull(type, "type");

        MetricGroupFactorySpi factorySpi = requireNonNull(factorySpis.get(type), () -> "Unrecognized metrics type: " + type);

        return factorySpi.create(config);
    }

    private static MetricGroupFactory instantiate(
        ServiceLoader<MetricGroupFactorySpi> factories)
    {
        Map<String, MetricGroupFactorySpi> factorySpisByName = new HashMap<>();
        factories.forEach(factorySpi -> factorySpisByName.put(factorySpi.type(), factorySpi));

        return new MetricGroupFactory(unmodifiableMap(factorySpisByName));
    }

    private MetricGroupFactory(
        Map<String, MetricGroupFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
