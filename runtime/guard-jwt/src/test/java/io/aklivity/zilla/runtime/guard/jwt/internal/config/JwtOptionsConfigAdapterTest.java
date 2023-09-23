/*
 * Copyright 2021-2023 Aklivity Inc
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.guard.jwt.config.JwtOptionsConfig;

public class JwtOptionsConfigAdapterTest
{
    private Jsonb jsonb;
    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new JwtOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"issuer\":\"https://auth.example.com\"," +
                    "\"audience\":\"https://api.example.com\"," +
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
                    "]," +
                    "\"challenge\":30" +
                "}";

        JwtOptionsConfig options = jsonb.fromJson(text, JwtOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.issuer, equalTo("https://auth.example.com"));
        assertThat(options.audience, equalTo("https://api.example.com"));
        assertThat(options.keys, not(empty()));
        assertThat(options.keys.get(0).kty, equalTo("EC"));
        assertThat(options.keys.get(0).crv, equalTo("P-256"));
        assertThat(options.keys.get(0).x, equalTo("MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4"));
        assertThat(options.keys.get(0).y, equalTo("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM"));
        assertThat(options.keys.get(0).use, equalTo("enc"));
        assertThat(options.keys.get(0).kid, equalTo("1"));
        assertThat(options.keys.get(1).kty, equalTo("RSA"));
        assertThat(options.keys.get(1).n, equalTo("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx" +
                                                  "4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs" +
                                                  "tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2" +
                                                  "QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbI" +
                                                  "SD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqb" +
                                                  "w0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"));
        assertThat(options.keys.get(1).e, equalTo("AQAB"));
        assertThat(options.keys.get(1).alg, equalTo("RS256"));
        assertThat(options.keys.get(1).kid, equalTo("2011-04-29"));
        assertThat(options.challenge.get(), equalTo(Duration.ofSeconds(30)));
    }

    @Test
    public void shouldWriteOptions()
    {
        JwtOptionsConfig options = JwtOptionsConfig.builder()
                .issuer("https://auth.example.com")
                .audience("https://api.example.com")
                .key()
                    .kty("EC")
                    .kid("1")
                    .use("enc")
                    .crv("P-256")
                    .x("MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4")
                    .y("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
                    .build()
                .key()
                    .kty("RSA")
                    .kid("2011-04-29")
                    .n("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx" +
                       "4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs" +
                       "tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2" +
                       "QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbI" +
                       "SD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqb" +
                       "w0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw")
                    .e("AQAB")
                    .alg("RS256")
                    .build()
                .challenge(Duration.ofSeconds(30))
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{" +
                        "\"issuer\":\"https://auth.example.com\"," +
                        "\"audience\":\"https://api.example.com\"," +
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
                        "]," +
                        "\"challenge\":30" +
                    "}"));
    }

    @Test
    public void shouldReadOptionsDynamicKeys()
    {
        String text =
                "{" +
                    "\"issuer\":\"https://auth.example.com\"," +
                    "\"audience\":\"https://api.example.com\"," +
                    "\"keys\": \"https://auth.example.com/.well-known/jwks.json\"," +
                    "\"challenge\":30" +
                "}";
        JwtOptionsConfig options = jsonb.fromJson(text, JwtOptionsConfig.class);
        assertThat(options, not(nullValue()));
        assertThat(options.issuer, equalTo("https://auth.example.com"));
        assertThat(options.audience, equalTo("https://api.example.com"));
        assertThat(options.keys, empty());
        assertThat(options.keysURL.isPresent(), is(true));
        assertThat(options.keysURL.get(), equalTo("https://auth.example.com/.well-known/jwks.json"));
    }

    @Test
    public void shouldReadOptionsImplicitKeys()
    {
        String text =
                "{" +
                    "\"issuer\":\"https://auth.example.com/\"," +
                    "\"audience\":\"https://api.example.com\"," +
                    "\"challenge\":3" +
                "}";
        JwtOptionsConfig options = jsonb.fromJson(text, JwtOptionsConfig.class);
        assertThat(options, not(nullValue()));
        assertThat(options.keys, empty());
        assertThat(options.keysURL.isPresent(), is(true));
        assertThat(options.keysURL.get(), equalTo("https://auth.example.com/.well-known/jwks.json"));
    }
}
