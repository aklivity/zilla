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
package io.aklivity.zilla.runtime.model.core.internal;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

// Per-stream validation strategy for a core model, owning all in-flight parse state so concurrent
// streams sharing a per-worker CoreModelHandler never interfere. Reset on INIT, decode the
// fragment incrementally, and apply the model's final checks on FIN. Returns a three-state
// Validity so the pipeline can distinguish a parse failure (MALFORMED, always rejected) from a
// semantic-constraint failure (INVALID, relaxable under LENIENT) from a clean value (VALID).
interface CoreModelValidator
{
    Validity validate(
        int flags,
        DirectBufferEx data,
        int index,
        int length);
}
