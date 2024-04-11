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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiTrait;

public final class AsyncapiTraitView extends AsyncapiResolvable<AsyncapiTrait>
{
    private final AsyncapiTrait trait;

    public String refKey()
    {
        return key;
    }

    public AsyncapiSchema commonHeaders()
    {
        return trait.headers;
    }

    public static AsyncapiTraitView of(
        Map<String, AsyncapiTrait> traits,
        AsyncapiTrait asyncapiTrait)
    {
        return new AsyncapiTraitView(traits, asyncapiTrait);
    }

    private AsyncapiTraitView(
        Map<String, AsyncapiTrait> traits,
        AsyncapiTrait trait)
    {
        super(traits, "#/components/messageTraits/(\\w+)");
        this.trait = trait.ref == null ? trait : resolveRef(trait.ref);
    }
}
