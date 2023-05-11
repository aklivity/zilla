/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.manager.internal.settings;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.junit.Test;

public class ZpmSecurityTest
{
    @Test
    public void shouldReadEmptySecurity()
    {
        String text =
                "{" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmSecurity security = builder.fromJson(text, ZpmSecurity.class);

        assertThat(security, not(nullValue()));
        assertThat(security.secret, nullValue());
    }

    @Test
    public void shouldWriteEmptySecurity()
    {
        String expected =
                "{" +
                "}";

        ZpmSecurity security = new ZpmSecurity();

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(security);

        assertEquals(expected, actual);
    }

    @Test
    public void shouldReadSecurity()
    {
        String text =
                "{" +
                    "\"secret\":\"whisper\"" +
                "}";

        Jsonb builder = JsonbBuilder.create();
        ZpmSecurity security = builder.fromJson(text, ZpmSecurity.class);

        assertThat(security, not(nullValue()));
        assertThat(security.secret, equalTo("whisper"));
    }

    @Test
    public void shouldWriteSecurity()
    {
        String expected =
                "{" +
                    "\"secret\":\"whisper\"" +
                "}";

        ZpmSecurity security = new ZpmSecurity();
        security.secret = "whisper";

        Jsonb builder = JsonbBuilder.create();
        String actual = builder.toJson(security);

        assertEquals(expected, actual);
    }
}
