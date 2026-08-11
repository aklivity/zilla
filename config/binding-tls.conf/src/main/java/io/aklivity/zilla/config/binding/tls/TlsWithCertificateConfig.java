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

import java.util.Map;
import java.util.function.Function;

public final class TlsWithCertificateConfig
{
    public static final String SUBJECT_CN = "subject.cn";
    public static final String SUBJECT_DN = "subject.dn";

    public final Map<String, String> fields;

    public static TlsWithCertificateConfigBuilder<TlsWithCertificateConfig> builder()
    {
        return new TlsWithCertificateConfigBuilder<>(Function.identity());
    }

    public static <T> TlsWithCertificateConfigBuilder<T> builder(
        Function<TlsWithCertificateConfig, T> mapper)
    {
        return new TlsWithCertificateConfigBuilder<>(mapper);
    }

    TlsWithCertificateConfig(
        Map<String, String> fields)
    {
        this.fields = fields;
    }
}
