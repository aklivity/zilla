/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collector.of;
import static java.util.stream.Collector.Characteristics.IDENTITY_FINISH;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class AsyncapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final AsyncapiOptionsConfig options;
    public final List<AsyncapiRouteConfig> routes;
    private final Int2ObjectHashMap<String> composites;
    private final long overrideRouteId;
    private final Long2LongHashMap resolvedIds;
    private final HttpHeaderHelper helper;
    private final Object2ObjectHashMap<Matcher, String> paths;
    private final Map<CharSequence, String> operationIds;

    public AsyncapiBindingConfig(
        BindingConfig binding,
        long overrideRouteId)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.overrideRouteId = overrideRouteId;
        this.options = AsyncapiOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(AsyncapiRouteConfig::new).collect(toList());
        this.resolvedIds = binding.composites.stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("mqtt") || b.type.equals("http") || b.type.equals("kafka") || b.type.equals("mqtt-kafka"))
            .collect(of(
                () -> new Long2LongHashMap(-1),
                (m, r) -> m.put(0L, r.id), //TODO: populate proper apiId
                (m, r) -> m,
                IDENTITY_FINISH
            ));
        this.composites = new Int2ObjectHashMap<>();
        binding.composites.stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("mqtt") || b.type.equals("http") || b.type.equals("kafka") || b.type.equals("mqtt-kafka"))
            .forEach(b -> this.composites.put(NamespacedId.namespaceId(b.id), b.type));

        this.paths = new Object2ObjectHashMap<>();
        options.specs.forEach(c -> c.asyncApi.channels.forEach((k, v) ->
        {
            String regex = v.address.replaceAll("\\{[^/]+}", "[^/]+");
            regex = "^" + regex + "$";
            Pattern pattern = Pattern.compile(regex);
            paths.put(pattern.matcher(""), k);
        }));

        this.helper = new HttpHeaderHelper();

        Map<CharSequence, String> resolversByMethod = new TreeMap<>(CharSequence::compare);
        options.specs.forEach(c -> c.asyncApi.operations.forEach((k, v) ->
        {
            String[] refParts = v.channel.ref.split("/");
            resolversByMethod.put(refParts[refParts.length - 1], k);
        }));
        this.operationIds = unmodifiableMap(resolversByMethod);
    }

    public boolean isCompositeOriginId(
        long originId)
    {
        return composites.containsKey(NamespacedId.namespaceId(originId));
    }

    public String getCompositeOriginType(
        long originId)
    {
        return composites.get(NamespacedId.namespaceId(originId));
    }

    public long resolveResolvedId(
        long apiId)
    {
        return overrideRouteId != -1 ? overrideRouteId : resolvedIds.get(apiId);
    }

    public String resolveOperationId(
        HttpBeginExFW httpBeginEx)
    {
        helper.visit(httpBeginEx);

        String operationId = null;

        for (Map.Entry<Matcher, String> item : paths.entrySet())
        {
            Matcher matcher = item.getKey();
            matcher.reset(helper.path);
            if (matcher.find())
            {
                String channelName = item.getValue();
                operationId = operationIds.get(channelName);
                break;
            }
        }

        return operationId;
    }

    public AsyncapiRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    private static final class HttpHeaderHelper
    {
        private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
        private static final String8FW HEADER_NAME_PATH = new String8FW(":path");
        private static final String8FW HEADER_NAME_SCHEME = new String8FW(":scheme");
        private static final String8FW HEADER_NAME_AUTHORITY = new String8FW(":authority");

        private final Map<String8FW, Consumer<String16FW>> visitors;
        {
            Map<String8FW, Consumer<String16FW>> visitors = new HashMap<>();
            visitors.put(HEADER_NAME_METHOD, this::visitMethod);
            visitors.put(HEADER_NAME_PATH, this::visitPath);
            visitors.put(HEADER_NAME_SCHEME, this::visitScheme);
            visitors.put(HEADER_NAME_AUTHORITY, this::visitAuthority);
            this.visitors = visitors;
        }
        private final AsciiSequenceView methodRO = new AsciiSequenceView();
        private final AsciiSequenceView pathRO = new AsciiSequenceView();
        private final String16FW schemeRO = new String16FW();
        private final String16FW authorityRO = new String16FW();

        public CharSequence path;
        public CharSequence method;
        public String16FW scheme;
        public String16FW authority;

        private void visit(
            HttpBeginExFW beginEx)
        {
            method = null;
            path = null;
            scheme = null;
            authority = null;

            if (beginEx != null)
            {
                beginEx.headers().forEach(this::dispatch);
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

            return method != null &&
                path != null &&
                scheme != null &&
                authority != null;
        }

        private void visitMethod(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            method = methodRO.wrap(buffer, offset, length);
        }

        private void visitPath(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            path = pathRO.wrap(buffer, offset, length);
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
    }
}
