package io.aklivity.zilla.runtime.engine.expression;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

public final class ExpressionResolver
{
    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("\\$\\{\\{\\s*([^\\s\\}]*)\\.([^\\s\\}]*)\\s*\\}\\}");

    private final Map<String, ExpressionResolverSpi> resolverSpis;

    public static ExpressionResolver instantiate()
    {
        return instantiate(load(ExpressionResolverSpi.class));
    }

    public String resolve(
        String config)
    {
        return EXPRESSION_PATTERN.matcher(config)
                .replaceAll(r -> resolve(r.group(1), r.group(2)));
    }

    private String resolve(
        String context,
        String var)
    {
        ExpressionResolverSpi resolver = requireNonNull(resolverSpis.get(context), "Unrecognized resolver name: " + context);
        String value = resolver.resolve(var);
        return value != null ? value : "";
    }

    private static ExpressionResolver instantiate(
        ServiceLoader<ExpressionResolverSpi> resolvers)
    {
        Map<String, ExpressionResolverSpi> resolverSpisByName = new HashMap<>();
        resolvers.forEach(resolverSpi -> resolverSpisByName.put(resolverSpi.name(), resolverSpi));
        return new ExpressionResolver(unmodifiableMap(resolverSpisByName));
    }

    private Iterable<String> names()
    {
        return resolverSpis.keySet();
    }

    private ExpressionResolver(
        Map<String, ExpressionResolverSpi> resolverSpis)
    {
        this.resolverSpis = resolverSpis;
    }

}
