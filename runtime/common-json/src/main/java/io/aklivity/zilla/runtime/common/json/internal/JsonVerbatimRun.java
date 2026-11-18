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
 * The structure of the run of events parsed since a verbatim reader last drained the run, kept as three words that
 * each delivered event updates, so nothing is recorded per event and a run is copied in three assignments.
 * <p>
 * The structure is relative to the container stack held when the run started. A close of a container opened
 * before the run is counted and moves the run to the container below, so only what happens in the container the
 * run ends in is kept. A container opened by the run is held in two bit masks, outermost first, while it is open
 * and forgotten once it closes.
 * <p>
 * The {@code mode} word holds, from bit 0: the first token of the run (3 bits, 0 for none, otherwise the ordinal
 * of its {@link JsonStep} plus one), a comma before it, the last member-level token (2 bits: 0 none, 1 key, 2
 * value), a member named in the container the run ends in, a value started there without a key, the run drained
 * (the next event or pull starts a new run), the containers closed (7 bits) and the containers left open (7 bits).
 */
final class JsonVerbatimRun
{
    private static final JsonStep[] STEPS = JsonStep.values();

    private static final int FIRST_MASK = 0x7;
    private static final int SEPARATED = 1 << 3;
    private static final int LAST_SHIFT = 4;
    private static final int LAST_MASK = 0x3 << LAST_SHIFT;
    private static final int LAST_KEY = 1 << LAST_SHIFT;
    private static final int LAST_VALUE = 2 << LAST_SHIFT;
    private static final int OCCUPIED = 1 << 6;
    private static final int BARE_VALUE = 1 << 7;
    private static final int DRAINED = 1 << 8;
    private static final int CLOSED_SHIFT = 9;
    private static final int OPENED_SHIFT = 16;
    private static final int COUNT_MASK = 0x7f;

    private int mode;
    private long openedArrays;
    private long openedOccupied;

    void reset()
    {
        mode = 0;
        openedArrays = 0L;
        openedOccupied = 0L;
    }

    void copyFrom(
        JsonVerbatimRun run)
    {
        mode = run.mode;
        openedArrays = run.openedArrays;
        openedOccupied = run.openedOccupied;
    }

    // a comma that precedes the run's first token and that the run no longer carries
    void unseparate()
    {
        mode &= ~SEPARATED;
    }

    // every byte of the run has been pulled: the next event or pull starts a new run
    void drain()
    {
        mode |= DRAINED;
    }

    boolean drained()
    {
        return (mode & DRAINED) != 0;
    }

    void accept(
        JsonEvent event,
        boolean commaBefore)
    {
        switch (event)
        {
        case KEY_NAME:
            begin(JsonStep.KEY_NAME.ordinal() + 1, commaBefore);
            final int opened = opened();
            if (opened > 0)
            {
                openedOccupied |= 1L << opened - 1;
            }
            else
            {
                mode |= OCCUPIED;
            }
            mode = mode & ~LAST_MASK | LAST_KEY;
            break;
        case VALUE_STRING:
        case VALUE_NUMBER:
        case VALUE_TRUE:
        case VALUE_FALSE:
        case VALUE_NULL:
            begin(JsonStep.VALUE.ordinal() + 1, commaBefore);
            startValue();
            break;
        case START_OBJECT:
            begin(JsonStep.START_OBJECT.ordinal() + 1, commaBefore);
            startValue();
            open(false);
            break;
        case START_ARRAY:
            begin(JsonStep.START_ARRAY.ordinal() + 1, commaBefore);
            startValue();
            open(true);
            break;
        case END_OBJECT:
            begin(JsonStep.END_OBJECT.ordinal() + 1, commaBefore);
            close();
            break;
        case END_ARRAY:
            begin(JsonStep.END_ARRAY.ordinal() + 1, commaBefore);
            close();
            break;
        default:
            break;
        }
    }

    JsonStep leading()
    {
        return (mode & SEPARATED) != 0 ? JsonStep.SEPARATOR : first();
    }

    int closed()
    {
        return mode >>> CLOSED_SHIFT & COUNT_MASK;
    }

    int opened()
    {
        return mode >>> OPENED_SHIFT & COUNT_MASK;
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
        return (mode & OCCUPIED) != 0;
    }

    boolean bareValue()
    {
        return (mode & BARE_VALUE) != 0;
    }

    JsonStep last()
    {
        final int last = mode & LAST_MASK;
        return last == LAST_KEY ? JsonStep.KEY_NAME : last == LAST_VALUE ? JsonStep.VALUE : null;
    }

    private JsonStep first()
    {
        final int first = mode & FIRST_MASK;
        return first == 0 ? null : STEPS[first - 1];
    }

    private void begin(
        int step,
        boolean commaBefore)
    {
        if ((mode & FIRST_MASK) == 0)
        {
            mode |= step | (commaBefore ? SEPARATED : 0);
        }
    }

    // a value completes the key before it, otherwise it is an element of an array or a value the run met without
    // the key that named it
    private void startValue()
    {
        if ((mode & LAST_MASK) != LAST_KEY)
        {
            final int opened = opened();
            if (opened == 0)
            {
                mode |= BARE_VALUE;
            }
            else if ((openedArrays >>> opened - 1 & 1L) != 0L)
            {
                openedOccupied |= 1L << opened - 1;
            }
        }
        mode = mode & ~LAST_MASK | LAST_VALUE;
    }

    private void open(
        boolean array)
    {
        final int opened = opened();
        final long bit = 1L << opened;
        openedArrays = array ? openedArrays | bit : openedArrays & ~bit;
        openedOccupied &= ~bit;
        mode += 1 << OPENED_SHIFT;
    }

    private void close()
    {
        final int opened = opened();
        if (opened > 0)
        {
            final long bit = 1L << opened - 1;
            openedArrays &= ~bit;
            openedOccupied &= ~bit;
            mode -= 1 << OPENED_SHIFT;
        }
        else
        {
            mode += 1 << CLOSED_SHIFT;
            mode &= ~(OCCUPIED | BARE_VALUE);
        }
    }
}
