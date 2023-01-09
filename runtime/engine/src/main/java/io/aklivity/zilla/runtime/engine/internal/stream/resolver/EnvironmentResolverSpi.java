package io.aklivity.zilla.runtime.engine.internal.stream.resolver;

import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.resolver.ExpressionResolverSpi;

public class EnvironmentResolverSpi implements ExpressionResolverSpi
{

    private static final Pattern MUSTACHE_PATTERN = Pattern.compile("\\$\\{\\{\\s*env\\.([^\\s\\}]*)\\s*\\}\\}");

    @Override
    public String name()
    {
        return EnvironmentResolverSpi.class.getName();
    }

    @Override
    public String resolve(String config)
    {
        UnaryOperator<String> env = System::getenv;
        return MUSTACHE_PATTERN
                .matcher(config)
                .replaceAll(r -> Optional.ofNullable(env.apply(r.group(1))).orElse(""));
    }
}
