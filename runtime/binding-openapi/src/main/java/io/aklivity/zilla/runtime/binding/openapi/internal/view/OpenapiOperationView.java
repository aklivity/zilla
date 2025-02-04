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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiServerConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiOperation;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.extensions.http.kafka.OpenapiHttpKafkaOperationExtension;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiOperationView
{
    public static final String DEFAULT = "default";

    public final OpenapiView specification;
    public final long compositeId;
    public final String method;
    public final String path;
    public final String id;

    public final List<OpenapiParameterView> parameters;
    public final OpenapiRequestBodyView requestBody;
    public final Map<String, OpenapiResponseView> responses;
    public final List<List<OpenapiSecurityRequirementView>> security;
    public final List<OpenapiServerView> servers;
    public final OpenapiHttpKafkaOperationExtension httpKafka;

    OpenapiOperationView(
        OpenapiView specification,
        long compositeId,
        List<OpenapiServerConfig> configs,
        OpenapiResolver resolver,
        String method,
        String path,
        OpenapiOperation model)
    {
        this.specification = specification;
        this.compositeId = compositeId;
        this.method = method;
        this.path = path;

        this.id = model.operationId;

        this.parameters = model.parameters != null
                ? model.parameters.stream()
                    .map(p -> new OpenapiParameterView(this, resolver, p))
                    .toList()
                : null;

        this.requestBody = model.requestBody != null
                ? new OpenapiRequestBodyView(this, resolver, model.requestBody)
                : null;

        this.responses = model.responses != null
                ? model.responses.entrySet().stream()
                    .map(e -> new OpenapiResponseView(this, resolver, e.getKey(), e.getValue()))
                    .collect(toMap(c -> c.status, identity()))
                : null;

        this.security = model.security != null
                ? model.security.stream()
                    .map(s -> s.entrySet().stream()
                        .map(e -> new OpenapiSecurityRequirementView(e.getKey(), e.getValue()))
                        .toList())
                    .toList()
                : specification.security;

        this.servers = model.servers != null
                ? model.servers.stream()
                    .flatMap(s -> configs.stream().map(c -> new OpenapiServerView(resolver, s, c)))
                    .toList()
                : specification.servers;

        this.httpKafka = model.httpKafka;
    }

    public boolean hasRequestBodyOrParameters()
    {
        return hasRequestBody() || hasParameters();
    }

    public boolean hasRequestBody()
    {
        return requestBody != null && requestBody.content != null;
    }

    public boolean hasParameters()
    {
        return parameters != null;
    }

    public boolean hasResponses()
    {
        return responses != null;
    }

    public boolean hasExtensionHttpKafka()
    {
        return httpKafka != null;
    }
}
