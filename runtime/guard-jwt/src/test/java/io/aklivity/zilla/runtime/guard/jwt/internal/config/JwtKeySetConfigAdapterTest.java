/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.jwt.internal.config;

import static io.aklivity.zilla.runtime.guard.jwt.internal.keys.JwtKeyConfigs.RFC7515_ES256_CONFIG;
import static io.aklivity.zilla.runtime.guard.jwt.internal.keys.JwtKeyConfigs.RFC7515_RS256_CONFIG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class JwtKeySetConfigAdapterTest extends AbstractJwtConfigAdapterTest
{
    @Before
    public void initJson()
    {
        initJson(new JwtKeySetConfigAdapter());
    }

    @Test
    public void shouldReadJwtKeySet()
    {
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
                        "}," +
                        "{" +
                        "\"kty\":\"RSA\"," +
                        "\"n\":\"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx" +
                        "4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs" +
                        "tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2" +
                        "QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbI" +
                        "SD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqb" +
                        "w0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\"," +
                        "\"e\":\"AQAB\"," +
                        "\"alg\":\"RS256\"," +
                        "\"kid\":\"2011-04-29\"" +
                        "}" +
                     "]" +
                "}";
        JwtKeySetConfig jwksConfig = jsonb.fromJson(text, JwtKeySetConfig.class);
        assertThat(jwksConfig.keys, not(nullValue()));
        assertThat(jwksConfig.keys.size(), is(2));
    }

    @Test
    public void shouldWriteJwtKeySet()
    {

        JwtKeySetConfig keySetConfig = new JwtKeySetConfig(List.of(RFC7515_RS256_CONFIG, RFC7515_ES256_CONFIG));
        String text = jsonb.toJson(keySetConfig);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{" +
                         "\"keys\":[" +
                             "{" +
                                 "\"kty\":\"RSA\"," +
                                 "\"n\":\"ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26m" +
                                         "PddxHmfHQp-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZ" +
                                         "S-pCgNMsD1W_YpRPEwOWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-b" +
                                         "Sf63kpaSHSXndS5z5rexMdbBYUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg66" +
                                         "5xsmtdVMTBQY4uDZlxvb3qCo5ZwKh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmou" +
                                         "dF8NAco9_h9iaGNj8q2ethFkMLs91kzk2PAcDTW9gb54h4FRWyuXpoQ\"," +
                                 "\"e\":\"AQAB\"," +
                                 "\"alg\":\"RS256\"," +
                                 "\"use\":\"verify\"," +
                                 "\"kid\":\"test\"" +
                             "}," +
                             "{" +
                                 "\"kty\":\"RSA\"," +
                                 "\"crv\":\"P-256\"," +
                                 "\"x\":\"f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU\"," +
                                 "\"y\":\"x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0\"," +
                                 "\"use\":\"verify\"," +
                                 "\"kid\":\"test\"" +
                             "}" +
                         "]" +
                        "}"));
    }
}
