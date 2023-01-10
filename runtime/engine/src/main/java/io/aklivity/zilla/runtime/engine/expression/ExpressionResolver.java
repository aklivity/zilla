package io.aklivity.zilla.runtime.engine.expression;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

public class ExpressionResolver
{
    private final Map<String, ExpressionResolverSpi> resolverSpis;
    private final Map<String, String> contextMap;
    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("\\$\\{\\{\\s*([^\\s\\}]*)\\.([^\\s\\}]*)\\s*\\}\\}");

    public static ExpressionResolver instantiate()
    {
        return instantiate(load(ExpressionResolverSpi.class));
    }

    public String resolve(String config)
    {
        return EXPRESSION_PATTERN.matcher(config)
                .replaceAll(r -> Optional.ofNullable(requireNonNull(resolverSpis.get(contextMap.get(r.group(1))),
                        () -> "Unrecognized resolver name: " + contextMap.get(r.group(1)))
                        .resolve(r.group(2))).orElse(""));
    }

    private static ExpressionResolver instantiate(ServiceLoader<ExpressionResolverSpi> resolvers)
    {
        Map<String, ExpressionResolverSpi> resolverSpisByName = new HashMap<>();
        Map<String, String> contextSpisMap = new HashMap<>();
        contextSpisMap.put("env", EnvironmentResolverSpi.class.getName());
        resolvers.forEach(resolverSpi -> resolverSpisByName.put(resolverSpi.name(), resolverSpi));
        return new ExpressionResolver(unmodifiableMap(resolverSpisByName), unmodifiableMap(contextSpisMap));
    }

    private Iterable<String> names()
    {
        return resolverSpis.keySet();
    }

    private ExpressionResolver(Map<String, ExpressionResolverSpi> resolverSpis, Map<String, String> contextMap)
    {
        this.resolverSpis = resolverSpis;
        this.contextMap = contextMap;
    }

}
