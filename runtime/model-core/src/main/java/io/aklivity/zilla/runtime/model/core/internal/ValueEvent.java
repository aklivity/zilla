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

// The event vocabulary CoreExtModelPipeline pumps in, shared by the composed bytes and string ext
// pipelines. BytesEvent and StringEvent are independent public SPI types (mirroring their independent
// discovery), but a value with no internal structure frames the same way whichever model carries it, so
// the pump names this one shape and each subclass maps it to its own model's event on the way out.
enum ValueEvent
{
    START_VALUE,
    SEGMENT,
    END_VALUE
}
