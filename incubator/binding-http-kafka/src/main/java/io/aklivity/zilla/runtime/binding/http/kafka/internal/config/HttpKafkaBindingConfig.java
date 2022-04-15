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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class HttpKafkaBindingConfig
{
    public final long id;
    public final String entry;
    public final KindConfig kind;
    public final HttpKafkaOptionsConfig options;
    public final List<HttpKafkaRouteConfig> routes;

    private final HttpKafkaHeaderHelper helper;

    public HttpKafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.entry = binding.entry;
        this.kind = binding.kind;
        this.options = Optional.ofNullable(binding.options)
                .map(HttpKafkaOptionsConfig.class::cast)
                .orElse(null);
        this.routes = binding.routes.stream().map(r -> new HttpKafkaRouteConfig(options, r)).collect(toList());
        this.helper = new HttpKafkaHeaderHelper();
    }

    public HttpKafkaRouteConfig resolve(
        long authorization,
        HttpBeginExFW beginEx)
    {
        helper.visit(beginEx);

        CharSequence method = helper.method;
        CharSequence path = helper.path;

        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(method, path))
            .findFirst()
            .orElse(null);
    }

    private static final class HttpKafkaHeaderHelper
    {
        private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
        private static final String8FW HEADER_NAME_PATH = new String8FW(":path");

        private final Map<String8FW, Consumer<String16FW>> visitors;
        {
            Map<String8FW, Consumer<String16FW>> visitors = new HashMap<>();
            visitors.put(HEADER_NAME_METHOD, this::visitMethod);
            visitors.put(HEADER_NAME_PATH, this::visitPath);
            this.visitors = visitors;
        }

        private final AsciiSequenceView methodRO = new AsciiSequenceView();
        private final AsciiSequenceView pathRO = new AsciiSequenceView();

        public CharSequence method;
        public CharSequence path;

        private void visit(
            HttpBeginExFW beginEx)
        {
            method = null;
            path = null;

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
            return method != null && path != null;
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
    }
}
