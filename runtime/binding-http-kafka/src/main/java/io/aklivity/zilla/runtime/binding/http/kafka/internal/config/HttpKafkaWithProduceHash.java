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
import java.util.TreeMap;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public class HttpKafkaWithProduceHash
{
    private final String16FW correlationId;
    private final MessageDigest md5;
    private final Map<byte[], Integer> inputs;

    private byte[] digest;

    HttpKafkaWithProduceHash(
        String16FW correlationId)
    {
        this.correlationId = correlationId;
        this.md5 = initMD5();
        this.inputs = new TreeMap<>(this::compareByteArrays);
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

    private int compareByteArrays(
        byte[] array1,
        byte[] array2)
    {
        int minLength = Math.min(array1.length, array2.length);
        int result = 0;
        for (int i = 0; i < minLength && result == 0; i++)
        {
            result = Byte.compare(array1[i], array2[i]);
        }
        return result != 0 ? result : Integer.compare(array1.length, array2.length);
    }
}
