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
package io.aklivity.zilla.config.binding.tls;

import static java.util.Collections.emptyMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class TlsWithCertificateConfigBuilder<T> extends ConfigBuilder<T, TlsWithCertificateConfigBuilder<T>>
{
    private final Function<TlsWithCertificateConfig, T> mapper;

    private Map<String, String> fields;

    TlsWithCertificateConfigBuilder(
        Function<TlsWithCertificateConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<TlsWithCertificateConfigBuilder<T>> thisType()
    {
        return (Class<TlsWithCertificateConfigBuilder<T>>) getClass();
    }

    public TlsWithCertificateConfigBuilder<T> field(
        String name,
        String value)
    {
        if (fields == null)
        {
            fields = new LinkedHashMap<>();
        }
        fields.put(name, value);
        return this;
    }

    public TlsWithCertificateConfigBuilder<T> fields(
        Map<String, String> fields)
    {
        this.fields = fields;
        return this;
    }

    public TlsWithCertificateConfigBuilder<T> subjectCommonName(
        String value)
    {
        return field(TlsWithCertificateConfig.SUBJECT_CN, value);
    }

    public TlsWithCertificateConfigBuilder<T> subjectDistinguishedName(
        String value)
    {
        return field(TlsWithCertificateConfig.SUBJECT_DN, value);
    }

    @Override
    public T build()
    {
        return mapper.apply(new TlsWithCertificateConfig(fields != null ? fields : emptyMap()));
    }
}
