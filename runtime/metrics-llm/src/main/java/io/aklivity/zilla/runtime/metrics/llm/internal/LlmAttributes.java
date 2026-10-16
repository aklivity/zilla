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

import jakarta.json.JsonValue;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.common.json.JsonStrings;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.llm.internal.types.stream.LlmBeginExFW;

final class LlmAttributes
{
    static final LlmAttributes NONE = new LlmAttributes(Collections.emptyList(), null, null, 0);

    private static final Pattern IDENTITY = Pattern.compile(
        "\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9._:\\-]*)'\\]\\.identity\\}");
    private static final Pattern ATTRIBUTE = Pattern.compile(
        "\\$\\{guarded\\['([a-zA-Z]+[a-zA-Z0-9._:\\-]*)'\\]\\.attributes\\.([a-zA-Z]+[a-zA-Z0-9._:\\-]*)\\}");
    private static final Pattern MODEL = Pattern.compile("\\$\\{llm\\.(request|response)\\.model\\}");

    private final List<GuardedBinding> guardedBindings;
    private final List<String> requestModels;
    private final List<String> responseModels;
    private final EngineContext context;
    private final ToLongFunction<String> resolveId;
    private final int llmTypeId;
    private final Map<String, GuardHandler> guards;
    private final Long2ObjectHashMap<Map<String, String>> requests;
    private final Long2ObjectHashMap<Map<String, String>> responses;
    private final Map<String, String> combined;
    private final LlmBeginExFW llmBeginExRO = new LlmBeginExFW();

    LlmAttributes(
        List<AttributeConfig> attributes,
        EngineContext context,
        ToLongFunction<String> resolveId,
        int llmTypeId)
    {
        this.guardedBindings = new ArrayList<>();
        this.requestModels = new ArrayList<>();
        this.responseModels = new ArrayList<>();
        this.context = context;
        this.resolveId = resolveId;
        this.llmTypeId = llmTypeId;
        this.guards = new Object2ObjectHashMap<>();
        this.requests = new Long2ObjectHashMap<>();
        this.responses = new Long2ObjectHashMap<>();
        this.combined = new Object2ObjectHashMap<>();

        for (AttributeConfig attribute : attributes)
        {
            final Matcher identity = IDENTITY.matcher(attribute.value);
            final Matcher guarded = ATTRIBUTE.matcher(attribute.value);
            final Matcher model = MODEL.matcher(attribute.value);
            if (identity.matches())
            {
                guardedBindings.add(new GuardedBinding(attribute.name, identity.group(1), null));
            }
            else if (guarded.matches())
            {
                guardedBindings.add(new GuardedBinding(attribute.name, guarded.group(1), guarded.group(2)));
            }
            else if (model.matches())
            {
                final List<String> names = "request".equals(model.group(1)) ? requestModels : responseModels;
                names.add(attribute.name);
            }
        }
    }

    void request(
        long exchangeId,
        long authorization,
        BeginFW begin)
    {
        if (!guardedBindings.isEmpty() || !requestModels.isEmpty())
        {
            final Map<String, String> values = new Object2ObjectHashMap<>();
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

            final String model = requestModels.isEmpty() ? null : model(begin);
            if (model != null)
            {
                for (String name : requestModels)
                {
                    values.put(name, model);
                }
            }

            requests.put(exchangeId, values);
        }
    }

    void response(
        long exchangeId,
        BeginFW begin)
    {
        if (!responseModels.isEmpty() && requests.containsKey(exchangeId))
        {
            final String model = model(begin);
            if (model != null)
            {
                final Map<String, String> values = new Object2ObjectHashMap<>();
                for (String name : responseModels)
                {
                    values.put(name, model);
                }
                responses.put(exchangeId, values);
            }
        }
    }

    int attributesId(
        long exchangeId)
    {
        return labelId(requests.get(exchangeId), responses.get(exchangeId));
    }

    int requestAttributesId(
        long exchangeId)
    {
        return labelId(requests.get(exchangeId), null);
    }

    void release(
        long exchangeId)
    {
        if (!requests.isEmpty())
        {
            requests.remove(exchangeId);
            responses.remove(exchangeId);
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

    private static String asString(
        JsonValue value)
    {
        return value != null ? JsonStrings.asString(value) : null;
    }

    private String model(
        BeginFW begin)
    {
        final LlmBeginExFW llmBeginEx = begin.extension().get(llmBeginExRO::tryWrap);
        return llmBeginEx != null && llmBeginEx.typeId() == llmTypeId ? llmBeginEx.model().asString() : null;
    }

    private int labelId(
        Map<String, String> request,
        Map<String, String> response)
    {
        int labelId = 0;

        combined.clear();
        if (request != null)
        {
            combined.putAll(request);
        }
        if (response != null)
        {
            combined.putAll(response);
        }

        if (!combined.isEmpty())
        {
            final String[] names = combined.keySet().toArray(String[]::new);
            Arrays.sort(names);

            final StringBuilder label = new StringBuilder();
            for (String name : names)
            {
                if (label.length() > 0)
                {
                    label.append(',');
                }
                label.append(name).append('=').append(combined.get(name));
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
