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

final class LlmOpenaiResponsesCitationsFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";
    private static final String TYPE_URL = "url_citation";

    private final boolean object;

    static LlmOpenaiResponsesCitationsFromCanonical array(
        boolean sameDialect)
    {
        return new LlmOpenaiResponsesCitationsFromCanonical(MODE_ARRAY, false, sameDialect);
    }

    static LlmOpenaiResponsesCitationsFromCanonical object(
        boolean sameDialect)
    {
        return new LlmOpenaiResponsesCitationsFromCanonical(MODE_OBJECT, true, sameDialect);
    }

    private LlmOpenaiResponsesCitationsFromCanonical(
        int mode,
        boolean object,
        boolean sameDialect)
    {
        super(mode, sameDialect);
        this.object = object;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, TYPE_URL))
        {
            action = DESCEND;
        }
        else
        {
            action = unprefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (object && depth == 1)
        {
            pass(JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }
}
