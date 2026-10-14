/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.classifier;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Result;
import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer.Status;

public class DeanonymizerResultTest
{
    @Test
    public void shouldSetOutcome()
    {
        Result result = new Result();

        result.set(Status.COMPLETE, 11, 9, 2);

        assertThat(result.status(), equalTo(Status.COMPLETE));
        assertThat(result.consumed(), equalTo(11));
        assertThat(result.produced(), equalTo(9));
        assertThat(result.unresolved(), equalTo(2));
    }

    @Test
    public void shouldReturnSameInstance()
    {
        Result result = new Result();

        assertThat(result.set(Status.UNDERFLOW, 0, 0, 0), sameInstance(result));
    }

    @Test
    public void shouldReplaceUnresolvedWhenReused()
    {
        Result result = new Result();

        result.set(Status.COMPLETE, 11, 9, 2);
        result.set(Status.OVERFLOW, 4, 4, 0);

        assertThat(result.status(), equalTo(Status.OVERFLOW));
        assertThat(result.consumed(), equalTo(4));
        assertThat(result.produced(), equalTo(4));
        assertThat(result.unresolved(), equalTo(0));
    }
}
