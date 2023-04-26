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

import static io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType.BASE64;
import static io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType.TEXT;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;



import io.aklivity.zilla.runtime.binding.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class GrpcBindingConfig
{
    private static final Pattern METHOD_PATTERN = Pattern.compile("/(?<ServiceName>.*?)/(?<Method>.*)");
    private static final String SERVICE_NAME = "ServiceName";
    private static final String METHOD = "Method";
    private static final byte[] HEADER_GRPC_PREFIX = new byte[5];
    private static final byte[] HEADER_BIN_SUFFIX = new byte[4];
    private static final byte[] GRPC_PREFIX = "grpc-".getBytes();
    private static final byte[] BIN_SUFFIX = "-bin".getBytes();
    private final HttpGrpcHeaderHelper helper;

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final GrpcOptionsConfig options;
    public final List<GrpcRouteConfig> routes;


    public GrpcBindingConfig(
        BindingConfig binding,
        MutableDirectBuffer metadataBuffer)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = GrpcOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(GrpcRouteConfig::new).collect(toList());
        this.helper = new HttpGrpcHeaderHelper(metadataBuffer);
    }


    public GrpcRouteConfig resolve(
        long authorization,
        CharSequence service,
        CharSequence method,
        Array32FW<GrpcMetadataFW> metadataHeaders)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(service, method, metadataHeaders))
            .findFirst()
            .orElse(null);
    }

    public GrpcMethodResult resolveMethod(
        HttpBeginExFW beginEx)
    {
        helper.visit(beginEx);

        final CharSequence path = helper.path;
        final CharSequence serviceNameHeader = helper.serviceName;

        GrpcMethodResult methodResolver = null;

        final Matcher matcher = METHOD_PATTERN.matcher(path);

        if (matcher.matches())
        {
            final CharSequence serviceName = serviceNameHeader != null ? serviceNameHeader : matcher.group(SERVICE_NAME);
            final String methodName = matcher.group(METHOD);

            final GrpcMethodConfig method = options.protobufs.stream()
                .map(p -> p.services.stream().filter(s -> s.service.equals(serviceName)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .map(s -> s.methods.stream().filter(m -> m.method.equals(methodName)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

            if (method != null)
            {
                methodResolver = new GrpcMethodResult(
                    serviceName,
                    methodName,
                    helper.grpcTimeout,
                    helper.contentType,
                    helper.scheme,
                    helper.authority,
                    helper.te,
                    helper.metadata
                );
            }
        }

        return methodResolver;
    }

    private static final class HttpGrpcHeaderHelper
    {
        private static final Pattern PERIOD_PATTERN = Pattern.compile("([0-9]+)([HMSmun])");
        private static final String8FW HEADER_NAME_SERVICE_NAME = new String8FW("service-name");
        private static final String8FW HEADER_NAME_PATH = new String8FW(":path");
        private static final String8FW HEADER_NAME_SCHEME = new String8FW(":scheme");
        private static final String8FW HEADER_NAME_AUTHORITY = new String8FW(":authority");
        private static final String8FW HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");
        private static final String8FW HEADER_NAME_TE = new String8FW("te");
        private static final String8FW HEADER_NAME_GRPC_TIMEOUT = new String8FW("grpc-timeout");


        private final Array32FW.Builder<GrpcMetadataFW.Builder, GrpcMetadataFW> grpcMetadataRW =
            new Array32FW.Builder<>(new GrpcMetadataFW.Builder(), new GrpcMetadataFW());

        private final Set<String8FW> httpHeaders =
            new HashSet<>(asList(new String8FW(":path"),
                new String8FW(":method"),
                new String8FW(":scheme"),
                new String8FW(":authority"),
                new String8FW("service-name"),
                new String8FW("te"),
                new String8FW("content-type"),
                new String8FW("user-agent")));
        private final MutableDirectBuffer metadataBuffer;
        private final Map<String8FW, Consumer<String16FW>> visitors;
        {
            Map<String8FW, Consumer<String16FW>> visitors = new HashMap<>();
            visitors.put(HEADER_NAME_SERVICE_NAME, this::visitServiceName);
            visitors.put(HEADER_NAME_PATH, this::visitPath);
            visitors.put(HEADER_NAME_GRPC_TIMEOUT, this::visitGrpcTimeout);
            visitors.put(HEADER_NAME_SCHEME, this::visitScheme);
            visitors.put(HEADER_NAME_AUTHORITY, this::visitAuthority);
            visitors.put(HEADER_NAME_TE, this::visitTe);
            visitors.put(HEADER_NAME_CONTENT_TYPE, this::visitContentType);
            this.visitors = visitors;
        }
        private final AsciiSequenceView pathRO = new AsciiSequenceView();
        private final AsciiSequenceView serviceNameRO = new AsciiSequenceView();
        private final AsciiSequenceView grpcTimeoutRO = new AsciiSequenceView();
        private final String16FW contentTypeRO = new String16FW();
        private final String16FW schemeRO = new String16FW();
        private final String16FW authorityRO = new String16FW();
        private final String16FW teRO = new String16FW();

        public Array32FW<GrpcMetadataFW> metadata;
        public CharSequence path;
        public CharSequence serviceName;
        private CharSequence grpcTimeoutText;
        public long grpcTimeout;
        public String16FW contentType;
        public String16FW scheme;
        public String16FW authority;
        public String16FW te;


        HttpGrpcHeaderHelper(
            MutableDirectBuffer metadataBuffer)
        {
            this.metadataBuffer = metadataBuffer;
        }

        private void visit(
            HttpBeginExFW beginEx)
        {
            serviceName = null;
            path = null;
            grpcTimeoutText = null;
            scheme = null;
            authority = null;
            te = null;
            contentType = null;
            metadata = null;
            grpcMetadataRW.wrap(metadataBuffer, 0, metadataBuffer.capacity());

            if (beginEx != null)
            {
                beginEx.headers().forEach(this::dispatch);
                metadata = grpcMetadataRW.build();
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

            return serviceName != null &&
                path != null &&
                scheme != null &&
                authority != null &&
                te != null &&
                contentType != null &&
                grpcTimeoutText != null;
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

        private void visitGrpcTimeout(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            grpcTimeoutText = grpcTimeoutRO.wrap(buffer, offset, length);
            grpcTimeout = parsePeriod(grpcTimeoutText);
        }

        private void visitScheme(
            String16FW value)
        {
            scheme = schemeRO.wrap(value.buffer(), value.offset(), value.limit());
        }

        private void visitAuthority(
            String16FW value)
        {
            authority = authorityRO.wrap(value.buffer(), value.offset(), value.limit());
        }

        private void visitTe(
            String16FW value)
        {
            te = teRO.wrap(value.buffer(), value.offset(), value.limit());
        }

        private void visitContentType(
            String16FW value)
        {
            contentType = contentTypeRO.wrap(value.buffer(), value.offset(), value.limit());
        }

        private void visitHeader(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();
            final String16FW value = header.value();
            final boolean notHttpHeader = !httpHeaders.contains(name);

            final int offset = name.offset();
            final int limit = name.limit();
            name.buffer().getBytes(offset, HEADER_GRPC_PREFIX);
            name.buffer().getBytes(limit - BIN_SUFFIX.length, HEADER_BIN_SUFFIX);

            if (notHttpHeader && !GRPC_PREFIX.equals(HEADER_GRPC_PREFIX))
            {
                final GrpcType type = Arrays.equals(BIN_SUFFIX, HEADER_BIN_SUFFIX) ? BASE64 : TEXT;
                final int metadataNameLength = type == BASE64 ? name.length() - BIN_SUFFIX.length : name.length();

                grpcMetadataRW.item(m -> m.type(t -> t.set(type))
                    .nameLen(metadataNameLength)
                    .name(name.value(), 0, metadataNameLength)
                    .valueLen(value.length())
                    .value(value.value(), 0, value.length()));
            }
        }

        private long parsePeriod(
            CharSequence period)
        {
            long milliseconds = 0;

            if (period != null)
            {
                Matcher matcher = PERIOD_PATTERN.matcher(period);

                while (matcher.find())
                {
                    int number = Integer.parseInt(matcher.group(1));
                    String type = matcher.group(2);
                    switch (type)
                    {
                    case "H":
                        milliseconds = TimeUnit.HOURS.toMillis(number);
                        break;
                    case "M":
                        milliseconds = TimeUnit.MINUTES.toMillis(number);
                        break;
                    case "S":
                        milliseconds = TimeUnit.SECONDS.toMillis(number);
                        break;
                    case "m":
                        milliseconds = milliseconds;
                        break;
                    case "u":
                        milliseconds = TimeUnit.MICROSECONDS.toMillis(number);
                        break;
                    case "n":
                        milliseconds = TimeUnit.NANOSECONDS.toMillis(number);
                        break;
                    }
                }
            }
            return milliseconds;
        }
    }
}
