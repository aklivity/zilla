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
package io.aklivity.zilla.runtime.classifier.presidio.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.presidio.PresidioClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class PresidioClassifierHandlerTest
{
    private EngineContext engine;
    private PresidioClassifierHandler declared;
    private PresidioClassifierHandler undeclared;

    @Before
    public void init()
    {
        engine = mock(EngineContext.class);

        declared = new PresidioClassifierHandler(engine, classifier(PresidioClassifierOptionsConfig.builder()
            .endpoint("http://localhost:3000")
            .label("person", "PERSON")
            .label("email", "EMAIL_ADDRESS")
            .build()));

        undeclared = new PresidioClassifierHandler(engine, classifier(PresidioClassifierOptionsConfig.builder()
            .endpoint("http://localhost:3000/")
            .build()));
    }

    @After
    public void close()
    {
        declared.close();
        undeclared.close();
    }

    @Test
    public void shouldInitDetectorForDeclaredLabels()
    {
        assertThat(declared.initDetector(List.of("person", "email")), not(nullValue()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectLabelNotDeclared()
    {
        declared.initDetector(List.of("PERSON"));
    }

    @Test
    public void shouldInitDetectorForEntityNamesWhenLabelsNotDeclared()
    {
        assertThat(undeclared.initDetector(List.of("PERSON", "EMAIL_ADDRESS")), not(nullValue()));
    }

    @Test
    public void shouldInitDetectorWithoutTimeout()
    {
        PresidioClassifierHandler unlimited = new PresidioClassifierHandler(engine, classifier(
            PresidioClassifierOptionsConfig.builder()
                .endpoint("http://localhost:3000")
                .timeout(Duration.ZERO)
                .build()));

        assertThat(unlimited.initDetector(List.of("PERSON")), not(nullValue()));

        unlimited.close();
    }

    @Test
    public void shouldNotSupportAnonymizerOrDeanonymizer()
    {
        assertThat(declared.initAnonymizer(List.of("person")), nullValue());
        assertThat(declared.initDeanonymizer(List.of("person")), nullValue());
    }

    private static ClassifierConfig classifier(
        PresidioClassifierOptionsConfig options)
    {
        return GenericClassifierConfig.builder()
            .namespace("test")
            .name("presidio0")
            .type("presidio")
            .options(options)
            .build();
    }
}
