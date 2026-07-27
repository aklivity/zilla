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
package io.aklivity.zilla.config.guard.jwt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class JwtKeySetConfigReaderTest
{
    @Test
    public void shouldReadJwtKeySet()
    {
        JwtKeySetConfigReader reader = new JwtKeySetConfigReader();

        String text =
                "{" +
                    "\"keys\":" +
                    "[" +
                        "{" +
                        "\"kty\":\"EC\"," +
                        "\"crv\":\"P-256\"," +
                        "\"x\":\"MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4\"," +
                        "\"y\":\"4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM\"," +
                        "\"use\":\"enc\"," +
                        "\"kid\":\"1\"" +
                        "}" +
                     "]" +
                "}";

        JwtKeySetConfig jwksConfig = reader.read(text);

        assertThat(jwksConfig.keys, not(nullValue()));
        assertThat(jwksConfig.keys.size(), is(1));
    }

    @Test
    public void shouldReadJwtKeySetWhenKeysMissing()
    {
        JwtKeySetConfigReader reader = new JwtKeySetConfigReader();

        JwtKeySetConfig jwksConfig = reader.read("{}");

        assertThat(jwksConfig.keys, nullValue());
    }
}
