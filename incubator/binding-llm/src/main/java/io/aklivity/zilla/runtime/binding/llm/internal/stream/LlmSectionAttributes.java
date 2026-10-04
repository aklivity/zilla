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

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmDataExFW;

final class LlmSectionAttributes
{
    static final String MEDIA_TYPE = "media-type";
    static final String SOURCE = "source";
    static final String URI = "uri";
    static final String REF_ID = "ref-id";
    static final String FILENAME = "filename";
    static final String TOOL_NAME = "tool-name";
    static final String TOOL_CALL_ID = "tool-call-id";
    static final String IS_ERROR = "is-error";
    static final String PROVIDER_EXECUTED = "provider-executed";

    static final String SOURCE_INLINE = "inline";
    static final String SOURCE_URL = "url";
    static final String SOURCE_REFERENCE = "reference";

    static final String TRUE = "true";
    static final String FALSE = "false";

    private String mediaType;
    private String source;
    private String uri;
    private String refId;
    private String filename;
    private String toolName;
    private String toolCallId;
    private String isError;
    private String providerExecuted;

    void clear()
    {
        mediaType = null;
        source = null;
        uri = null;
        refId = null;
        filename = null;
        toolName = null;
        toolCallId = null;
        isError = null;
        providerExecuted = null;
    }

    void copyFrom(
        LlmSectionAttributes that)
    {
        mediaType = that.mediaType;
        source = that.source;
        uri = that.uri;
        refId = that.refId;
        filename = that.filename;
        toolName = that.toolName;
        toolCallId = that.toolCallId;
        isError = that.isError;
        providerExecuted = that.providerExecuted;
    }

    String mediaType()
    {
        return mediaType;
    }

    String source()
    {
        return source;
    }

    String uri()
    {
        return uri;
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

    void uri(
        String uri)
    {
        this.uri = uri;
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

    void url(
        String uri)
    {
        this.source = SOURCE_URL;
        this.uri = uri;
    }

    void reference(
        String refId)
    {
        this.source = SOURCE_REFERENCE;
        this.refId = refId;
    }

    void writeTo(
        LlmDataExFW.Builder builder)
    {
        write(builder, MEDIA_TYPE, mediaType);
        write(builder, SOURCE, source);
        write(builder, URI, uri);
        write(builder, REF_ID, refId);
        write(builder, FILENAME, filename);
        write(builder, TOOL_NAME, toolName);
        write(builder, TOOL_CALL_ID, toolCallId);
        write(builder, IS_ERROR, isError);
        write(builder, PROVIDER_EXECUTED, providerExecuted);
    }

    private static void write(
        LlmDataExFW.Builder builder,
        String name,
        String value)
    {
        if (value != null)
        {
            builder.attributesItem(a -> a.name(name).value(value));
        }
    }
}
