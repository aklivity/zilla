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
package io.aklivity.zilla.config.guard.x509;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class X509GuardConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        X509GuardConfig guard = X509GuardConfig.builder()
            .namespace("test")
            .name("x509_0")
            .options()
                .identity("subject.cn")
                .attribute("organization", "subject.o")
                .match("partner")
                    .field("issuer.cn", "Partner Issuing CA")
                    .build()
                .build()
            .build();

        assertThat(guard.namespace, equalTo("test"));
        assertThat(guard.name, equalTo("x509_0"));
        assertThat(guard.type, equalTo("x509"));

        X509OptionsConfig options = (X509OptionsConfig) guard.options;
        assertThat(options.identity, equalTo("subject.cn"));
        assertThat(options.attributes.get("organization"), equalTo("subject.o"));
        assertThat(options.roles.get("partner"), hasSize(1));
        assertThat(options.roles.get("partner").get(0).fields.get("issuer.cn"), equalTo("Partner Issuing CA"));
    }

    @Test
    public void shouldBuildWithSuppliedMaps()
    {
        X509MatchConfig match = X509MatchConfig.builder()
            .fields(Map.of("issuer.cn", "Internal CA"))
            .build();

        X509GuardConfig guard = X509GuardConfig.builder()
            .namespace("test")
            .name("x509_1")
            .options()
                .attributes(Map.of("organization", "subject.o"))
                .roles(Map.of("internal", List.of(match)))
                .build()
            .build();

        X509OptionsConfig options = (X509OptionsConfig) guard.options;
        assertThat(options.identity, equalTo("subject.dn"));
        assertThat(options.attributes.get("organization"), equalTo("subject.o"));
        assertThat(options.roles.get("internal").get(0).fields.get("issuer.cn"), equalTo("Internal CA"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        X509GuardConfig guard = X509GuardConfig.builder(X509GuardConfig.class::cast)
            .namespace("test")
            .name("x509_2")
            .build();

        assertThat(guard.name, equalTo("x509_2"));
    }

    @Test
    public void shouldBuildMatchViaIdentityMapper()
    {
        X509MatchConfig match = X509MatchConfig.builder()
            .field("san.dns", "*.internal.example.com")
            .build();

        assertThat(match.fields.get("san.dns"), equalTo("*.internal.example.com"));
    }
}
