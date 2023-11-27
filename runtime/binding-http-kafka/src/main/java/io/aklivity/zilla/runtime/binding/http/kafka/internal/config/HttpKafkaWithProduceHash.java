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
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public class HttpKafkaWithProduceHash
{
    private final String16FW correlationId;
    private final Map<byte[], Integer> inputs;
    private final MessageDigest md5;

    private byte[] digest;

    HttpKafkaWithProduceHash(
        String16FW correlationId,
        Map<byte[], Integer> inputs)
    {
        this.correlationId = correlationId;
        this.inputs = inputs;
        this.md5 = initMD5();
    }

    public void updateHash(
        DirectBuffer value)
    {
        byte[] hashBytes = new byte[value.capacity()];
        value.getBytes(0, hashBytes, 0, value.capacity());
        inputs.compute(hashBytes, (k, v) -> (v == null) ? 1 : v + 1);
    }

    public void digestHash()
    {
        for (Map.Entry<byte[], Integer> entry : inputs.entrySet())
        {
            for (int i = 0; i < entry.getValue(); i++)
            {
                md5.update(entry.getKey());
            }
        }
        digest = md5.digest();
        inputs.clear();
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
