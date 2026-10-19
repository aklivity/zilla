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

import java.net.URL;
import java.util.List;
import java.util.ServiceLoader;

import jakarta.json.JsonObject;

import io.aklivity.zilla.config.engine.factory.Factory;
import io.aklivity.zilla.config.engine.factory.FactorySpi;

public interface BindingInfo extends FactorySpi
{
    default List<String> aliases()
    {
        return List.of();
    }

    URL schema();

    default List<BindingExtInfo> extensions()
    {
        return Factory.instantiate(ServiceLoader.load(BindingExtInfo.class))
            .stream()
            .filter(info -> info.type().equals(type()))
            .toList();
    }

    ConfigAdapter<OptionsConfig, JsonObject> options();

    default ConfigAdapter<ConditionConfig, JsonObject> condition()
    {
        return null;
    }

    default ConfigAdapter<WithConfig, JsonObject> with()
    {
        return null;
    }
}
