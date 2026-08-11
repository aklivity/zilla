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
package io.aklivity.zilla.config.binding.tls.internal;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.binding.tls.TlsWithCertificateConfig;
import io.aklivity.zilla.config.binding.tls.TlsWithCertificateConfigBuilder;
import io.aklivity.zilla.config.binding.tls.TlsWithConfig;
import io.aklivity.zilla.config.binding.tls.TlsWithConfigBuilder;
import io.aklivity.zilla.config.engine.WithConfig;

public final class TlsWithConfigAdapter implements JsonbAdapter<WithConfig, JsonObject>
{
    private static final String CERTIFICATE_NAME = "certificate";

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        TlsWithConfig tlsWith = (TlsWithConfig) with;

        JsonObjectBuilder object = Json.createObjectBuilder();

        TlsWithCertificateConfig certificate = tlsWith.certificate;
        if (certificate != null)
        {
            JsonObjectBuilder newCertificate = Json.createObjectBuilder();
            certificate.fields.forEach(newCertificate::add);
            object.add(CERTIFICATE_NAME, newCertificate);
        }

        return object.build();
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        TlsWithConfigBuilder<TlsWithConfig> with = TlsWithConfig.builder();

        if (object.containsKey(CERTIFICATE_NAME))
        {
            JsonObject certificate = object.getJsonObject(CERTIFICATE_NAME);

            TlsWithCertificateConfigBuilder<TlsWithConfigBuilder<TlsWithConfig>> newCertificate = with.certificate();
            certificate.keySet().forEach(name -> newCertificate.field(name, certificate.getString(name)));
            newCertificate.build();
        }

        return with.build();
    }
}
