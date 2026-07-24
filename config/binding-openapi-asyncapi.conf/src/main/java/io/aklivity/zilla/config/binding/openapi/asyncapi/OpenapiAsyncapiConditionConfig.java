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
package io.aklivity.zilla.config.binding.openapi.asyncapi;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;

public class OpenapiAsyncapiConditionConfig extends ConditionConfig
{
    public final String spec;
    public final String operation;
    public final String tag;
    public final List<OpenapiAsyncapiConditionServerConfig> servers;

    public static OpenapiAsyncapiConditionConfigBuilder<OpenapiAsyncapiConditionConfig> builder()
    {
        return new OpenapiAsyncapiConditionConfigBuilder<>(OpenapiAsyncapiConditionConfig.class::cast);
    }

    public static <T> OpenapiAsyncapiConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new OpenapiAsyncapiConditionConfigBuilder<>(mapper);
    }

    OpenapiAsyncapiConditionConfig(
        String spec,
        String operation,
        String tag,
        List<OpenapiAsyncapiConditionServerConfig> servers)
    {
        this.spec = spec;
        this.operation = operation;
        this.tag = tag;
        this.servers = servers;
    }

    public boolean matches(
        String spec,
        String operationId)
    {
        return matches(spec, operationId, null, null);
    }

    public boolean matches(
        String spec,
        String operationId,
        List<String> tags,
        List<OpenapiServerView> operationServers)
    {
        return matchesSpec(spec) &&
            matchesOperation(operationId) &&
            matchesTag(tags) &&
            matchesServers(operationServers);
    }

    private boolean matchesSpec(
        String spec)
    {
        return this.spec == null || this.spec.equals(spec);
    }

    private boolean matchesOperation(
        String operationId)
    {
        return this.operation == null || this.operation.equals(operationId);
    }

    private boolean matchesTag(
        List<String> tags)
    {
        return this.tag == null || tags != null && tags.contains(this.tag);
    }

    private boolean matchesServers(
        List<OpenapiServerView> operationServers)
    {
        return servers == null || servers.isEmpty() ||
            operationServers != null && servers.stream().anyMatch(s -> operationServers.stream().anyMatch(s::matches));
    }
}
