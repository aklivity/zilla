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
package io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.handler;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.Channel;
import org.kaazing.k3po.driver.internal.behavior.handler.codec.ChannelEncoder;
import org.kaazing.k3po.driver.internal.behavior.handler.codec.MessageEncoder;
import org.kaazing.k3po.lang.types.StructuredTypeInfo;

import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaChannel;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaChannelConfig;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind;

public final class ZillaExtensionEncoder implements ChannelEncoder
{
    private final ZillaExtensionKind writeExtKind;
    private final StructuredTypeInfo type;
    private final List<MessageEncoder> encoders;

    public ZillaExtensionEncoder(
        ZillaExtensionKind writeExtKind,
        StructuredTypeInfo type,
        List<MessageEncoder> encoders)
    {
        this.writeExtKind = writeExtKind;
        this.type = type;
        this.encoders = requireNonNull(encoders);
    }

    @Override
    public void encode(
        Channel channel) throws Exception
    {
        encode((ZillaChannel) channel);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getQualifiedName()).append(' ');
        for (MessageEncoder encoder : encoders)
        {
            sb.append(encoder).append(' ');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private void encode(
        ZillaChannel channel)
    {
        ZillaChannelConfig config = channel.getConfig();
        ChannelBufferFactory bufferFactory = config.getBufferFactory();
        for (MessageEncoder encoder : encoders)
        {
            channel.writeExtBuffer(writeExtKind, false).writeBytes(encoder.encode(bufferFactory));
        }
    }
}
