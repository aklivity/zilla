package io.aklivity.zilla.runtime.engine.exporter;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import io.aklivity.zilla.runtime.engine.Configuration;

public final class ExporterFactory
{
    private final Map<String, ExporterFactorySpi> factorySpis;

    public static ExporterFactory instantiate()
    {
        return instantiate(load(ExporterFactorySpi.class));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public Exporter create(
        String type,
        Configuration config)
    {
        requireNonNull(type, "type");

        ExporterFactorySpi factorySpi = requireNonNull(factorySpis.get(type), () -> "Unrecognized exporter type: " + type);

        return factorySpi.create(config);
    }

    private static ExporterFactory instantiate(
        ServiceLoader<ExporterFactorySpi> factories)
    {
        Map<String, ExporterFactorySpi> factorySpisByName = new HashMap<>();
        factories.forEach(factorySpi -> factorySpisByName.put(factorySpi.type(), factorySpi));

        return new ExporterFactory(unmodifiableMap(factorySpisByName));
    }

    private ExporterFactory(
        Map<String, ExporterFactorySpi> factorySpis)
    {
        this.factorySpis = factorySpis;
    }
}
