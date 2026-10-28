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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.function.Function;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCard;
import io.aklivity.zilla.runtime.binding.a2a.internal.card.A2aAgentCardParser;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class A2aCardTranslatorTest
{
    private static final String CARD_V10 =
        "{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:9090/a2a\"," +
        "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}],\"version\":\"1.0.0\",\"skills\":" +
        "[{\"id\":\"weather\",\"name\":\"Weather\"},{\"id\":\"forecast\",\"name\":\"Forecast\"}]}";

    private final A2aAgentCardParser parser = new A2aAgentCardParser();
    private final A2aCardTranslator translator = new A2aCardTranslator();
    private final UnsafeBufferEx input = new UnsafeBufferEx(new byte[8192]);
    private final UnsafeBufferEx output = new UnsafeBufferEx(new byte[8192]);

    @Test
    public void shouldTranslateCardVerbatimWithoutRoles()
    {
        assertThat(translate(CARD_V10, skill -> List.of()), equalTo(CARD_V10));
    }

    @Test
    public void shouldInjectRolesIntoGuardedSkills()
    {
        assertThat(translate(CARD_V10, skill -> "forecast".equals(skill) ? List.of("agents:forecast", "agents:all") : List.of()),
            equalTo("{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:9090/a2a\"," +
            "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}],\"version\":\"1.0.0\",\"skills\":" +
            "[{\"id\":\"weather\",\"name\":\"Weather\"},{\"id\":\"forecast\",\"name\":\"Forecast\"," +
            "\"securityRequirements\":[{\"schemes\":{\"oauth2\":{\"list\":[\"agents:forecast\",\"agents:all\"]}}}]}]}"));
    }

    @Test
    public void shouldBlendRolesWithDeclaredSecurityRequirements()
    {
        assertThat(translate("{\"skills\":[{\"id\":\"forecast\",\"securityRequirements\":" +
            "[{\"schemes\":{\"basic\":{\"list\":[]}}}]}]}", skill -> List.of("agents:forecast")),
            equalTo("{\"skills\":[{\"id\":\"forecast\",\"securityRequirements\":[{\"schemes\":{\"basic\":{\"list\":[]}}}," +
            "{\"schemes\":{\"oauth2\":{\"list\":[\"agents:forecast\"]}}}]}]}"));
    }

    @Test
    public void shouldTranslateLegacyCard()
    {
        assertThat(translate("{\"name\":\"Weather Agent\",\"url\":\"http://localhost:8080/a2a\"," +
            "\"preferredTransport\":\"JSONRPC\",\"protocolVersion\":\"0.3.0\",\"additionalInterfaces\":" +
            "[{\"url\":\"http://localhost:8081\",\"transport\":\"GRPC\"}],\"version\":\"1.0.0\",\"skills\":" +
            "[{\"id\":\"weather\",\"security\":[{\"oauth2\":[\"a\",\"b\"]}]}]}", skill -> List.of()),
            equalTo("{\"name\":\"Weather Agent\",\"supportedInterfaces\":[{\"url\":\"http://localhost:8080/a2a\"," +
            "\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"0.3.0\"},{\"url\":\"http://localhost:8081\"," +
            "\"protocolBinding\":\"GRPC\",\"protocolVersion\":\"0.3.0\"}],\"version\":\"1.0.0\",\"skills\":" +
            "[{\"id\":\"weather\",\"securityRequirements\":[{\"schemes\":{\"oauth2\":{\"list\":[\"a\",\"b\"]}}}]}]}"));
    }

    private String translate(
        String card,
        Function<String, List<String>> roles)
    {
        final byte[] bytes = card.getBytes(UTF_8);
        input.putBytes(0, bytes);

        final A2aAgentCard parsed = parser.parse(input, 0, bytes.length);
        final int limit = translator.translate(parsed, roles, input, 0, bytes.length, output, 0);
        return output.getStringWithoutLengthUtf8(0, limit);
    }
}
