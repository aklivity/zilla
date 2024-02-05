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

import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import org.agrona.collections.LongHashSet;

import java.util.List;
import java.util.stream.Collectors;

public final class OpenapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final OpenapiOptionsConfig options;
    private final LongHashSet httpOrigins;


    public OpenapiBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = OpenapiOptionsConfig.class.cast(binding.options);

        httpOrigins = binding.composites.stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("http"))
            .map(b -> b.id)
            .collect(Collectors.toCollection(LongHashSet::new));
    }

    public boolean isCompositeBinding(
        long originId)
    {
        return httpOrigins.contains(originId);
    }
}
