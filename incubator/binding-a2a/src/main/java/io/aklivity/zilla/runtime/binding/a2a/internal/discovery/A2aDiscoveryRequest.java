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
package io.aklivity.zilla.runtime.binding.a2a.internal.discovery;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

public final class A2aDiscoveryRequest
{
    private static final String MESSAGE = "message";
    private static final String MESSAGE_ID = "messageId";
    private static final String PARTS = "parts";
    private static final String TEXT = "text";
    private static final String DATA = "data";
    private static final String QUERY = "query";
    private static final String LIMIT = "limit";

    public final String messageId;
    public final String query;
    public final int limit;

    public static A2aDiscoveryRequest parse(
        byte[] body)
    {
        A2aDiscoveryRequest request = null;

        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(body)))
        {
            final JsonObject message = reader.readObject().getJsonObject(MESSAGE);
            request = message != null && message.get(PARTS) instanceof JsonArray parts ? of(message, parts) : null;
        }
        catch (JsonException | ClassCastException | IllegalStateException ex)
        {
            request = null;
        }

        return request;
    }

    private static A2aDiscoveryRequest of(
        JsonObject message,
        JsonArray parts)
    {
        final List<String> texts = new ArrayList<>();
        String query = null;
        int limit = 0;

        for (JsonValue part : parts)
        {
            final JsonObject object = part.asJsonObject();

            if (object.get(TEXT) instanceof JsonString text)
            {
                texts.add(text.getString());
            }
            else if (object.get(DATA) instanceof JsonObject data)
            {
                query = data.get(QUERY) instanceof JsonString value ? value.getString() : query;
                limit = data.get(LIMIT) instanceof JsonNumber value ? value.intValue() : limit;
            }
        }

        return new A2aDiscoveryRequest(
            message.getString(MESSAGE_ID, null),
            query != null && !query.isBlank() ? query : String.join(" ", texts),
            limit);
    }

    private A2aDiscoveryRequest(
        String messageId,
        String query,
        int limit)
    {
        this.messageId = messageId;
        this.query = query;
        this.limit = limit;
    }
}
