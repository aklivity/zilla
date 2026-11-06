/*
 * Copyright 2021-2026 Aklivity Inc
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

package io.aklivity.zilla.runtime.binding.a2a.internal.push;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.vault.SecretKeyManager;

public final class A2aPushSealer
{
    private static final int WRAP_OVERHEAD_MAX = 1024;

    private static final String AGENT_NAME = "a";
    private static final String TASK_ID_NAME = "t";
    private static final String URL_NAME = "u";
    private static final String TOKEN_NAME = "k";
    private static final String AUTHENTICATION_NAME = "h";
    private static final String EXPIRY_NAME = "e";
    private static final String VERSION_NAME = "v";
    private static final String NONCE_NAME = "n";

    private final SecretKeyManager manager;
    private final String key;

    public A2aPushSealer(
        SecretKeyManager manager,
        String key)
    {
        this.manager = manager;
        this.key = key;
    }

    public String seal(
        A2aPushSeal seal)
    {
        final byte[] plain = encode(seal).getBytes(UTF_8);
        final UnsafeBufferEx source = new UnsafeBufferEx(plain);
        final MutableDirectBufferEx wrapped = new UnsafeBufferEx(new byte[plain.length + WRAP_OVERHEAD_MAX]);

        final int length = manager.wrap(key, source, 0, plain.length, wrapped, 0);

        String token = null;
        if (length >= 0)
        {
            final byte[] bytes = new byte[length];
            wrapped.getBytes(0, bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        return token;
    }

    public A2aPushSeal unseal(
        String token)
    {
        A2aPushSeal seal = null;

        try
        {
            final byte[] bytes = Base64.getUrlDecoder().decode(token);
            final UnsafeBufferEx wrapped = new UnsafeBufferEx(bytes);
            final MutableDirectBufferEx plain = new UnsafeBufferEx(new byte[bytes.length]);

            final int length = manager.unwrap(wrapped, 0, bytes.length, plain, 0);

            if (length >= 0)
            {
                seal = decode(plain.getStringWithoutLengthUtf8(0, length));
            }
        }
        catch (RuntimeException ex)
        {
            seal = null;
        }

        return seal;
    }

    private static String encode(
        A2aPushSeal seal)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder()
            .add(AGENT_NAME, seal.agent());

        if (seal.taskId() != null)
        {
            builder.add(TASK_ID_NAME, seal.taskId());
        }

        builder.add(URL_NAME, seal.url());

        if (seal.token() != null)
        {
            builder.add(TOKEN_NAME, seal.token());
        }

        if (seal.authentication() != null)
        {
            builder.add(AUTHENTICATION_NAME, seal.authentication());
        }

        builder.add(EXPIRY_NAME, seal.expiry())
            .add(VERSION_NAME, seal.version())
            .add(NONCE_NAME, seal.nonce());

        final StringWriter text = new StringWriter();
        Json.createWriter(text).write(builder.build());
        return text.toString();
    }

    private static A2aPushSeal decode(
        String text)
    {
        try (JsonReader reader = Json.createReader(new StringReader(text)))
        {
            final JsonObject object = reader.readObject();
            final JsonValue authentication = object.get(AUTHENTICATION_NAME);

            return new A2aPushSeal(
                string(object, AGENT_NAME),
                string(object, TASK_ID_NAME),
                string(object, URL_NAME),
                string(object, TOKEN_NAME),
                authentication instanceof JsonObject member ? member : null,
                object.getJsonNumber(EXPIRY_NAME).longValue(),
                string(object, VERSION_NAME),
                string(object, NONCE_NAME));
        }
    }

    private static String string(
        JsonObject object,
        String name)
    {
        return object.get(name) instanceof JsonString value ? value.getString() : null;
    }
}
