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
package io.aklivity.zilla.runtime.binding.llm.internal.config;

import java.util.Optional;

import io.aklivity.zilla.runtime.binding.llm.config.LlmBindingConfig;
import io.aklivity.zilla.runtime.binding.llm.dialect.LlmLegacyDialect;
import io.aklivity.zilla.runtime.binding.llm.internal.codec.LlmContentCodecFactory;
import io.aklivity.zilla.runtime.binding.llm.internal.dialect.LlmLegacyDialectResolver;
import io.aklivity.zilla.runtime.binding.llm.sign.LlmRequestSigner;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;
import io.aklivity.zilla.runtime.engine.EngineContext;

public final class LlmLegacyBindingConfig extends LlmBindingConfig
{
    public final LlmRequestSigner signer;

    private final LlmLegacyDialectResolver dialects;

    public LlmLegacyBindingConfig(
        LlmBindingConfig binding,
        EngineContext context,
        LlmContentCodecFactory codecs)
    {
        super(binding);
        this.dialects = new LlmLegacyDialectResolver(options.dialect, context::signaler);
        this.dialects.dialects().forEach(codecs::validate);
        this.signer = options.dialect != null
            ? Optional.ofNullable(dialects.dialectNamed(options.dialect)).map(LlmLegacyDialect::signer).orElse(null)
            : null;
    }

    public LlmLegacyDialect resolveDialect(
        JsonEnvelope headers)
    {
        return dialects.resolve(headers);
    }

    public LlmLegacyDialect dialectNamed(
        String name)
    {
        return dialects.dialectNamed(name);
    }
}
