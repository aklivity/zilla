/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class GrpcBindingConfig
{
    private static final Pattern METHOD_PATTERN = Pattern.compile("/(?<ServiceName>.*?)\\/(?<Method>.*)");
    private static final String SERVICE_NAME = "ServiceName";
    private static final String METHOD = "method";
    private static final String PATH = "path";
    public final long id;
    public final String entry;
    public final KindConfig kind;
    public final GrpcOptionsConfig options;
    public final List<GrpcRouteConfig> routes;

    public GrpcBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = GrpcOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(GrpcRouteConfig::new).collect(toList());
    }

    public GrpcRouteConfig resolve(
        long authorization,
        Function<String, String> metadataByName,
        GrpcMethodConfig methodConfig)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(methodConfig.method, metadataByName))
            .findFirst()
            .orElse(null);
    }

    public GrpcMethodConfig resolveGrpcMethodConfig(
        Map<String, String> headers)
    {

        final String path = headers.get(PATH);
        final String serviceNameHeader = headers.get(SERVICE_NAME);
        GrpcMethodConfig grpcMethodConfig = null;

        final Matcher matcher = METHOD_PATTERN.matcher(path);

        if (matcher.find())
        {
            final CharSequence serviceName = serviceNameHeader != null ? serviceNameHeader : matcher.group(SERVICE_NAME);
            final String fullMethodName = String.format("%s/%s", serviceName, matcher.group(METHOD));

            grpcMethodConfig = options.protobufConfigs.stream()
                .map(p -> p.serviceConfigs.stream().filter(s -> s.serviceName.equals(serviceName)).findFirst().get())
                .map(s -> s.methodConfigs.stream().filter(m -> m.method.equals(fullMethodName)).findFirst().get())
                .findFirst()
                .orElse(null);
        }

        return grpcMethodConfig;
    }
}
