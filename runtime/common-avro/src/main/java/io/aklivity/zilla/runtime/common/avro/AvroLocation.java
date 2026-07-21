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
package io.aklivity.zilla.runtime.common.avro;

/**
 * The position of the current event within the datum, for diagnostics. Avro binary has no line or
 * column, so a location reports the parse nesting {@link #depth()} (the number of open
 * record/array/map/value frames) and the byte {@link #getStreamOffset()} from the start of the datum.
 */
public interface AvroLocation
{
    int depth();

    long getStreamOffset();
}
