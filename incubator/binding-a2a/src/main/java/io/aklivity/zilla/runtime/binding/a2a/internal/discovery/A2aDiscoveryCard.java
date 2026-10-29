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

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.json.Json;

public final class A2aDiscoveryCard
{
    public static final String SKILL_ID = "find-agent";

    private static final String DESCRIPTION = "Finds agents by name, skill or description";

    public static final byte[] BYTES = Json.createObjectBuilder()
        .add("name", "Agent Discovery")
        .add("description", DESCRIPTION)
        .add("version", "1.0.0")
        .add("capabilities", Json.createObjectBuilder()
            .add("streaming", false)
            .add("pushNotifications", false))
        .add("defaultInputModes", Json.createArrayBuilder().add("text/plain").add("application/json"))
        .add("defaultOutputModes", Json.createArrayBuilder().add("text/plain").add("application/json"))
        .add("skills", Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("id", SKILL_ID)
                .add("name", "Find agent")
                .add("description", DESCRIPTION)
                .add("tags", Json.createArrayBuilder().add("discovery").add("search"))
                .add("examples", Json.createArrayBuilder().add("Which agent can forecast the weather?"))))
        .build()
        .toString()
        .getBytes(UTF_8);

    private A2aDiscoveryCard()
    {
    }
}
