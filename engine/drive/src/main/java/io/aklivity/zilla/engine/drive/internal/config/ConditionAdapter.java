/*
 * Copyright 2021-2021 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.engine.drive.internal.config;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.engine.drive.config.Condition;
import io.aklivity.zilla.engine.drive.config.ConditionAdapterSpi;

public class ConditionAdapter implements JsonbAdapter<Condition, JsonObject>
{
    private final Map<String, ConditionAdapterSpi> delegatesByName;

    private ConditionAdapterSpi delegate;

    public ConditionAdapter()
    {
        delegatesByName = ServiceLoader
            .load(ConditionAdapterSpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(ConditionAdapterSpi::type, identity()));
    }

    public void adaptType(
        String type)
    {
        delegate = delegatesByName.get(type);
    }

    @Override
    public JsonObject adaptToJson(
        Condition condition)
    {
        return delegate != null ? delegate.adaptToJson(condition) : null;
    }

    @Override
    public Condition adaptFromJson(
        JsonObject object)
    {
        return delegate != null ? delegate.adaptFromJson(object) : null;
    }
}
