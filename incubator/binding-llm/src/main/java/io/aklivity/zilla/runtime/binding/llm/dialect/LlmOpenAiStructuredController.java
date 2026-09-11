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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

/**
 * Wraps the {@link JsonController} an OpenAI dialect transform receives from its upstream, declining
 * {@link #segmentable()} and {@link #verbatim()} so every event the transform forwards downstream arrives
 * (and is forwarded) as a plain structured event rather than a {@code SEGMENT}/{@code VERBATIM} byte run.
 * <p>
 * An OpenAI dialect transform renames object keys and remaps one scalar value by reading their text via
 * {@link io.aklivity.zilla.runtime.common.json.JsonSource#getStringView()}; that accessor is only valid on a
 * structured event, so the transform must keep its downstream from opting a value into segmented or verbatim
 * delivery in its place. The cost is a canonical re-render of every value at the terminal generator instead
 * of a raw byte copy — acceptable for the modest, chunk-sized payloads an LLM response streams, and far
 * simpler than a stage that switches its renaming logic per delivery mode.
 * </p>
 * <p>
 * One instance is held per transform and rewrapped once per event via {@link #wrap(JsonController)}; it is
 * never retained beyond the current {@code transform} call.
 * </p>
 */
final class LlmOpenAiStructuredController implements JsonController
{
    private JsonController delegate;

    LlmOpenAiStructuredController wrap(
        JsonController delegate)
    {
        this.delegate = delegate;
        return this;
    }

    @Override
    public void segmentable()
    {
    }

    @Override
    public long authorization()
    {
        return delegate.authorization();
    }

    @Override
    public JsonEnvelope envelope()
    {
        return delegate.envelope();
    }

    @Override
    public void verbatim()
    {
    }

    @Override
    public void consumed(
        int sourceBytes)
    {
        delegate.consumed(sourceBytes);
    }
}
