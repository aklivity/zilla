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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

public final class A2aPushCapabilities
{
    private static final String CAPABILITIES_NAME = "capabilities";
    private static final String PUSH_NOTIFICATIONS_NAME = "pushNotifications";

    public static JsonObject advertise(
        JsonObject card)
    {
        final JsonObject capabilities = card.get(CAPABILITIES_NAME) instanceof JsonObject existing
            ? replace(existing, PUSH_NOTIFICATIONS_NAME)
            : Json.createObjectBuilder().add(PUSH_NOTIFICATIONS_NAME, true).build();

        return replace(card, CAPABILITIES_NAME, capabilities);
    }

    private static JsonObject replace(
        JsonObject object,
        String member)
    {
        return replace(object, member, JsonValue.TRUE);
    }

    private static JsonObject replace(
        JsonObject object,
        String member,
        JsonValue replacement)
    {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        object.forEach((name, value) -> builder.add(name, member.equals(name) ? replacement : value));

        if (!object.containsKey(member))
        {
            builder.add(member, replacement);
        }

        return builder.build();
    }

    private A2aPushCapabilities()
    {
    }
}
