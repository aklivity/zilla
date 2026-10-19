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

final class LlmOpenaiControlToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_INCLUDE_USAGE = "include_usage";

    private static final int REPLACING_NONE = 0;
    private static final int REPLACING_STREAM = 1;
    private static final int REPLACING_OPTIONS = 2;
    private static final int REPLACING_USAGE = 3;

    private int replacing;
    private boolean streamSeen;
    private boolean streaming;
    private boolean optionsSeen;
    private boolean usageSeen;
    private boolean usageRequested;

    boolean streamSeen()
    {
        return streamSeen;
    }

    boolean streaming()
    {
        return streaming;
    }

    boolean optionsSeen()
    {
        return optionsSeen;
    }

    boolean usageRequested()
    {
        return usageRequested;
    }

    @Override
    public void reset()
    {
        super.reset();
        replacing = REPLACING_NONE;
        streamSeen = false;
        streaming = false;
        optionsSeen = false;
        usageSeen = false;
        usageRequested = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, MEMBER_STREAM))
        {
            target = MEMBER_STREAM;
            replacing = REPLACING_STREAM;
            action = REPLACE_VALUE;
        }
        else if (depth == 1 && matches(key, MEMBER_STREAM_OPTIONS))
        {
            target = MEMBER_STREAM_OPTIONS;
            replacing = REPLACING_OPTIONS;
            action = DESCEND_KEPT_OR_REPLACE;
        }
        else if (depth == 2 && matches(key, MEMBER_INCLUDE_USAGE))
        {
            target = MEMBER_INCLUDE_USAGE;
            replacing = REPLACING_USAGE;
            action = REPLACE_VALUE;
        }
        else
        {
            action = prefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onDescend(
        int depth)
    {
        optionsSeen = true;
        usageSeen = false;

        return Status.ADVANCED;
    }

    @Override
    protected Status onReplace(
        JsonEvent event)
    {
        final boolean flag = event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL;
        Status status = Status.REJECTED;

        switch (replacing)
        {
        case REPLACING_STREAM:
            if (flag)
            {
                streamSeen = true;
                streaming = event == JsonEvent.VALUE_TRUE;
                emit(JsonEvent.KEY_NAME, MEMBER_STREAM);
                emit(event);
                status = Status.ADVANCED;
            }
            break;
        case REPLACING_OPTIONS:
            if (event == JsonEvent.VALUE_NULL)
            {
                optionsSeen = true;
                emit(JsonEvent.KEY_NAME, MEMBER_STREAM_OPTIONS);
                emit(JsonEvent.START_OBJECT);
                emitIncludeUsage();
                emit(JsonEvent.END_OBJECT);
                status = Status.ADVANCED;
            }
            break;
        case REPLACING_USAGE:
            if (flag)
            {
                usageSeen = true;
                usageRequested = event == JsonEvent.VALUE_TRUE;
                emitIncludeUsage();
                status = Status.ADVANCED;
            }
            break;
        default:
            break;
        }

        return status;
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (depth == 2 && !usageSeen)
        {
            emitIncludeUsage();
        }

        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }

    private void emitIncludeUsage()
    {
        emit(JsonEvent.KEY_NAME, MEMBER_INCLUDE_USAGE);
        emit(JsonEvent.VALUE_TRUE);
    }
}
