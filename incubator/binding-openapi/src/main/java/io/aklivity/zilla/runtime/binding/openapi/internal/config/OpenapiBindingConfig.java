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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.PathItem;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public final class OpenapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final OpenapiOptionsConfig options;
    public final List<OpenapiRouteConfig> routes;
    public final HttpHeaderHelper helper;

    private final IntHashSet httpOrigins;
    private final Object2ObjectHashMap<Matcher, PathItem> paths;
    private final Map<CharSequence, Function<PathItem, String>> resolversByMethod;

    public OpenapiBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = OpenapiOptionsConfig.class.cast(binding.options);
        this.paths = new Object2ObjectHashMap<>();
        options.openapis.forEach(c -> c.openapi.paths.forEach((k, v) ->
        {
            String regex = k.replaceAll("\\{[^/]+}", "[^/]+");
            regex = "^" + regex + "$";
            Pattern pattern = Pattern.compile(regex);
            paths.put(pattern.matcher(""), v);
        }));

        this.routes = binding.routes.stream().map(OpenapiRouteConfig::new).collect(toList());

        this.httpOrigins = binding.composites.stream()
            .map(c -> c.bindings)
            .flatMap(List::stream)
            .filter(b -> b.type.equals("http"))
            .map(b -> NamespacedId.namespaceId(b.id))
            .collect(toCollection(IntHashSet::new));
        this.helper = new HttpHeaderHelper();

        this.resolversByMethod = new TreeMap<>(CharSequence::compare);
        resolversByMethod.put("POST", o -> o.post != null ? o.post.operationId : null);
        resolversByMethod.put("PUT", o -> o.put != null ? o.put.operationId : null);
        resolversByMethod.put("GET", o -> o.get != null ? o.get.operationId : null);
        resolversByMethod.put("DELETE", o -> o.delete != null ? o.delete.operationId : null);
        resolversByMethod.put("OPTIONS", o -> o.options != null ? o.options.operationId : null);
        resolversByMethod.put("HEAD", o -> o.head != null ? o.head.operationId : null);
        resolversByMethod.put("PATCH", o -> o.patch != null ? o.patch.operationId : null);
        resolversByMethod.put("TRACE", o -> o.post != null ? o.trace.operationId : null);
    }

    public boolean isCompositeNamespace(
        int namespaceId)
    {
        return httpOrigins.contains(namespaceId);
    }

    public String resolveOperationId(
        HttpBeginExFW httpBeginEx)
    {
        helper.visit(httpBeginEx);

        String operationId = null;

        resolve:
        for (Map.Entry<Matcher, PathItem> item : paths.entrySet())
        {
            Matcher matcher = item.getKey();
            matcher.reset(helper.path);
            if (matcher.find())
            {
                PathItem operations = item.getValue();
                operationId = resolveMethod(operations);
                break resolve;
            }
        }

        return operationId;
    }

    private String resolveMethod(
        PathItem operations)
    {
        Function<PathItem, String> resolver = resolversByMethod.get(helper.method);
        return resolver != null ? resolver.apply(operations) : null;
    }

    public OpenapiRouteConfig resolve(
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
