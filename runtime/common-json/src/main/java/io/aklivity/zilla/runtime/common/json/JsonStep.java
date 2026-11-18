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
package io.aklivity.zilla.runtime.common.json;

/**
 * The kind of token a {@link JsonVerbatim} run begins with or last starts a member with — the vocabulary a
 * recipient needs to keep its own generator state coherent as it splices the run's bytes, nothing more. It is
 * deliberately not the full {@link JsonEvent} set: the five scalar {@code VALUE_*} events of {@link JsonEvent}
 * collapse to a single {@link #VALUE}, since a recipient copying bytes never needs a scalar's type, only that a
 * value occupies a slot. {@link #SEPARATOR} marks a comma that precedes the first token of a run.
 */
public enum JsonStep
{
    START_OBJECT,
    END_OBJECT,
    START_ARRAY,
    END_ARRAY,
    KEY_NAME,
    VALUE,
    SEPARATOR
}
