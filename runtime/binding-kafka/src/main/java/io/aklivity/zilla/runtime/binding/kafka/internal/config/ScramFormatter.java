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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.agrona.LangUtil;

public class ScramFormatter
{

    private static final String CLIENT_KEY = "Client Key";
    private static final String SERVER_KEY = "Server Key";
    private MessageDigest messageDigest;
    private Mac mac;

    public ScramFormatter(ScramMechanism mechanism)
    {
        try
        {
            this.messageDigest = MessageDigest.getInstance(mechanism.hashAlgorithm());
            this.mac = Mac.getInstance(mechanism.macAlgorithm());
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public byte[] hmac(byte[] key, byte[] bytes)
    {
        try
        {
            mac.init(new SecretKeySpec(key, mac.getAlgorithm()));
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
        return mac.doFinal(bytes);
    }

    public byte[] hash(byte[] str)
    {
        return messageDigest.digest(str);
    }

    public byte[] xor(byte[] first, byte[] second)
    {
        byte[] result = new byte[first.length];
        if (first.length == second.length)
        {
            for (int i = 0; i < result.length; i++)
            {
                result[i] = (byte) (first[i] ^ second[i]);
            }
        }
        return result;
    }

    public byte[] hi(byte[] str, byte[] salt, int iterations)
    {
        try
        {
            mac.init(new SecretKeySpec(str, mac.getAlgorithm()));
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
        mac.update(salt);
        byte[] u1 = mac.doFinal(new byte[]{0, 0, 0, 1});
        byte[] prev = u1;
        byte[] result = u1;
        for (int i = 2; i <= iterations; i++)
        {
            byte[] ui = hmac(str, prev);
            result = xor(result, ui);
            prev = ui;
        }
        return result;
    }

    public byte[] clientKey(byte[] saltedPassword)
    {
        return hmac(saltedPassword, toBytes(CLIENT_KEY));
    }

    public byte[] serverKey(byte[] saltedPassword)
    {
        return hmac(saltedPassword, toBytes(SERVER_KEY));
    }

    public String clientProof(byte[] saltedPassword, byte[] authMessage)
    {
        byte[] clientKey = clientKey(saltedPassword);
        return Base64.getEncoder().encodeToString(xor(clientKey,
                hmac(hash(clientKey), authMessage)));
    }

    public byte[] toBytes(String str)
    {
        return str.getBytes(StandardCharsets.UTF_8);
    }

}
