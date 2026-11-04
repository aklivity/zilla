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

final class LlmOpenaiResponsesMcpFromCanonical extends LlmToolTransform
{
    LlmOpenaiResponsesMcpFromCanonical(
        String dialect)
    {
        super(MODE_OBJECT, dialect);
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "type"))
        {
            target = "type";
            action = PASS_STRING;
        }
        else if (matches(key, "label"))
        {
            target = "server_label";
            action = PASS_STRING;
        }
        else if (matches(key, "url"))
        {
            target = "server_url";
            action = PASS_STRING;
        }
        else if (matches(key, "authorization"))
        {
            target = "authorization";
            action = PASS_STRING;
        }
        else if (matches(key, "tools"))
        {
            target = "allowed_tools";
            action = PASS_VALUE;
        }
        else
        {
            action = unprefix(key);
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
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
