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
package io.aklivity.zilla.runtime.common.json;

/**
 * One descended level of a {@link JsonPosition}: a container the insertion point lives in, carrying both its
 * kind and its occupancy. The occupancy is folded into the step kind so there is no separate boolean (which
 * would be meaningless on a scalar): {@code START_*} is an entered-but-empty container, {@code CONTINUE_*} a
 * container that already has a child (so the next sibling needs a leading separator). The terminal
 * {@link #KEY_NAME} marks an after-key cut — verbatim emitted a key and the value is expected, so the
 * generator writes the value of a pending key rather than a fresh member. A {@code JsonStep} parallels a
 * {@link JsonEvent}: {@code START_*}/{@code KEY_NAME} mirror the events, while {@code CONTINUE_*} are the
 * occupancy-bearing additions — a <em>state</em>, where the event is a <em>transition</em>.
 */
public enum JsonStep
{
    START_OBJECT,
    START_ARRAY,
    CONTINUE_OBJECT,
    CONTINUE_ARRAY,
    KEY_NAME
}
