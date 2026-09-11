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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

public class LlmOpenAiStructuredControllerTest
{
    private final JsonController delegate = mock(JsonController.class);
    private final LlmOpenAiStructuredController controller = new LlmOpenAiStructuredController();

    @Test
    public void shouldDeclineSegmentableWithoutForwardingToDelegate()
    {
        controller.wrap(delegate);

        controller.segmentable();

        verify(delegate, never()).segmentable();
    }

    @Test
    public void shouldDeclineVerbatimWithoutForwardingToDelegate()
    {
        controller.wrap(delegate);

        controller.verbatim();

        verify(delegate, never()).verbatim();
    }

    @Test
    public void shouldDelegateAuthorization()
    {
        when(delegate.authorization()).thenReturn(42L);

        controller.wrap(delegate);

        assertThat(controller.authorization(), equalTo(42L));
    }

    @Test
    public void shouldDelegateEnvelope()
    {
        JsonEnvelope envelope = mock(JsonEnvelope.class);
        when(delegate.envelope()).thenReturn(envelope);

        controller.wrap(delegate);

        assertThat(controller.envelope(), sameInstance(envelope));
    }

    @Test
    public void shouldDelegateConsumed()
    {
        controller.wrap(delegate);

        controller.consumed(7);

        verify(delegate).consumed(7);
    }
}
