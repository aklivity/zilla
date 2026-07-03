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

import java.lang.foreign.MemorySegment;

import jakarta.json.stream.JsonParser;

public interface JsonTokenizer
{
    void reset();

    void wrap(
        MemorySegment segment,
        int offset,
        int limit);

    void terminal(
        boolean terminalEof);

    void window(
        int length);

    boolean advance();

    JsonParser.Event event();

    void clearEvent();

    String stringValue();

    JsonStringView stringView();

    void markScratchConsumed(
        int consumed);

    long streamOffset();

    long line();

    long column();

    long valueStreamStart();

    long valueStreamEnd();

    boolean done();

    long documentEndOffset();

    boolean inObjectContext();

    boolean inArrayContext();

    boolean memberSeparated();

    boolean separatorBefore();

    boolean fragmenting();

    boolean stringVerbatim();

    String currentPath();

    void segmenting(
        boolean segmenting);

    void scalarSegment(
        boolean scalarSegment);
}
