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
package io.aklivity.zilla.runtime.metrics.mcp.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.runtime.metrics.mcp.internal.types.stream.McpBeginExFW;

final class McpAttributeHelper
{
    private static final String OUTCOME = "outcome";
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{mcp\\.([a-zA-Z]+)\\}");

    private final ToIntFunction<String> supplyLabelId;
    private final List<McpAttributeBinding> beginBindings;
    private final List<String> outcomeNames;

    McpAttributeHelper(
        List<AttributeConfig> attributes,
        ToIntFunction<String> supplyLabelId)
    {
        this.supplyLabelId = supplyLabelId;
        this.beginBindings = new ArrayList<>();
        this.outcomeNames = new ArrayList<>();

        for (AttributeConfig attr : attributes)
        {
            Matcher matcher = EXPRESSION.matcher(attr.value);
            if (matcher.matches())
            {
                String field = matcher.group(1);
                if (OUTCOME.equals(field))
                {
                    outcomeNames.add(attr.name);
                }
                else
                {
                    beginBindings.add(new McpAttributeBinding(attr.name, field));
                }
            }
        }
    }

    Map<String, String> extractBeginAttributes(
        McpBeginExFW beginEx,
        McpMethod method)
    {
        Map<String, String> result = new Object2ObjectHashMap<>();
        for (McpAttributeBinding binding : beginBindings)
        {
            String value = method.resolve(binding.field, beginEx);
            if (value != null)
            {
                result.put(binding.name, value);
            }
        }
        return result;
    }

    void applyOutcome(
        Map<String, String> attributes,
        String outcome)
    {
        for (String name : outcomeNames)
        {
            attributes.put(name, outcome);
        }
    }

    int computeAttributesId(
        Map<String, String> attributes)
    {
        int attributesId = 0;
        if (!attributes.isEmpty())
        {
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
            attributesId = supplyLabelId.applyAsInt(sb.toString());
        }
        return attributesId;
    }

    private static final class McpAttributeBinding
    {
        final String name;
        final String field;

        McpAttributeBinding(
            String name,
            String field)
        {
            this.name = name;
            this.field = field;
        }
    }
}
