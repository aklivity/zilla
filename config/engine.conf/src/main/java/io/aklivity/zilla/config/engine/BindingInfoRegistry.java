/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.engine;

import static java.util.ServiceLoader.load;

import java.util.Map;
import java.util.stream.Stream;

import io.aklivity.zilla.config.engine.factory.Factory;

public final class BindingInfoRegistry extends Factory
{
    private final Map<String, BindingInfo> infosByType;

    public static BindingInfoRegistry instantiate()
    {
        return instantiate(load(BindingInfo.class), BindingInfoRegistry::new);
    }

    public Stream<BindingInfo> stream()
    {
        return infosByType.values().stream();
    }

    public BindingInfo lookup(
        String type)
    {
        return infosByType.get(type);
    }

    private BindingInfoRegistry(
        Map<String, BindingInfo> infosByType)
    {
        this.infosByType = infosByType;
    }
}
