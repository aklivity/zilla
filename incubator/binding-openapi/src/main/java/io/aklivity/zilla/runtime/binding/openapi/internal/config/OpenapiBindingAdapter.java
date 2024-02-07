package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

final class OpenapiBindingAdapter implements CompositeBindingAdapterSpi
{
    private final UnaryOperator<BindingConfig> composite;

    OpenapiBindingAdapter()
    {
        Map<KindConfig, UnaryOperator<BindingConfig>> composites = new EnumMap<>(KindConfig.class);
        composites.put(SERVER, new OpenapiServerCompositeBindingAdapter()::adapt);
        composites.put(CLIENT, new OpenapiClientCompositeBindingAdapter()::adapt);
        UnaryOperator<BindingConfig> composite = binding -> composites
            .getOrDefault(binding.kind, UnaryOperator.identity()).apply(binding);
        this.composite = composite;
    }

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        return composite.apply(binding);
    }
}
