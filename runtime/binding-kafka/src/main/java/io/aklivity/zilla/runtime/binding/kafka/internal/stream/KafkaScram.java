/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaScramMechanism;

final class KafkaScram
{
    private static final String PRINTABLE = "[\\x21-\\x7E&&[^,]]+";
    private static final String BASE64_CHAR = "[a-zA-Z0-9/+]";
    private static final String BASE64 = String.format("(?:%s{4})*(?:%s{3}=|%s{2}==)?", BASE64_CHAR, BASE64_CHAR, BASE64_CHAR);
    private static final Pattern SERVER_FIRST_MESSAGE = Pattern.compile(String.format(
        "r=(?<nonce>%s),s=(?<salt>%s),i=(?<iterations>[0-9]+)", PRINTABLE, BASE64));
    private static final Pattern SERVER_FINAL_MESSAGE = Pattern.compile(String.format(
        "(?:v=(?<verifier>%s))", BASE64));
    private static final String CHANNEL_BINDING = Base64.getEncoder().encodeToString("n,,".getBytes(UTF_8));
    private static final String CLIENT_FIRST_FORMAT = "n,,n=%s,r=%s";
    private static final String CLIENT_FINAL_FORMAT = "c=%s,r=%s";
    private static final String AUTH_MESSAGE_FORMAT = "n=%s,r=%s,%s,%s";
    private static final byte[] CLIENT_KEY = "Client Key".getBytes(UTF_8);
    private static final byte[] SERVER_KEY = "Server Key".getBytes(UTF_8);
    private static final byte[] FIRST_BLOCK = new byte[] {0, 0, 0, 1};

    private final String username;
    private final String clientNonce;
    private final Mac mac;
    private final MessageDigest digest;

    private byte[] serverSignature;

    KafkaScram(
        KafkaScramMechanism mechanism,
        String username,
        String clientNonce)
    {
        this.username = username;
        this.clientNonce = clientNonce;
        Mac mac = null;
        MessageDigest digest = null;
        try
        {
            mac = Mac.getInstance(mechanism.macAlgorithm());
            digest = MessageDigest.getInstance(mechanism.hashAlgorithm());
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        this.mac = mac;
        this.digest = digest;
    }

    String clientFirst()
    {
        return String.format(CLIENT_FIRST_FORMAT, username, clientNonce);
    }

    String clientFinal(
        String serverFirst,
        String password)
    {
        String result = null;
        Matcher matcher = SERVER_FIRST_MESSAGE.matcher(serverFirst);
        if (matcher.matches() && matcher.group("nonce").startsWith(clientNonce))
        {
            String withoutProof = String.format(CLIENT_FINAL_FORMAT, CHANNEL_BINDING, matcher.group("nonce"));
            byte[] authMessage = String.format(AUTH_MESSAGE_FORMAT, username, clientNonce, serverFirst, withoutProof)
                .getBytes(UTF_8);
            byte[] saltedPassword = hi(password.getBytes(UTF_8),
                Base64.getDecoder().decode(matcher.group("salt")),
                Integer.parseInt(matcher.group("iterations")));
            byte[] clientKey = hmac(saltedPassword, CLIENT_KEY);
            byte[] clientSignature = hmac(digest.digest(clientKey), authMessage);
            serverSignature = hmac(hmac(saltedPassword, SERVER_KEY), authMessage);
            result = withoutProof + ",p=" + Base64.getEncoder().encodeToString(xor(clientKey, clientSignature));
        }
        return result;
    }

    boolean verify(
        String serverFinal)
    {
        Matcher matcher = SERVER_FINAL_MESSAGE.matcher(serverFinal);
        return serverSignature != null && matcher.matches() &&
            Arrays.equals(Base64.getDecoder().decode(matcher.group("verifier")), serverSignature);
    }

    private byte[] hi(
        byte[] password,
        byte[] salt,
        int iterations)
    {
        byte[] previous = hmac(password, concat(salt, FIRST_BLOCK));
        byte[] result = previous;
        for (int i = 2; i <= iterations; i++)
        {
            previous = hmac(password, previous);
            result = xor(result, previous);
        }
        return result;
    }

    private byte[] hmac(
        byte[] key,
        byte[] bytes)
    {
        try
        {
            mac.init(new SecretKeySpec(key, mac.getAlgorithm()));
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        return mac.doFinal(bytes);
    }

    private static byte[] xor(
        byte[] first,
        byte[] second)
    {
        byte[] result = new byte[first.length];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = (byte) (first[i] ^ second[i]);
        }
        return result;
    }

    private static byte[] concat(
        byte[] first,
        byte[] second)
    {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
