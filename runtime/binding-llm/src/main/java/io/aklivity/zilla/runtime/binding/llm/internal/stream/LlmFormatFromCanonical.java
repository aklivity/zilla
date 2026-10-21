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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.RESPONSE_FORMAT;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

abstract class LlmFormatFromCanonical extends LlmToolTransform
{
    protected static final int FORMAT_SCHEMA = 2;
    protected static final int FORMAT_OBJECT = 3;
    protected static final int FORMAT_TEXT = 4;

    protected static final String MEMBER_SCHEMA = "schema";

    private static final int FORMAT_OFF = 0;
    private static final int FORMAT_UNSET = 1;
    private static final int FORMAT_OMITTED = 5;

    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_JSON_SCHEMA = "json_schema";

    private int format;

    protected LlmFormatFromCanonical(
        int mode,
        boolean sameDialect)
    {
        super(mode, sameDialect);
    }

    protected abstract boolean formatObject();

    protected abstract boolean formatText();

    protected abstract void openFormat(
        int kind);

    protected abstract void closeFormat();

    protected abstract int schemaMember(
        CharSequence key);

    @Override
    public void reset()
    {
        super.reset();
        format = FORMAT_OFF;
    }

    protected final int formatShared()
    {
        format = FORMAT_UNSET;

        return DESCEND_OR_REPLACE;
    }

    protected final boolean formatActive(
        int depth)
    {
        return format != FORMAT_OFF && depth >= 2;
    }

    protected final int formatMember(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (depth == 2)
        {
            if (matches(key, MEMBER_TYPE))
            {
                action = capture(key);
            }
            else if (matches(key, MEMBER_JSON_SCHEMA))
            {
                action = open(FORMAT_SCHEMA) ? DESCEND : reject(RESPONSE_FORMAT);
            }
        }
        else if (format == FORMAT_SCHEMA)
        {
            action = schemaMember(key);
        }

        return action;
    }

    protected final Status formatCaptured(
        LlmCapturedValue value)
    {
        final String type = value.string(0);
        final int kind = kindOf(type);

        return kind != FORMAT_OFF && open(kind) ? Status.ADVANCED : rejected(RESPONSE_FORMAT);
    }

    protected final Status formatClosed(
        int depth)
    {
        Status status = Status.ADVANCED;

        if (depth == 2)
        {
            if (format == FORMAT_UNSET)
            {
                status = rejected(RESPONSE_FORMAT);
            }
            else if (format != FORMAT_OMITTED)
            {
                closeFormat();
            }

            format = FORMAT_OFF;
        }

        return status;
    }

    @Override
    protected final Status onReplace(
        JsonEvent event)
    {
        final boolean absent = format == FORMAT_UNSET && event == JsonEvent.VALUE_NULL;

        format = absent ? FORMAT_OFF : format;

        return absent ? Status.ADVANCED : rejected(RESPONSE_FORMAT);
    }

    private boolean open(
        int kind)
    {
        boolean opened = true;

        if (format == FORMAT_UNSET)
        {
            if (kind == FORMAT_OBJECT && !formatObject())
            {
                opened = false;
            }
            else if (kind == FORMAT_TEXT && !formatText())
            {
                format = FORMAT_OMITTED;
            }
            else
            {
                format = kind;
                openFormat(kind);
            }
        }
        else
        {
            opened = format == kind || format == FORMAT_OMITTED && kind == FORMAT_TEXT;
        }

        return opened;
    }

    private static int kindOf(
        String type)
    {
        int kind = FORMAT_OFF;

        if (TYPE_JSON_SCHEMA.equals(type))
        {
            kind = FORMAT_SCHEMA;
        }
        else if (TYPE_JSON_OBJECT.equals(type))
        {
            kind = FORMAT_OBJECT;
        }
        else if (TYPE_TEXT.equals(type))
        {
            kind = FORMAT_TEXT;
        }

        return kind;
    }
}
