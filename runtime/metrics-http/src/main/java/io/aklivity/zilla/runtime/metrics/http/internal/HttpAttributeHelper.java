/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.http.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.metrics.http.internal.types.Array32FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.HttpBeginExFW;

final class HttpAttributeHelper
{
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{http\\.(request|response)\\.([^}]+)\\}");
    private static final Map<String, String> FIELD_TO_HEADER = Map.of(
        "method", ":method",
        "path", ":path",
        "status", ":status"
    );

    private final List<HttpAttributeBinding> requestBindings;
    private final List<HttpAttributeBinding> responseBindings;
    private final ToIntFunction<String> supplyLabelId;
    private final HttpBeginExFW httpBeginExRO = new HttpBeginExFW();

    HttpAttributeHelper(
        List<AttributeConfig> attributes,
        ToIntFunction<String> supplyLabelId)
    {
        this.supplyLabelId = supplyLabelId;
        this.requestBindings = new ArrayList<>();
        this.responseBindings = new ArrayList<>();

        for (AttributeConfig attr : attributes)
        {
            Matcher matcher = EXPRESSION.matcher(attr.value);
            if (matcher.matches())
            {
                String category = matcher.group(1);
                String field = matcher.group(2);
                String headerName = FIELD_TO_HEADER.getOrDefault(field, field);
                HttpAttributeBinding binding = new HttpAttributeBinding(attr.name, headerName);
                if ("request".equals(category))
                {
                    requestBindings.add(binding);
                }
                else
                {
                    responseBindings.add(binding);
                }
            }
        }
    }

    boolean hasRequestBindings()
    {
        return !requestBindings.isEmpty();
    }

    boolean hasResponseBindings()
    {
        return !responseBindings.isEmpty();
    }

    Map<String, String> extractRequestAttributes(
        BeginFW begin)
    {
        return extractAttributes(begin, requestBindings);
    }

    Map<String, String> extractResponseAttributes(
        BeginFW begin)
    {
        return extractAttributes(begin, responseBindings);
    }

    int computeAttributesId(
        Map<String, String> attributes)
    {
        if (attributes.isEmpty())
        {
            return 0;
        }
        String[] keys = attributes.keySet().toArray(String[]::new);
        Arrays.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys)
        {
            if (sb.length() > 0)
            {
                sb.append(',');
            }
            sb.append(key).append('=').append(attributes.get(key));
        }
        return supplyLabelId.applyAsInt(sb.toString());
    }

    private Map<String, String> extractAttributes(
        BeginFW begin,
        List<HttpAttributeBinding> bindings)
    {
        Map<String, String> result = new Object2ObjectHashMap<>();
        if (bindings.isEmpty())
        {
            return result;
        }
        OctetsFW extension = begin.extension();
        HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);
        if (httpBeginEx != null)
        {
            Array32FW<HttpHeaderFW> headers = httpBeginEx.headers();
            for (HttpAttributeBinding binding : bindings)
            {
                HttpHeaderFW header = headers.matchFirst(h -> binding.headerName.equals(h.name()));
                if (header != null && header.value() != null && header.value().length() != -1)
                {
                    result.put(binding.attributeName, header.value().asString());
                }
            }
        }
        return result;
    }

    private static final class HttpAttributeBinding
    {
        final String attributeName;
        final String8FW headerName;

        HttpAttributeBinding(
            String attributeName,
            String headerName)
        {
            this.attributeName = attributeName;
            this.headerName = new String8FW(headerName);
        }
    }
}
