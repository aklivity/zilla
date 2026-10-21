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

final class LlmOpenaiCitationsFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";
    private static final String TYPE_URL = "url_citation";

    private boolean typed;

    LlmOpenaiCitationsFromCanonical(
        boolean sameDialect)
    {
        super(MODE_ARRAY, sameDialect);
    }

    @Override
    public void reset()
    {
        super.reset();
        typed = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 2)
        {
            action = unprefix(DIALECT, key);
        }
        else if (matches(key, "type"))
        {
            action = HOLD_STRING;
        }
        else if (!TYPE_URL.equals(held()))
        {
            action = DROP;
        }
        else if (matches(key, TYPE_URL))
        {
            emitType();
            target = TYPE_URL;
            action = DESCEND_KEPT;
        }
        else
        {
            emitType();
            action = unprefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        clearHeld();
        typed = false;
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (depth > 1)
        {
            pass(JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }

    private void emitType()
    {
        if (!typed)
        {
            typed = true;
            emit(JsonEvent.KEY_NAME, "type");
            emit(JsonEvent.VALUE_STRING, TYPE_URL);
        }
    }
}
