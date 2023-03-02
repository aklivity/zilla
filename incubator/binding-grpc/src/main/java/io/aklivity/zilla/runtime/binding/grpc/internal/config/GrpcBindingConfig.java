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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.collections.Object2ObjectHashMap;

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
    private static final Set<String8FW> GRPC_HEADERS =
        new HashSet<>(asList(new String8FW(":path"),
                             new String8FW(":method"),
                             new String8FW(":scheme"),
                             new String8FW(":authority"),
                             new String8FW("service-name"),
                             new String8FW("te"),
                             new String8FW("content-type"),
                             new String8FW("user-agent")));
    private static final byte[] HEADER_PREFIX = new byte[5];
    private static final byte[] GRPC_PREFIX = "grpc-".getBytes();
    private final HttpGrpcHeaderHelper helper;

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
        this.helper = new HttpGrpcHeaderHelper();
    }


    public GrpcRouteResolver resolve(
        long authorization,
        HttpBeginExFW beginEx,
        GrpcMethodConfig methodConfig)
    {
        if (beginEx != helper.beginEx)
        {
            helper.visit(beginEx);
        }

        final GrpcRouteConfig grpcRouteConfig = routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(methodConfig.method, helper.metadataHeaders::get))
            .findFirst()
            .orElse(null);

        GrpcRouteResolver resolver = null;

        if (grpcRouteConfig != null)
        {
            resolver = new GrpcRouteResolver(grpcRouteConfig.id, helper.contentType, helper.metadataHeaders);
        }

        return resolver;
    }

    public GrpcMethodConfig resolveGrpcMethodConfig(
        HttpBeginExFW beginEx)
    {
        helper.visit(beginEx);

        final CharSequence path = helper.path;
        final CharSequence serviceNameHeader = helper.serviceName;
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

    private static final class HttpGrpcHeaderHelper
    {
        private static final String8FW HEADER_NAME_SERVICE_NAME = new String8FW("service-name");
        private static final String8FW HEADER_NAME_PATH = new String8FW(":path");
        private static final String8FW HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");

        private final Map<String8FW, Consumer<String16FW>> visitors;
        {
            Map<String8FW, Consumer<String16FW>> visitors = new HashMap<>();
            visitors.put(HEADER_NAME_SERVICE_NAME, this::visitServiceName);
            visitors.put(HEADER_NAME_PATH, this::visitPath);
            visitors.put(HEADER_NAME_CONTENT_TYPE, this::visitContentType);
            this.visitors = visitors;
        }

        public HttpBeginExFW beginEx;
        private final AsciiSequenceView serviceNameRO = new AsciiSequenceView();
        private final AsciiSequenceView pathRO = new AsciiSequenceView();
        private final AsciiSequenceView contentTypeRO = new AsciiSequenceView();
        private final Map<String8FW, String16FW> metadataHeaders = new Object2ObjectHashMap<>();

        public CharSequence serviceName;
        public CharSequence path;
        public CharSequence contentType;

        private void visit(
            HttpBeginExFW beginEx)
        {
            this.beginEx = beginEx;
            serviceName = null;
            path = null;

            if (beginEx != null)
            {
                beginEx.headers().matchFirst(this::dispatch);
            }
        }

        private boolean dispatch(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();
            final Consumer<String16FW> visitor = visitors.get(name);
            if (visitor != null)
            {
                visitor.accept(header.value());
            }
            visitHeader(header);


            return serviceName != null && path != null;
        }

        private void visitServiceName(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            serviceName = serviceNameRO.wrap(buffer, offset, length);
        }

        private void visitPath(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            path = pathRO.wrap(buffer, offset, length);
        }

        private void visitContentType(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            contentType = contentTypeRO.wrap(buffer, offset, length);
        }

        private void visitHeader(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();
            final String16FW value = header.value();
            final boolean notGrpcHeader = !GRPC_HEADERS.contains(name);

            final int offset = name.offset();
            name.buffer().getBytes(offset, HEADER_PREFIX);

            if (notGrpcHeader && !GRPC_PREFIX.equals(HEADER_PREFIX))
            {
                metadataHeaders.put(name, value);
            }
        }
    }
}
