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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A wire version of the A2A JSON-RPC binding, translating to and from the canonical (v1.0) shapes carried on
 * the application stream.
 */
public interface A2aDialect
{
    String version();

    A2aOperation operation(
        String method);

    A2aRequestParams request(
        A2aOperation operation,
        JsonObject params);

    JsonValue result(
        A2aOperation operation,
        JsonValue canonical);

    JsonObject card(
        JsonObject canonical,
        String url,
        boolean secured);

    JsonObject requestParams(
        A2aOperation operation,
        A2aRequestParams request,
        String tenant);

    JsonValue resultFromWire(
        A2aOperation operation,
        JsonValue wire);

    JsonObject cardFromWire(
        JsonObject wire);

    boolean verbatim();
}
