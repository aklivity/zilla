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
package io.aklivity.zilla.runtime.binding.a2a.internal.card;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class A2aAgentCardParserTest
{
    private final A2aAgentCardParser parser = new A2aAgentCardParser();
    private final UnsafeBufferEx input = new UnsafeBufferEx(new byte[8192]);

    @Test
    public void shouldParseCurrentCard()
    {
        final A2aAgentCard card = parse("{\"name\":\"Weather Agent\",\"supportedInterfaces\":[" +
            "{\"url\":\"http://localhost:8081\",\"protocolBinding\":\"GRPC\",\"protocolVersion\":\"1.0\"}," +
            "{\"url\":\"http://localhost:8080/a2a\",\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"," +
            "\"tenant\":\"acme\"},{\"url\":\"http://localhost:8082/a2a\",\"protocolBinding\":\"jsonrpc\"}]," +
            "\"skills\":[{\"id\":\"weather\",\"tags\":[\"a\"]},{\"id\":\"forecast\"}]}");

        assertThat(card.legacy, is(false));
        assertThat(card.skills, contains("weather", "forecast"));
        assertThat(card.interfaces, hasSize(3));
        assertThat(card.offersJsonRpc(), is(true));

        final A2aAgentInterface selected = card.select(List.of("1.0", "0.3"));

        assertThat(selected.url, equalTo("http://localhost:8080/a2a"));
        assertThat(selected.tenant, equalTo("acme"));
        assertThat(selected.scheme(), equalTo("http"));
        assertThat(selected.authority(), equalTo("localhost:8080"));
        assertThat(selected.path(), equalTo("/a2a"));
        assertThat(card.interfaces.get(2).version, equalTo("1.0"));
    }

    @Test
    public void shouldParseLegacyCard()
    {
        final A2aAgentCard card = parse("{\"name\":\"Weather Agent\",\"url\":\"https://weather.example.com\"," +
            "\"preferredTransport\":\"JSONRPC\",\"protocolVersion\":\"0.3.0\",\"additionalInterfaces\":" +
            "[{\"url\":\"https://weather.example.com:8443/grpc\",\"transport\":\"GRPC\"}]," +
            "\"skills\":[{\"id\":\"weather\"}]}");

        assertThat(card.legacy, is(true));
        assertThat(card.interfaces, hasSize(2));

        final A2aAgentInterface selected = card.select(List.of("1.0", "0.3"));

        assertThat(selected.version, equalTo("0.3.0"));
        assertThat(selected.authority(), equalTo("weather.example.com:443"));
        assertThat(selected.path(), equalTo("/"));
        assertThat(card.select(List.of("1.0")), nullValue());
    }

    @Test
    public void shouldDefaultLegacyTransportAndVersion()
    {
        final A2aAgentCard card = parse("{\"url\":\"http://localhost:8080/a2a\"}");

        assertThat(card.select(List.of("0.3")).binding, equalTo("JSONRPC"));
        assertThat(card.select(List.of("0.3")).version, equalTo("0.3"));
    }

    @Test
    public void shouldPreferConfiguredVersionOrder()
    {
        final A2aAgentCard card = parse("{\"supportedInterfaces\":[" +
            "{\"url\":\"http://localhost:8080/v03\",\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"0.3\"}," +
            "{\"url\":\"http://localhost:8080/v10\",\"protocolBinding\":\"JSONRPC\",\"protocolVersion\":\"1.0\"}]}");

        assertThat(card.select(List.of("1.0", "0.3")).url, equalTo("http://localhost:8080/v10"));
        assertThat(card.select(List.of("0.3", "1.0")).url, equalTo("http://localhost:8080/v03"));
        assertThat(card.select(List.of("2.0")), nullValue());
    }

    @Test
    public void shouldNotOfferJsonRpc()
    {
        final A2aAgentCard card = parse("{\"supportedInterfaces\":[" +
            "{\"url\":\"localhost:8081\",\"protocolBinding\":\"GRPC\",\"protocolVersion\":\"1.0\"}]}");

        assertThat(card.offersJsonRpc(), is(false));
        assertThat(card.select(List.of("1.0", "0.3")), nullValue());
        assertThat(A2aAgentCard.versionOf("1"), equalTo("1.0"));
    }

    private A2aAgentCard parse(
        String card)
    {
        final byte[] bytes = card.getBytes(UTF_8);
        input.putBytes(0, bytes);
        return parser.parse(input, 0, bytes.length);
    }
}
