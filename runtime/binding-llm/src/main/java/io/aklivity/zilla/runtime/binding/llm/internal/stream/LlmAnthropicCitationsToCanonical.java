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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmAnthropicCitationsToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final String TYPE_URL = "web_search_result_location";
    private static final int KIND_NONE = 0;
    private static final int KIND_URL = 1;
    private static final int KIND_OTHER = 2;

    private int kind;
    private boolean nested;
    private boolean nestedClosed;

    LlmAnthropicCitationsToCanonical()
    {
        super(MODE_ARRAY);
    }

    @Override
    public void reset()
    {
        super.reset();
        kind = KIND_NONE;
        nested = false;
        nestedClosed = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "type"))
        {
            action = HOLD_STRING;
        }
        else if (kind == KIND_NONE && held() == null)
        {
            action = REJECT;
        }
        else
        {
            resolve();

            if (kind == KIND_URL && (matches(key, "url") || matches(key, "title")))
            {
                if (nestedClosed)
                {
                    action = REJECT;
                }
                else
                {
                    openNested();
                    target = key.toString();
                    action = matches(key, "title") ? PASS_STRING_OR_DROP : PASS_STRING;
                }
            }
            else
            {
                closeNested();
                action = prefix(DIALECT, key);
            }
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        clearHeld();
        kind = KIND_NONE;
        nested = false;
        nestedClosed = false;
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (held() != null)
        {
            resolve();
        }

        closeNested();

        return Status.ADVANCED;
    }

    private void resolve()
    {
        if (kind == KIND_NONE)
        {
            final boolean url = TYPE_URL.equals(held());

            kind = url ? KIND_URL : KIND_OTHER;

            emit(JsonEvent.KEY_NAME, url ? "type" : DIALECT + ":type");
            emit(JsonEvent.VALUE_STRING, url ? "url_citation" : held());
        }
    }

    private void openNested()
    {
        if (!nested)
        {
            emit(JsonEvent.KEY_NAME, "url_citation");
            emit(JsonEvent.START_OBJECT);
            nested = true;
        }
    }

    private void closeNested()
    {
        if (nested)
        {
            emit(JsonEvent.END_OBJECT);
            nested = false;
            nestedClosed = true;
        }
    }
}
