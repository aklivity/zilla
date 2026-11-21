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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class HttpRejections
{
    private static final String HTTP_DURATION = "http.duration";
    private static final String HEADER_STATUS = ":status";
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{http\\.(request|response)\\.([^}]+)\\}");
    private static final Map<String, String> FIELD_TO_HEADER = Map.of(
        "method", ":method",
        "path", ":path",
        "status", HEADER_STATUS);

    private final EngineContext context;
    private final long bindingId;
    private final int metricId;
    private final List<Attribute> requestAttributes;
    private final List<Attribute> responseAttributes;
    private final Int2ObjectHashMap<LongConsumer> writers;

    public HttpRejections(
        EngineContext context,
        BindingConfig binding)
    {
        this.context = context;
        this.bindingId = binding.id;
        this.metricId = resolveMetricId(context, binding);
        this.requestAttributes = new ArrayList<>();
        this.responseAttributes = new ArrayList<>();
        this.writers = new Int2ObjectHashMap<>();

        List<AttributeConfig> attributes = binding.telemetryRef != null && binding.telemetryRef.attributes != null
            ? binding.telemetryRef.attributes
            : List.of();
        for (AttributeConfig attribute : attributes)
        {
            Matcher matcher = EXPRESSION.matcher(attribute.value);
            if (matcher.matches())
            {
                String field = matcher.group(2);
                Attribute resolved = new Attribute(attribute.name, FIELD_TO_HEADER.getOrDefault(field, field));
                if ("request".equals(matcher.group(1)))
                {
                    requestAttributes.add(resolved);
                }
                else
                {
                    responseAttributes.add(resolved);
                }
            }
        }
    }

    public void record(
        String status,
        Function<String, String> request,
        long startedAt)
    {
        if (metricId != 0 && startedAt != 0L)
        {
            Map<String, String> attributes = new TreeMap<>();
            for (Attribute attribute : requestAttributes)
            {
                String value = request.apply(attribute.header);
                if (value != null)
                {
                    attributes.put(attribute.name, value);
                }
            }
            for (Attribute attribute : responseAttributes)
            {
                if (HEADER_STATUS.equals(attribute.header))
                {
                    attributes.put(attribute.name, status);
                }
            }
            int attributesId = attributesId(attributes);
            LongConsumer writer = writers.computeIfAbsent(attributesId, id ->
                context.supplyMetricWriter(Metric.Kind.HISTOGRAM, bindingId, metricId, id, KindConfig.SERVER));
            writer.accept(Math.max(0L, System.nanoTime() - startedAt));
        }
    }

    private int attributesId(
        Map<String, String> attributes)
    {
        int id = 0;
        if (!attributes.isEmpty())
        {
            StringBuilder label = new StringBuilder();
            attributes.forEach((name, value) ->
            {
                if (label.length() > 0)
                {
                    label.append(',');
                }
                label.append(name).append('=').append(value);
            });
            id = context.supplyTypeId(label.toString());
        }
        return id;
    }

    private static int resolveMetricId(
        EngineContext context,
        BindingConfig binding)
    {
        int resolved = 0;
        if (binding.kind == KindConfig.SERVER && binding.metricIds != null)
        {
            for (long id : binding.metricIds)
            {
                if (HTTP_DURATION.equals(context.supplyLocalName(id)))
                {
                    resolved = NamespacedId.localId(id);
                }
            }
        }
        return resolved;
    }

    private record Attribute(
        String name,
        String header)
    {
    }
}
