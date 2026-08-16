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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

// Common whole-value transform shape shared by the composed bytes and string ext pipelines. BytesTransform
// and StringTransform are independent public SPI types (mirroring their independent discovery), but their
// composition and execution machinery is identical, so BytesModelContext/StringModelContext each adapt
// their own installed extensions into this one shape before handing them to CoreExtModelPipeline.
interface ValueTransform
{
    int OMIT = -1;

    int transform(
        DirectBufferEx value,
        int index,
        int length,
        MutableDirectBufferEx dst,
        int dstIndex);
}
