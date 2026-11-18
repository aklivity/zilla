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
package io.aklivity.zilla.runtime.common.json.internal;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonStep;

/**
 * The structure of the run of events parsed since a verbatim reader last drained the run, kept as a few words that
 * each delivered event updates, so nothing is recorded per event.
 * <p>
 * The structure is relative to the container stack held when the run started. A close of a container opened
 * before the run is counted and moves the run to the container below, so only what happens in the container the
 * run ends in is kept. A container opened by the run is held in two bit masks, outermost first, while it is open
 * and forgotten once it closes.
 */
final class JsonVerbatimRun
{
    private JsonStep first;
    private boolean separated;
    private int closed;
    private int opened;
    private long openedArrays;
    private long openedOccupied;
    private boolean occupied;
    private boolean bareValue;
    private JsonStep last;

    void reset()
    {
        first = null;
        separated = false;
        closed = 0;
        opened = 0;
        openedArrays = 0L;
        openedOccupied = 0L;
        occupied = false;
        bareValue = false;
        last = null;
    }

    void copyFrom(
        JsonVerbatimRun run)
    {
        first = run.first;
        separated = run.separated;
        closed = run.closed;
        opened = run.opened;
        openedArrays = run.openedArrays;
        openedOccupied = run.openedOccupied;
        occupied = run.occupied;
        bareValue = run.bareValue;
        last = run.last;
    }

    // a comma that precedes the run's first token and that the run no longer carries
    void unseparate()
    {
        separated = false;
    }

    void accept(
        JsonEvent event,
        boolean commaBefore)
    {
        switch (event)
        {
        case KEY_NAME:
            begin(JsonStep.KEY_NAME, commaBefore);
            if (opened > 0)
            {
                openedOccupied |= 1L << opened - 1;
            }
            else
            {
                occupied = true;
            }
            last = JsonStep.KEY_NAME;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            begin(JsonStep.VALUE, commaBefore);
            startValue();
            break;
        case START_OBJECT:
            begin(JsonStep.START_OBJECT, commaBefore);
            startValue();
            open(false);
            break;
        case START_ARRAY:
            begin(JsonStep.START_ARRAY, commaBefore);
            startValue();
            open(true);
            break;
        case END_OBJECT:
            begin(JsonStep.END_OBJECT, commaBefore);
            close();
            break;
        case END_ARRAY:
            begin(JsonStep.END_ARRAY, commaBefore);
            close();
            break;
        default:
            break;
        }
    }

    JsonStep leading()
    {
        return separated ? JsonStep.SEPARATOR : first;
    }

    int closed()
    {
        return closed;
    }

    int opened()
    {
        return opened;
    }

    long openedArrays()
    {
        return openedArrays;
    }

    long openedOccupied()
    {
        return openedOccupied;
    }

    boolean occupied()
    {
        return occupied;
    }

    boolean bareValue()
    {
        return bareValue;
    }

    JsonStep last()
    {
        return last;
    }

    private void begin(
        JsonStep step,
        boolean commaBefore)
    {
        if (first == null)
        {
            first = step;
            separated = commaBefore;
        }
    }

    // a value completes the key before it, otherwise it is an element of an array or a value the run met without
    // the key that named it
    private void startValue()
    {
        if (last != JsonStep.KEY_NAME)
        {
            if (opened == 0)
            {
                bareValue = true;
            }
            else if ((openedArrays >>> opened - 1 & 1L) != 0L)
            {
                openedOccupied |= 1L << opened - 1;
            }
        }
        last = JsonStep.VALUE;
    }

    private void open(
        boolean array)
    {
        final long bit = 1L << opened;
        openedArrays = array ? openedArrays | bit : openedArrays & ~bit;
        openedOccupied &= ~bit;
        opened++;
    }

    private void close()
    {
        if (opened > 0)
        {
            opened--;
            final long bit = 1L << opened;
            openedArrays &= ~bit;
            openedOccupied &= ~bit;
        }
        else
        {
            closed++;
            occupied = false;
            bareValue = false;
        }
    }
}
