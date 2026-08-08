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
package io.aklivity.zilla.config.guard.x509.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.guard.x509.X509MatchConfig;
import io.aklivity.zilla.config.guard.x509.X509OptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class X509OptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new X509OptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml = """
            identity: subject.cn
            attributes:
              organization: subject.o
              tenant: san.uri
            roles:
              partner:
                - issuer.cn: "Partner Issuing CA"
              internal:
                - issuer.cn: "Internal CA"
                  subject.ou: "Platform"
                - issuer.cn: "Internal CA"
                  san.dns: "*.internal.example.com"
            """;

        X509OptionsConfig options = jsonb.fromJson(yaml, X509OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.identity, equalTo("subject.cn"));
        assertThat(options.attributes.get("organization"), equalTo("subject.o"));
        assertThat(options.attributes.get("tenant"), equalTo("san.uri"));

        List<X509MatchConfig> partner = options.roles.get("partner");
        assertThat(partner, hasSize(1));
        assertThat(partner.get(0).fields.get("issuer.cn"), equalTo("Partner Issuing CA"));

        List<X509MatchConfig> internal = options.roles.get("internal");
        assertThat(internal, hasSize(2));
        assertThat(internal.get(0).fields.get("issuer.cn"), equalTo("Internal CA"));
        assertThat(internal.get(0).fields.get("subject.ou"), equalTo("Platform"));
        assertThat(internal.get(1).fields.get("san.dns"), equalTo("*.internal.example.com"));
    }

    @Test
    public void shouldReadOptionsWithDefaults()
    {
        String yaml = """
            {}
            """;

        X509OptionsConfig options = jsonb.fromJson(yaml, X509OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.identity, equalTo("subject.dn"));
        assertThat(options.attributes, is(anEmptyMap()));
        assertThat(options.roles, is(anEmptyMap()));
    }

    @Test
    public void shouldWriteOptions()
    {
        X509OptionsConfig options = X509OptionsConfig.builder()
            .identity("subject.cn")
            .attribute("organization", "subject.o")
            .match("internal")
                .field("issuer.cn", "Internal CA")
                .field("subject.ou", "Platform")
                .build()
            .match("internal")
                .field("issuer.cn", "Internal CA")
                .field("san.dns", "*.internal.example.com")
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("""
            identity: subject.cn
            attributes:
              organization: subject.o
            roles:
              internal:
                - issuer.cn: "Internal CA"
                  subject.ou: Platform
                - issuer.cn: "Internal CA"
                  san.dns: "*.internal.example.com"
            """));
    }

    @Test
    public void shouldWriteOptionsWithDefaults()
    {
        X509OptionsConfig options = X509OptionsConfig.builder()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{}\n"));
    }
}
