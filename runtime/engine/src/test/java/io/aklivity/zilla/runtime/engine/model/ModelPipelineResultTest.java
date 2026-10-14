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
package io.aklivity.zilla.runtime.engine.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class ModelPipelineResultTest
{
    @Test
    public void shouldDefaultRejectionToNullWhenNotRejected()
    {
        ModelPipelineResult result = new ModelPipelineResult();

        ModelPipelineResult returned = result.set(ModelStatus.COMPLETE, 3, 3);

        assertSame(result, returned);
        assertEquals(ModelStatus.COMPLETE, result.status());
        assertEquals(3, result.consumed());
        assertEquals(3, result.produced());
        assertNull(result.rejection());
    }

    @Test
    public void shouldDefaultRejectionToNullWhenRejectedWithoutReason()
    {
        ModelPipelineResult result = new ModelPipelineResult();

        result.set(ModelStatus.REJECTED, 0, 0);

        assertEquals(ModelStatus.REJECTED, result.status());
        assertNull(result.rejection());
    }

    @Test
    public void shouldReportInvalidRejection()
    {
        ModelPipelineResult result = new ModelPipelineResult();

        ModelPipelineResult returned = result.set(ModelStatus.REJECTED, 0, 0, ModelRejection.INVALID);

        assertSame(result, returned);
        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(ModelRejection.INVALID, result.rejection());
    }

    @Test
    public void shouldReportWithheldRejection()
    {
        ModelPipelineResult result = new ModelPipelineResult();

        result.set(ModelStatus.REJECTED, 0, 0, ModelRejection.WITHHELD);

        assertEquals(ModelStatus.REJECTED, result.status());
        assertEquals(ModelRejection.WITHHELD, result.rejection());
    }

    @Test
    public void shouldClearRejectionOnSubsequentThreeArgSet()
    {
        ModelPipelineResult result = new ModelPipelineResult();
        result.set(ModelStatus.REJECTED, 0, 0, ModelRejection.WITHHELD);

        result.set(ModelStatus.COMPLETE, 4, 4);

        assertNull(result.rejection());
    }
}
