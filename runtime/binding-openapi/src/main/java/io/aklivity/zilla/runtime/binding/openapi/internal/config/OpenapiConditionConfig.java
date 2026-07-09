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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import java.util.List;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.config.ConditionConfig;

public class OpenapiConditionConfig extends ConditionConfig
{
    public final String spec;
    public final String operation;
    public final String tag;

    private final Pattern operationGlob;

    public OpenapiConditionConfig(
        String spec,
        String operation,
        String tag)
    {
        this.spec = spec;
        this.operation = operation;
        this.tag = tag;
        this.operationGlob = operation != null && operation.indexOf('*') != -1
            ? compileGlob(operation)
            : null;
    }

    public boolean matches(
        String apiId,
        String operationId,
        List<String> tags)
    {
        return matchesSpec(apiId) &&
            matchesOperation(operationId) &&
            matchesTag(tags);
    }

    private boolean matchesSpec(
        String apiId)
    {
        return this.spec == null || this.spec.equals(apiId);
    }

    private boolean matchesOperation(
        String operationId)
    {
        return this.operation == null ||
            (operationGlob != null ? operationGlob.matcher(operationId).matches() : this.operation.equals(operationId));
    }

    private boolean matchesTag(
        List<String> tags)
    {
        return this.tag == null || tags != null && tags.contains(this.tag);
    }

    private static Pattern compileGlob(
        String glob)
    {
        StringBuilder regex = new StringBuilder();
        String[] literals = glob.split("\\*", -1);

        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }

            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }

        return Pattern.compile(regex.toString());
    }
}
