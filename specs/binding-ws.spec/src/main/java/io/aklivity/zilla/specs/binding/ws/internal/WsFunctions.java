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
package io.aklivity.zilla.specs.binding.ws.internal;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64.Encoder;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.ws.internal.types.stream.WsBeginExFW;

public final class WsFunctions
{
    private static final Encoder BASE64_ENCODER = java.util.Base64.getEncoder();
    private static final int MAX_BUFFER_SIZE = 1024 * 8;

    private static final byte[] WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(US_ASCII);

    @Function
    public static WsBeginExHelper beginEx()
    {
        return new WsBeginExHelper();
    }

    public static final class WsBeginExHelper
    {
        private final WsBeginExFW.Builder wsBeginExRW;

        private WsBeginExHelper()
        {
            MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[MAX_BUFFER_SIZE]);
            this.wsBeginExRW = new WsBeginExFW.Builder()
                                    .wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public WsBeginExHelper typeId(
            int typeId)
        {
            wsBeginExRW.typeId(typeId);
            return this;
        }

        public WsBeginExHelper protocol(
            String protocol)
        {
            wsBeginExRW.protocol(protocol);
            return this;
        }

        public WsBeginExHelper scheme(
            String scheme)
        {
            wsBeginExRW.scheme(scheme);
            return this;
        }

        public WsBeginExHelper authority(
            String authority)
        {
            wsBeginExRW.authority(authority);
            return this;
        }

        public WsBeginExHelper path(
            String path)
        {
            wsBeginExRW.path(path);
            return this;
        }

        public byte[] build()
        {
            final WsBeginExFW wsBeginEx = wsBeginExRW.build();
            final byte[] result = new byte[wsBeginEx.sizeof()];
            wsBeginEx.buffer().getBytes(0, result);
            return result;
        }
    }

    @Function
    public static String handshakeKey()
    {
        Random random = ThreadLocalRandom.current();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }

    @Function
    public static String handshakeHash(
        String wsKey) throws NoSuchAlgorithmException
    {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(wsKey.getBytes(US_ASCII));
        byte[] digest = sha1.digest(WEBSOCKET_GUID);
        return BASE64_ENCODER.encodeToString(digest);
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(WsFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "ws";
        }
    }

    private WsFunctions()
    {
        // utility
    }
}
