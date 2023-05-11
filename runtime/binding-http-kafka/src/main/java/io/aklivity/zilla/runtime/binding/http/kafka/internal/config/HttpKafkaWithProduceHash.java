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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import static org.agrona.BitUtil.toHex;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public class HttpKafkaWithProduceHash
{
    private final String16FW correlationId;
    private final byte[] hashBytesRW;
    private final MessageDigest md5;

    private byte[] digest;

    HttpKafkaWithProduceHash(
        String16FW correlationId,
        byte[] hashBytesRW)
    {
        this.correlationId = correlationId;
        this.hashBytesRW = hashBytesRW;
        this.md5 = initMD5();
    }

    public void updateHash(
        DirectBuffer value)
    {
        value.getBytes(0, hashBytesRW, 0, value.capacity());
        md5.update(hashBytesRW, 0, value.capacity());
    }

    public void digestHash()
    {
        digest = md5.digest();
    }

    public String16FW correlationId()
    {
        return digest != null && correlationId != null
            ? new String16FW(String.format("%s-%s", correlationId.asString(), toHex(digest)))
            : correlationId;
    }

    private MessageDigest initMD5()
    {
        MessageDigest md5 = null;

        try
        {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return md5;
    }
}
