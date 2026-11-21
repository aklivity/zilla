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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class A2aDiscoveryRequestTest
{
    @Test
    public void shouldParseTextPartAsQuery()
    {
        final A2aDiscoveryRequest request = parse("{\"message\":{\"messageId\":\"m-1\",\"parts\":[{\"text\":\"weather\"}]}}");

        assertThat(request.messageId, equalTo("m-1"));
        assertThat(request.query, equalTo("weather"));
        assertThat(request.limit, equalTo(0));
    }

    @Test
    public void shouldJoinTextParts()
    {
        final A2aDiscoveryRequest request = parse("{\"message\":{\"parts\":[{\"text\":\"weather\"},{\"text\":\"forecast\"}]}}");

        assertThat(request.messageId, nullValue());
        assertThat(request.query, equalTo("weather forecast"));
    }

    @Test
    public void shouldParseQueryAndLimitFromDataPart()
    {
        final A2aDiscoveryRequest request =
            parse("{\"message\":{\"parts\":[{\"data\":{\"query\":\"flights\",\"limit\":3}}]}}");

        assertThat(request.query, equalTo("flights"));
        assertThat(request.limit, equalTo(3));
    }

    @Test
    public void shouldPreferDataQueryOverText()
    {
        final A2aDiscoveryRequest request =
            parse("{\"message\":{\"parts\":[{\"text\":\"weather\"},{\"data\":{\"query\":\"flights\"}}]}}");

        assertThat(request.query, equalTo("flights"));
    }

    @Test
    public void shouldKeepTextWhenDataHasNoQuery()
    {
        final A2aDiscoveryRequest request =
            parse("{\"message\":{\"parts\":[{\"text\":\"weather\"},{\"data\":{\"limit\":1}}]}}");

        assertThat(request.query, equalTo("weather"));
        assertThat(request.limit, equalTo(1));
    }

    @Test
    public void shouldRejectMalformedJson()
    {
        assertThat(parse("{\"message\":"), nullValue());
    }

    @Test
    public void shouldRejectMissingMessage()
    {
        assertThat(parse("{\"other\":{}}"), nullValue());
    }

    @Test
    public void shouldRejectMessageWithoutParts()
    {
        assertThat(parse("{\"message\":{\"messageId\":\"m-1\"}}"), nullValue());
    }

    @Test
    public void shouldRejectNonObjectBody()
    {
        assertThat(parse("[]"), nullValue());
    }

    private static A2aDiscoveryRequest parse(
        String body)
    {
        return A2aDiscoveryRequest.parse(body.getBytes(UTF_8));
    }
}
