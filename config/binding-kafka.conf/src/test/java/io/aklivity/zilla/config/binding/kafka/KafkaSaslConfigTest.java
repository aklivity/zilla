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
package io.aklivity.zilla.config.binding.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class KafkaSaslConfigTest
{
    @Test
    public void shouldBuildPlain()
    {
        KafkaSaslConfig sasl = KafkaSaslConfig.builder()
            .mechanism("plain")
            .username("alice")
            .password("alice-secret")
            .build();

        assertThat(sasl.mechanism, equalTo("plain"));
        assertThat(sasl.username, equalTo("alice"));
        assertThat(sasl.password, equalTo("alice-secret"));
        assertThat(sasl.toString(), equalTo("plain [username=alice]"));
    }

    @Test
    public void shouldBuildOauthbearer()
    {
        KafkaSaslConfig sasl = KafkaSaslConfig.builder()
            .mechanism("oauthbearer")
            .token("eyJhbGciOiJSUzI1NiJ9...")
            .build();

        assertThat(sasl.mechanism, equalTo("oauthbearer"));
        assertThat(sasl.token, equalTo("eyJhbGciOiJSUzI1NiJ9..."));
    }
}
