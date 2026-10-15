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

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmAttributeFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionDataExFW;

final class LlmSectionAttributes
{
    static final String MEDIA_TYPE = "media-type";
    static final String SOURCE = "source";
    static final String REF_ID = "ref-id";
    static final String FILENAME = "filename";
    static final String TOOL_NAME = "tool-name";
    static final String TOOL_CALL_ID = "tool-call-id";
    static final String IS_ERROR = "is-error";
    static final String PROVIDER_EXECUTED = "provider-executed";
    static final String REASONING_TAG = "reasoning-tag";

    static final String SOURCE_INLINE = "inline";
    static final String SOURCE_URL = "url";
    static final String SOURCE_REFERENCE = "reference";

    static final String TRUE = "true";
    static final String FALSE = "false";

    private final Consumer<LlmAttributeFW> reader = this::read;

    private String mediaType;
    private String source;
    private String refId;
    private String filename;
    private String toolName;
    private String toolCallId;
    private String isError;
    private String providerExecuted;
    private String reasoningTag;

    void clear()
    {
        mediaType = null;
        source = null;
        refId = null;
        filename = null;
        toolName = null;
        toolCallId = null;
        isError = null;
        providerExecuted = null;
        reasoningTag = null;
    }

    void readFrom(
        LlmSectionDataExFW dataEx)
    {
        clear();
        mergeFrom(dataEx);
    }

    void mergeFrom(
        LlmSectionDataExFW dataEx)
    {
        if (dataEx != null)
        {
            dataEx.attributes().forEach(reader);
        }
    }

    boolean present()
    {
        return mediaType != null || source != null || refId != null || filename != null ||
            toolName != null || toolCallId != null || isError != null || providerExecuted != null ||
            reasoningTag != null;
    }

    void difference(
        LlmSectionAttributes current,
        LlmSectionAttributes previous)
    {
        mediaType = changed(current.mediaType, previous.mediaType);
        source = changed(current.source, previous.source);
        refId = changed(current.refId, previous.refId);
        filename = changed(current.filename, previous.filename);
        toolName = changed(current.toolName, previous.toolName);
        toolCallId = changed(current.toolCallId, previous.toolCallId);
        isError = changed(current.isError, previous.isError);
        providerExecuted = changed(current.providerExecuted, previous.providerExecuted);
        reasoningTag = changed(current.reasoningTag, previous.reasoningTag);
    }

    void copyFrom(
        LlmSectionAttributes that)
    {
        mediaType = that.mediaType;
        source = that.source;
        refId = that.refId;
        filename = that.filename;
        toolName = that.toolName;
        toolCallId = that.toolCallId;
        isError = that.isError;
        providerExecuted = that.providerExecuted;
        reasoningTag = that.reasoningTag;
    }

    String get(
        String name)
    {
        return switch (name)
        {
        case MEDIA_TYPE -> mediaType;
        case SOURCE -> source;
        case REF_ID -> refId;
        case FILENAME -> filename;
        case TOOL_NAME -> toolName;
        case TOOL_CALL_ID -> toolCallId;
        case IS_ERROR -> isError;
        case PROVIDER_EXECUTED -> providerExecuted;
        case REASONING_TAG -> reasoningTag;
        default -> null;
        };
    }

    String mediaType()
    {
        return mediaType;
    }

    String source()
    {
        return source;
    }

    String refId()
    {
        return refId;
    }

    String filename()
    {
        return filename;
    }

    String toolName()
    {
        return toolName;
    }

    String toolCallId()
    {
        return toolCallId;
    }

    String isError()
    {
        return isError;
    }

    String providerExecuted()
    {
        return providerExecuted;
    }

    String reasoningTag()
    {
        return reasoningTag;
    }

    void mediaType(
        String mediaType)
    {
        this.mediaType = mediaType;
    }

    void source(
        String source)
    {
        this.source = source;
    }

    void refId(
        String refId)
    {
        this.refId = refId;
    }

    void filename(
        String filename)
    {
        this.filename = filename;
    }

    void toolName(
        String toolName)
    {
        this.toolName = toolName;
    }

    void toolCallId(
        String toolCallId)
    {
        this.toolCallId = toolCallId;
    }

    void isError(
        boolean isError)
    {
        this.isError = isError ? TRUE : FALSE;
    }

    void isError(
        String isError)
    {
        this.isError = isError;
    }

    void providerExecuted(
        boolean providerExecuted)
    {
        this.providerExecuted = providerExecuted ? TRUE : FALSE;
    }

    void inline(
        String mediaType)
    {
        this.source = SOURCE_INLINE;
        this.mediaType = mediaType;
    }

    void url()
    {
        this.source = SOURCE_URL;
    }

    void reference(
        String refId)
    {
        this.source = SOURCE_REFERENCE;
        this.refId = refId;
    }

    void writeTo(
        LlmSectionDataExFW.Builder builder)
    {
        write(builder, MEDIA_TYPE, mediaType);
        write(builder, SOURCE, source);
        write(builder, REF_ID, refId);
        write(builder, FILENAME, filename);
        write(builder, TOOL_NAME, toolName);
        write(builder, TOOL_CALL_ID, toolCallId);
        write(builder, IS_ERROR, isError);
        write(builder, PROVIDER_EXECUTED, providerExecuted);
        write(builder, REASONING_TAG, reasoningTag);
    }

    private static String changed(
        String current,
        String previous)
    {
        return current != null && !current.equals(previous) ? current : null;
    }

    private void read(
        LlmAttributeFW attribute)
    {
        final String value = attribute.value().asString();

        switch (attribute.name().asString())
        {
        case MEDIA_TYPE:
            mediaType = value;
            break;
        case SOURCE:
            source = value;
            break;
        case REF_ID:
            refId = value;
            break;
        case FILENAME:
            filename = value;
            break;
        case TOOL_NAME:
            toolName = value;
            break;
        case TOOL_CALL_ID:
            toolCallId = value;
            break;
        case IS_ERROR:
            isError = value;
            break;
        case PROVIDER_EXECUTED:
            providerExecuted = value;
            break;
        case REASONING_TAG:
            reasoningTag = value;
            break;
        default:
            break;
        }
    }

    private static void write(
        LlmSectionDataExFW.Builder builder,
        String name,
        String value)
    {
        if (value != null)
        {
            builder.attributesItem(a -> a.name(name).value(value));
        }
    }
}
