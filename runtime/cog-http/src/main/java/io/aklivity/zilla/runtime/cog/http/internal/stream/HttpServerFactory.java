/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.http.internal.stream;

import static io.aklivity.zilla.runtime.cog.http.internal.config.HttpVersion.HTTP_1_1;
import static io.aklivity.zilla.runtime.cog.http.internal.config.HttpVersion.HTTP_2;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.SortedSet;
import java.util.TreeSet;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.cog.http.internal.HttpConfiguration;
import io.aklivity.zilla.runtime.cog.http.internal.config.HttpBinding;
import io.aklivity.zilla.runtime.cog.http.internal.config.HttpOptions;
import io.aklivity.zilla.runtime.cog.http.internal.config.HttpVersion;
import io.aklivity.zilla.runtime.cog.http.internal.types.ProxyInfoFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.ProxyInfoType;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.cog.http.internal.types.stream.ProxyBeginExFW;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.Binding;

public final class HttpServerFactory implements HttpStreamFactory
{
    private static final SortedSet<HttpVersion> DEFAULT_SUPPORTED_VERSIONS = new TreeSet<>(EnumSet.allOf(HttpVersion.class));

    private final BeginFW beginRO = new BeginFW();
    private final ProxyBeginExFW proxyBeginExRO = new ProxyBeginExFW();

    private final int proxyTypeId;
    private final Long2ObjectHashMap<HttpBinding> bindings;
    private final EnumMap<HttpVersion, HttpStreamFactory> factories;

    public HttpServerFactory(
        HttpConfiguration config,
        AxleContext context)
    {
        this.proxyTypeId = context.supplyTypeId("proxy");
        this.bindings = new Long2ObjectHashMap<>();

        EnumMap<HttpVersion, HttpStreamFactory> factories = new EnumMap<>(HttpVersion.class);
        factories.put(HTTP_1_1, new Http11ServerFactory(config, context));
        factories.put(HTTP_2, new Http2ServerFactory(config, context));
        this.factories = factories;
    }

    @Override
    public void attach(
        Binding binding)
    {
        HttpBinding httpBinding = new HttpBinding(binding);
        bindings.put(binding.id, httpBinding);

        factories.values().forEach(f -> f.attach(binding));
    }

    @Override
    public void detach(
        long bindingId)
    {
        factories.values().forEach(f -> f.detach(bindingId));

        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer network)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();

        HttpBinding binding = bindings.get(routeId);

        MessageConsumer newStream = null;

        if (binding != null)
        {
            HttpOptions options = binding.options;
            SortedSet<HttpVersion> supportedVersions = options != null && options.versions != null
                    ? options.versions
                    : DEFAULT_SUPPORTED_VERSIONS;

            StreamFactory factory = null;

            if (!supportedVersions.isEmpty())
            {
                factory = factories.get(supportedVersions.first());
            }

            ProxyBeginExFW beginEx = begin.extension().get(proxyBeginExRO::tryWrap);
            if (beginEx != null && beginEx.typeId() == proxyTypeId)
            {
                ProxyInfoFW info = beginEx.infos().matchFirst(i -> i.kind() == ProxyInfoType.ALPN);
                HttpVersion version = info != null ? HttpVersion.of(info.alpn().asString()) : null;

                if (version != null && supportedVersions.contains(version))
                {
                    factory = factories.get(version);
                }
            }

            if (factory != null)
            {
                newStream = factory.newStream(msgTypeId, buffer, index, length, network);
            }
        }

        return newStream;
    }
}
