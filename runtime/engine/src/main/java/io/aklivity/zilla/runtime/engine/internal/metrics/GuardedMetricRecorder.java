/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.JsonValue;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.common.json.JsonStrings;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.metrics.MetricRecorder;

public final class GuardedMetricRecorder implements MetricRecorder
{
    public static final int DEFAULT_CAPACITY = 1024;

    private static final Pattern IDENTITY = Pattern.compile(
        "\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9._:\\-]*)'\\]\\.identity\\}");
    private static final Pattern ATTRIBUTE = Pattern.compile(
        "\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9._:\\-]*)'\\]\\.attributes\\.([a-zA-Z]+[a-zA-Z0-9._:\\-]*)\\}");
    private static final Pattern CREDENTIALS = Pattern.compile(
        "\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9._:\\-]*)'\\]\\.credentials\\}");

    private final List<GuardedBinding> guardedBindings;
    private final EngineContext context;
    private final ToLongFunction<String> resolveId;
    private final IntFunction<LongConsumer> supplyWriter;
    private final int capacity;
    private final Map<String, GuardHandler> guards;
    private final Long2ObjectHashMap<LongConsumer> writersByAuthorization;
    private final Map<String, String> values;
    private final LongConsumer defaultWriter;

    public GuardedMetricRecorder(
        EngineContext context,
        ToLongFunction<String> resolveId,
        List<AttributeConfig> attributes,
        IntFunction<LongConsumer> supplyWriter,
        int capacity)
    {
        this.guardedBindings = new ArrayList<>();
        this.context = context;
        this.resolveId = resolveId;
        this.supplyWriter = supplyWriter;
        this.capacity = capacity;
        this.guards = new Object2ObjectHashMap<>();
        this.writersByAuthorization = new Long2ObjectHashMap<>();
        this.values = new Object2ObjectHashMap<>();

        for (AttributeConfig attribute : attributes)
        {
            final Matcher identity = IDENTITY.matcher(attribute.value);
            final Matcher guarded = ATTRIBUTE.matcher(attribute.value);
            if (CREDENTIALS.matcher(attribute.value).matches())
            {
                throw new IllegalArgumentException("telemetry attribute must not reference credentials: " + attribute.name);
            }
            else if (identity.matches())
            {
                guardedBindings.add(new GuardedBinding(attribute.name, identity.group(1), null));
            }
            else if (guarded.matches())
            {
                guardedBindings.add(new GuardedBinding(attribute.name, guarded.group(1), guarded.group(2)));
            }
        }

        this.defaultWriter = supplyWriter.apply(0);
    }

    @Override
    public void record(
        long authorization,
        long value)
    {
        LongConsumer writer = defaultWriter;

        if (!guardedBindings.isEmpty())
        {
            writer = writersByAuthorization.get(authorization);
            if (writer == null)
            {
                if (writersByAuthorization.size() >= capacity)
                {
                    writersByAuthorization.clear();
                }
                writer = supplyWriter.apply(attributesId(authorization));
                writersByAuthorization.put(authorization, writer);
            }
        }

        writer.accept(value);
    }

    private int attributesId(
        long authorization)
    {
        int attributesId = 0;

        values.clear();
        for (GuardedBinding binding : guardedBindings)
        {
            final GuardHandler guard = supplyGuard(binding.guard);
            if (guard != null)
            {
                final String value = binding.attribute == null
                    ? guard.identity(authorization)
                    : asString(guard.attribute(authorization, binding.attribute));

                if (value != null)
                {
                    values.put(binding.name, value);
                }
            }
        }

        if (!values.isEmpty())
        {
            final String[] names = values.keySet().toArray(String[]::new);
            Arrays.sort(names);

            final StringBuilder label = new StringBuilder();
            for (String name : names)
            {
                if (label.length() > 0)
                {
                    label.append(',');
                }
                label.append(name).append('=').append(values.get(name));
            }

            attributesId = context.supplyTypeId(label.toString());
        }

        return attributesId;
    }

    private GuardHandler supplyGuard(
        String name)
    {
        GuardHandler guard = guards.get(name);
        if (guard == null)
        {
            guard = context.supplyGuard(resolveId.applyAsLong(name));
            if (guard != null)
            {
                guards.put(name, guard);
            }
        }
        return guard;
    }

    private static String asString(
        JsonValue value)
    {
        return value != null ? JsonStrings.asString(value) : null;
    }

    private static final class GuardedBinding
    {
        private final String name;
        private final String guard;
        private final String attribute;

        private GuardedBinding(
            String name,
            String guard,
            String attribute)
        {
            this.name = name;
            this.guard = guard;
            this.attribute = attribute;
        }
    }
}
