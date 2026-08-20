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

    /**
     * Returns the authorization in effect for the message currently being transformed.
     * <p>
     * A mediating stage may override this to scope the authorization further for a nested composition; a
     * non-mediating stage passes its own through, so this reflects the authorization the pipeline received
     * for the current message unless some stage along the way has narrowed it.
     * </p>
     *
     * @return the authorization in effect for the current message
     */
    long authorization();

    /**
     * Returns the metadata travelling alongside the value being transformed, addressed by name rather than
     * by position within the value. The default is {@link ProtobufEnvelope#NONE}, in force when no envelope
     * was supplied to the pipeline, so a stage reads an empty envelope rather than no envelope at all. A
     * mediating stage may supply its own to its downstream.
     *
     * @return the envelope in force for this pipeline
     */
    default ProtobufEnvelope envelope()
    {
        return ProtobufEnvelope.NONE;
    }

    /**
     * Reports {@code sourceBytes} source bytes consumed by a verbatim segment write so the upstream advances
     * past them and re-exposes the value's remainder from {@link ProtobufSource#segment()} on resume — the
     * pushback that lets a bounded sink stream a length-delimited value without tracking its own write cursor.
     */
    default void consumed(
        int sourceBytes)
    {
    }
}
