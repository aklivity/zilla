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
package io.aklivity.zilla.runtime.common.protobuf;

/**
 * The per-edge control handle a {@link ProtobufStream} stage uses to steer its immediate upstream. A
 * stage calls {@link #segmentable()} at a composite-field boundary (on the {@link ProtobufEvent#FIELD}
 * event) to opt in to receiving that field's value as a segment rather than as structured events.
 * Best-effort: the upstream may honor it (the events that follow satisfy {@link ProtobufEvent#segmented()})
 * or decline (structured events follow). A mediating {@link ProtobufTransform} supplies its own
 * controller to its downstream; a non-mediating stage passes its own through.
 */
public interface ProtobufController
{
    void segmentable();
}
