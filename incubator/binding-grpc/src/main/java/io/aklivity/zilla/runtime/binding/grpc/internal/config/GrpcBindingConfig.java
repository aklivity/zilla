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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class GrpcBindingConfig
{
    private static final Pattern METHOD_PATTERN = Pattern.compile("/(?<ServiceName>.*?)/(?<Method>.*)");
    private static final String SERVICE_NAME = "ServiceName";
    private static final String METHOD = "Method";
    public final long id;
    public final String entry;
    public final KindConfig kind;
    public final GrpcOptionsConfig options;
    public final List<GrpcRouteConfig> routes;
    private final HttpGrpcHeaderHelper helper;

    public GrpcBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = GrpcOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(GrpcRouteConfig::new).collect(toList());
        this.helper = new HttpGrpcHeaderHelper();
    }

    public GrpcRouteConfig resolve(
        long authorization,
        GrpcMethodConfig methodConfig)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(methodConfig.method))
            .findFirst()
            .orElse(null);
    }

    public GrpcMethodConfig resolveGrpcMethodConfig(
        HttpBeginExFW beginExFW)
    {
        helper.visit(beginExFW);

        CharSequence path = helper.path;
        final Matcher matcher = METHOD_PATTERN.matcher(path);
        final CharSequence serviceName = helper.serviceName != null ? helper.serviceName : matcher.group(SERVICE_NAME);
        final String fullMethodName = String.format("%s/%s", serviceName, matcher.group(METHOD));

        return options.protobufConfigs.stream()
            .map(p -> p.serviceConfigs.stream().filter(s -> s.serviceName.equals(serviceName)).findFirst().get())
            .map(s -> s.methodConfigs.stream().filter(m -> m.method.equals(fullMethodName)).findFirst().get())
            .findFirst()
            .orElse(null);
    }

    private static final class HttpGrpcHeaderHelper
    {
        private static final String8FW HEADER_NAME_PATH = new String8FW(":path");
        private static final String8FW HEADER_NAME_SERVICE_NAME = new String8FW("service-name");

        private final Map<String8FW, Consumer<String16FW>> visitors;
        {
            Map<String8FW, Consumer<String16FW>> visitors = new HashMap<>();
            visitors.put(HEADER_NAME_PATH, this::visitPath);
            visitors.put(HEADER_NAME_SERVICE_NAME, this::visitServiceName);
            this.visitors = visitors;
        }

        private final AsciiSequenceView pathRO = new AsciiSequenceView();
        private final AsciiSequenceView serviceNameRO = new AsciiSequenceView();

        public CharSequence path;
        public CharSequence serviceName;

        private void visit(
            HttpBeginExFW beginEx)
        {
            path = null;
            serviceName = null;

            if (beginEx != null)
            {
                beginEx.headers().matchFirst(this::dispatch);
            }
        }

        private boolean dispatch(
            HttpHeaderFW header)
        {
            final Consumer<String16FW> visitor = visitors.get(header.name());
            if (visitor != null)
            {
                visitor.accept(header.value());
            }
            return path != null && serviceName != null;
        }

        private void visitPath(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            path = pathRO.wrap(buffer, offset, length);
        }

        private void visitServiceName(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            serviceName = serviceNameRO.wrap(buffer, offset, length);
        }
    }
}
