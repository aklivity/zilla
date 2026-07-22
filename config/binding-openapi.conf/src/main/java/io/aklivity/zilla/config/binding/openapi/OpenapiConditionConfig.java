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
package io.aklivity.zilla.config.binding.openapi;

import java.util.List;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;

public class OpenapiConditionConfig extends ConditionConfig
{
    public final String spec;
    public final String operation;
    public final String tag;
    public final List<OpenapiConditionServerConfig> servers;

    private final Pattern operationGlob;

    public OpenapiConditionConfig(
        String spec,
        String operation,
        String tag)
    {
        this(spec, operation, tag, null);
    }

    public OpenapiConditionConfig(
        String spec,
        String operation,
        String tag,
        List<OpenapiConditionServerConfig> servers)
    {
        this.spec = spec;
        this.operation = operation;
        this.tag = tag;
        this.servers = servers;
        this.operationGlob = operation != null && operation.indexOf('*') != -1
            ? compileGlob(operation)
            : null;
    }

    public boolean matches(
        String spec,
        String operation,
        List<String> tags)
    {
        return matchesSpec(spec) &&
            matchesOperation(operation) &&
            matchesTag(tags);
    }

    public boolean matchesServers(
        List<OpenapiServerView> operationServers)
    {
        return servers == null ||
            servers.stream().anyMatch(s -> operationServers != null &&
                operationServers.stream().anyMatch(s::matches));
    }

    private boolean matchesSpec(
        String spec)
    {
        return this.spec == null || this.spec.equals(spec);
    }

    private boolean matchesOperation(
        String operation)
    {
        return this.operation == null ||
            (operationGlob != null ? operationGlob.matcher(operation).matches() : this.operation.equals(operation));
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
