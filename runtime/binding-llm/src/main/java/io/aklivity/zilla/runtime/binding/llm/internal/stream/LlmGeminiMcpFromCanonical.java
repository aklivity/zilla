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

final class LlmGeminiMcpFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = LlmGeminiDialectFactorySpi.NAME;
    private static final String NATIVE_HEADERS = "gemini:headers";
    private static final String NATIVE_TIMEOUT = "gemini:timeout";
    private static final String NATIVE_SSE_READ_TIMEOUT = "gemini:sseReadTimeout";
    private static final String NATIVE_TERMINATE_ON_CLOSE = "gemini:terminateOnClose";
    private static final String NATIVE_AUTHORIZATION_HEADER = "gemini:authorization_header";
    private static final String NATIVE_AUTHORIZATION_FORMAT = "gemini:authorization_format";
    private static final String DEFAULT_AUTHORIZATION_HEADER = "Authorization";
    private static final String DEFAULT_AUTHORIZATION_FORMAT = "Bearer %s";
    private static final String TOKEN_PLACEHOLDER = "%s";

    private final boolean sameDialect;
    private final LlmCapturedValue headers = new LlmCapturedValue();
    private final LlmCapturedValue timeout = new LlmCapturedValue();
    private final LlmCapturedValue sseReadTimeout = new LlmCapturedValue();
    private final LlmCapturedValue terminateOnClose = new LlmCapturedValue();

    private String label;
    private String url;
    private String authorization;
    private String authorizationHeader;
    private String authorizationFormat;

    LlmGeminiMcpFromCanonical(
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect ? DIALECT : null);
        this.sameDialect = sameDialect;
    }

    @Override
    public void reset()
    {
        super.reset();
        headers.clear();
        timeout.clear();
        sseReadTimeout.clear();
        terminateOnClose.clear();
        label = null;
        url = null;
        authorization = null;
        authorizationHeader = null;
        authorizationFormat = null;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (matches(key, "label") ||
            matches(key, "url") ||
            matches(key, "authorization") ||
            sameDialect &&
                (matches(key, NATIVE_HEADERS) ||
                matches(key, NATIVE_TIMEOUT) ||
                matches(key, NATIVE_SSE_READ_TIMEOUT) ||
                matches(key, NATIVE_TERMINATE_ON_CLOSE) ||
                matches(key, NATIVE_AUTHORIZATION_HEADER) ||
                matches(key, NATIVE_AUTHORIZATION_FORMAT)))
        {
            action = capture(key);
        }
        else if (matches(key, "tools"))
        {
            action = reject(key);
        }

        return action;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        final String member = capturedMember();

        switch (member)
        {
        case "label":
            label = value.string(0);
            break;
        case "url":
            url = value.string(0);
            break;
        case "authorization":
            authorization = value.string(0);
            break;
        case NATIVE_AUTHORIZATION_HEADER:
            authorizationHeader = value.string(0);
            break;
        case NATIVE_AUTHORIZATION_FORMAT:
            authorizationFormat = value.string(0);
            break;
        case NATIVE_HEADERS:
            copy(value, headers);
            break;
        case NATIVE_TIMEOUT:
            copy(value, timeout);
            break;
        case NATIVE_SSE_READ_TIMEOUT:
            copy(value, sseReadTimeout);
            break;
        default:
            copy(value, terminateOnClose);
            break;
        }

        return Status.ADVANCED;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.REJECTED;

        if (label != null)
        {
            emit(JsonEvent.KEY_NAME, "name");
            emit(JsonEvent.VALUE_STRING, label);
            emit(JsonEvent.KEY_NAME, "streamableHttpTransport");
            emit(JsonEvent.START_OBJECT);

            if (url != null)
            {
                emit(JsonEvent.KEY_NAME, "url");
                emit(JsonEvent.VALUE_STRING, url);
            }

            emitHeaders();
            emitNative("timeout", timeout);
            emitNative("sseReadTimeout", sseReadTimeout);
            emitNative("terminateOnClose", terminateOnClose);

            emit(JsonEvent.END_OBJECT);
            pass(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private void emitHeaders()
    {
        final boolean entries = headers.isObject(0) && headers.event(1) == JsonEvent.KEY_NAME;

        if (authorization != null || entries)
        {
            emit(JsonEvent.KEY_NAME, "headers");
            emit(JsonEvent.START_OBJECT);

            if (authorization != null)
            {
                final String name = authorizationHeader != null ? authorizationHeader : DEFAULT_AUTHORIZATION_HEADER;
                final String format = authorizationFormat != null ? authorizationFormat : DEFAULT_AUTHORIZATION_FORMAT;

                emit(JsonEvent.KEY_NAME, name);
                emit(JsonEvent.VALUE_STRING, format.replace(TOKEN_PLACEHOLDER, authorization));
            }

            int index = 1;

            while (entries && headers.event(index) == JsonEvent.KEY_NAME)
            {
                emit(JsonEvent.KEY_NAME, headers.text(index));
                emitCaptured(headers, index + 1);
                index = headers.end(index + 1);
            }

            emit(JsonEvent.END_OBJECT);
        }
    }

    private void emitNative(
        String member,
        LlmCapturedValue value)
    {
        if (value.count() > 0)
        {
            emit(JsonEvent.KEY_NAME, member);
            emitCaptured(value, 0);
        }
    }

    private static void copy(
        LlmCapturedValue from,
        LlmCapturedValue to)
    {
        to.clear();

        for (int index = 0; index < from.count(); index++)
        {
            to.add(from.event(index), from.text(index), Integer.MAX_VALUE);
        }
    }
}
