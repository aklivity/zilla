/*
 * Copyright 2021-2024 Aklivity Inc
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

import org.agrona.DirectBuffer;

// Per-stream validation strategy for a core model, owning all in-flight parse state so concurrent
// streams sharing a per-worker CoreModelHandler never interfere. Reset on FLAGS_INIT, decode the
// fragment incrementally, and apply the model's final checks on FLAGS_FIN.
interface CoreModelValidator
{
    int FLAGS_INIT = 0x02;
    int FLAGS_FIN = 0x01;
    int FLAGS_COMPLETE = 0x03;

    boolean validate(
        int flags,
        DirectBuffer data,
        int index,
        int length);
}
