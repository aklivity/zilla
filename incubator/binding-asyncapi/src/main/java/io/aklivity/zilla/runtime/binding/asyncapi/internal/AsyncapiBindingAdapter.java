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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CompositeBindingAdapterSpi;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class AsyncapiBindingAdapter implements CompositeBindingAdapterSpi
{
    private final UnaryOperator<BindingConfig> composite;

    AsyncapiBindingAdapter()
    {
        Map<KindConfig, UnaryOperator<BindingConfig>> composites = new EnumMap<>(KindConfig.class);
        composites.put(SERVER, new AsyncapiServerCompositeBindingAdapter()::adapt);
        composites.put(CLIENT, new AsyncapiClientCompositeBindingAdapter()::adapt);
        UnaryOperator<BindingConfig> composite = binding -> composites
            .getOrDefault(binding.kind, UnaryOperator.identity()).apply(binding);
        this.composite = composite;
    }

    @Override
    public String type()
    {
        return AsyncapiBinding.NAME;
    }

    @Override
    public BindingConfig adapt(
        BindingConfig binding)
    {
        return composite.apply(binding);
    }
}
