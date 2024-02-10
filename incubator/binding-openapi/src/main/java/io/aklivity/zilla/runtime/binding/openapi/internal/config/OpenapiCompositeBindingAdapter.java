/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.function.UnaryOperator.identity;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class OpenapiCompositeBindingAdapter implements CompositeBindingAdapterSpi
{
    private final UnaryOperator<BindingConfig> composite;

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    public OpenapiCompositeBindingAdapter()
    {
        Map<KindConfig, UnaryOperator<BindingConfig>> composites = new EnumMap<>(KindConfig.class);
        composites.put(SERVER, new OpenapiServerCompositeBindingAdapter()::adapt);
        composites.put(CLIENT, new OpenapiClientCompositeBindingAdapter()::adapt);
        UnaryOperator<BindingConfig> composite = binding -> composites
            .getOrDefault(binding.kind, identity()).apply(binding);
        this.composite = composite;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        return composite.apply(binding);
    }
}
