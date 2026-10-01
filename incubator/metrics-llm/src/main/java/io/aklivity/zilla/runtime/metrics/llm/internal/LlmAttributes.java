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
package io.aklivity.zilla.runtime.metrics.llm.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;

final class LlmAttributes
{
    static final int STATUS_ABSENT = -1;
    static final int STATUS_OK = 200;

    static final LlmAttributes NONE = new LlmAttributes(Collections.emptyList(), null, null);

    private static final Pattern STATUS = Pattern.compile("\\$\\{llm\\.status\\}");
    private static final Pattern IDENTITY = Pattern.compile(
        "\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9._:\\-]*)'\\]\\.identity\\}");
    private static final Pattern ATTRIBUTE = Pattern.compile(
        "\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9._:\\-]*)'\\]\\.attributes\\.([a-zA-Z]+[a-zA-Z0-9._:\\-]*)\\}");

    private final List<String> statusNames;
    private final List<GuardedBinding> guardedBindings;
    private final EngineContext context;
    private final ToLongFunction<String> resolveId;
    private final Map<String, GuardHandler> guards;
    private final Long2ObjectHashMap<Map<String, String>> requests;

    LlmAttributes(
        List<AttributeConfig> attributes,
        EngineContext context,
        ToLongFunction<String> resolveId)
    {
        this.statusNames = new ArrayList<>();
        this.guardedBindings = new ArrayList<>();
        this.context = context;
        this.resolveId = resolveId;
        this.guards = new Object2ObjectHashMap<>();
        this.requests = new Long2ObjectHashMap<>();

        for (AttributeConfig attribute : attributes)
        {
            final Matcher status = STATUS.matcher(attribute.value);
            final Matcher identity = IDENTITY.matcher(attribute.value);
            final Matcher guarded = ATTRIBUTE.matcher(attribute.value);
            if (status.matches())
            {
                statusNames.add(attribute.name);
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
    }

    void request(
        long exchangeId,
        long authorization)
    {
        if (!guardedBindings.isEmpty())
        {
            final Map<String, String> values = new Object2ObjectHashMap<>();
            for (GuardedBinding binding : guardedBindings)
            {
                final GuardHandler guard = supplyGuard(binding.guard);
                if (guard != null)
                {
                    final String value = binding.attribute == null
                        ? guard.identity(authorization)
                        : guard.attribute(authorization, binding.attribute);

                    if (value != null)
                    {
                        values.put(binding.name, value);
                    }
                }
            }
            requests.put(exchangeId, values);
        }
    }

    int attributesId(
        long exchangeId,
        int status)
    {
        int attributesId = 0;

        if (!statusNames.isEmpty() || !guardedBindings.isEmpty())
        {
            final Map<String, String> values = new Object2ObjectHashMap<>();
            final Map<String, String> request = requests.get(exchangeId);
            if (request != null)
            {
                values.putAll(request);
            }

            if (status != STATUS_ABSENT)
            {
                final String value = Integer.toString(status);
                for (String name : statusNames)
                {
                    values.put(name, value);
                }
            }

            attributesId = labelId(values);
        }

        return attributesId;
    }

    void release(
        long exchangeId)
    {
        if (!guardedBindings.isEmpty())
        {
            requests.remove(exchangeId);
        }
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

    private int labelId(
        Map<String, String> values)
    {
        int labelId = 0;

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

            labelId = context.supplyTypeId(label.toString());
        }

        return labelId;
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
