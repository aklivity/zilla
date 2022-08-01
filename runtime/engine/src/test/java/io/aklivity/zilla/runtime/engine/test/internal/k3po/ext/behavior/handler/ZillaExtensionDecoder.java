/*
 * Copyright 2021-2022 Aklivity Inc.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.kaazing.k3po.driver.internal.behavior.handler.codec.ChannelDecoder;
import org.kaazing.k3po.driver.internal.behavior.handler.codec.MessageDecoder;
import org.kaazing.k3po.lang.types.StructuredTypeInfo;

import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaChannel;
import io.aklivity.zilla.runtime.engine.test.internal.k3po.ext.behavior.ZillaExtensionKind;

public final class ZillaExtensionDecoder implements ChannelDecoder
{
    private final ZillaExtensionKind readExtKind;
    private final StructuredTypeInfo type;
    private final List<MessageDecoder> decoders;
    private final List<MessageDecoder> remainingDecoders;

    public ZillaExtensionDecoder(
        ZillaExtensionKind readExtKind,
        StructuredTypeInfo type,
        List<MessageDecoder> decoders)
    {
        this.readExtKind = readExtKind;
        this.type = type;
        this.decoders = requireNonNull(decoders);
        this.remainingDecoders = new ArrayList<>(decoders);
    }

    @Override
    public boolean decode(
        Channel channel) throws Exception
    {
        return decode((ZillaChannel) channel);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getQualifiedName()).append(' ');
        for (MessageDecoder decoder : decoders)
        {
            sb.append(decoder).append(' ');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private boolean decode(
        ZillaChannel channel) throws Exception
    {
        final ChannelBuffer readExtBuffer = readExtKind.decodeBuffer(channel);

        Iterator<MessageDecoder> iterator = remainingDecoders.iterator();

        while (iterator.hasNext())
        {
            MessageDecoder decoder = iterator.next();

            ChannelBuffer remainingExtBuffer;

            if (iterator.hasNext())
            {
                remainingExtBuffer = decoder.decode(readExtBuffer);
            }
            else
            {
                remainingExtBuffer = decoder.decodeLast(readExtBuffer);
            }

            if (remainingExtBuffer == null)
            {
                return false;
            }

            // rewind as needed
            final int remainingExtBytes = remainingExtBuffer.readableBytes();
            if (remainingExtBytes > 0)
            {
                readExtBuffer.skipBytes(-remainingExtBytes);
            }
            // Remove the decoder because it is done
            iterator.remove();
        }

        return true;
    }
}
