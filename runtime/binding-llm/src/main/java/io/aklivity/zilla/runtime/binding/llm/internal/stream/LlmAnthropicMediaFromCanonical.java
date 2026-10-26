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

final class LlmAnthropicMediaFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";

    private final String part;
    private final String container;
    private final String dataMember;
    private final String urlMember;
    private final boolean citable;
    private final LlmCacheBreakpoint breakpoints;

    private boolean cited;

    static LlmAnthropicMediaFromCanonical image(
        boolean sameDialect)
    {
        return image(sameDialect, null);
    }

    static LlmAnthropicMediaFromCanonical document(
        boolean sameDialect)
    {
        return document(sameDialect, null);
    }

    static LlmAnthropicMediaFromCanonical image(
        boolean sameDialect,
        LlmCacheBreakpoint breakpoints)
    {
        return new LlmAnthropicMediaFromCanonical("image", "image_url", "url", "url", false, sameDialect, breakpoints);
    }

    static LlmAnthropicMediaFromCanonical document(
        boolean sameDialect,
        LlmCacheBreakpoint breakpoints)
    {
        return new LlmAnthropicMediaFromCanonical("document", "file", "file_data", "file_url", true, sameDialect, breakpoints);
    }

    private LlmAnthropicMediaFromCanonical(
        String part,
        String container,
        String dataMember,
        String urlMember,
        boolean citable,
        boolean sameDialect,
        LlmCacheBreakpoint breakpoints)
    {
        super(MODE_OBJECT, sameDialect ? DIALECT : null);
        this.part = part;
        this.container = container;
        this.dataMember = dataMember;
        this.urlMember = urlMember;
        this.citable = citable;
        this.breakpoints = breakpoints;
    }

    @Override
    public void reset()
    {
        super.reset();
        cited = false;
    }

    boolean cited()
    {
        return cited;
    }

    @Override
    protected int shared(
        CharSequence member)
    {
        return citable && matches(member, LlmCitationsEnabled.MEMBER) ? capture(member) : DROP;
    }

    @Override
    protected int breakpoint(
        CharSequence member)
    {
        return breakpoints != null ? capture(member) : DROP;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        if (matches(capturedMember(), LlmCitationsEnabled.MEMBER))
        {
            cited |= LlmCitationsEnabled.emit(this, value, LlmCitationsEnabled.MEMBER);
        }
        else
        {
            breakpoints.fromCanonical(this, value);
        }

        return Status.ADVANCED;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        final boolean inline = attributeEquals("source", LlmSectionAttributes.SOURCE_INLINE);
        int action;

        if (depth == 1 && matches(key, "type"))
        {
            action = DROP;
        }
        else if (depth == 1 && matches(key, container))
        {
            target = "source";
            action = DESCEND_KEPT;
        }
        else if (depth == 2 && matches(key, dataMember) && inline)
        {
            target = "data";
            action = STRIP_DATA_URI;
        }
        else if (depth == 2 && (matches(key, dataMember) || matches(key, urlMember)))
        {
            target = "url";
            action = PASS_STRING;
        }
        else if (depth == 2 && matches(key, "file_id"))
        {
            target = "file_id";
            action = PASS_STRING;
        }
        else if (depth == 2 && matches(key, "filename"))
        {
            action = DROP;
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
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, part);
    }

    @Override
    protected Status onDescend(
        int depth)
    {
        final String mediaType = attributeEquals("source", LlmSectionAttributes.SOURCE_INLINE) ? attribute("media-type") : null;
        Status status = Status.ADVANCED;

        if (mediaType != null)
        {
            emit(JsonEvent.KEY_NAME, "type");
            emit(JsonEvent.VALUE_STRING, "base64");
            emit(JsonEvent.KEY_NAME, "media_type");
            emit(JsonEvent.VALUE_STRING, mediaType);
        }
        else if (attributeEquals("source", LlmSectionAttributes.SOURCE_URL))
        {
            emit(JsonEvent.KEY_NAME, "type");
            emit(JsonEvent.VALUE_STRING, "url");
        }
        else if (attributeEquals("source", LlmSectionAttributes.SOURCE_REFERENCE))
        {
            emit(JsonEvent.KEY_NAME, "type");
            emit(JsonEvent.VALUE_STRING, "file");
        }
        else
        {
            status = Status.REJECTED;
        }

        return status;
    }

    @Override
    protected Status onClose(
        int depth)
    {
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }
}
