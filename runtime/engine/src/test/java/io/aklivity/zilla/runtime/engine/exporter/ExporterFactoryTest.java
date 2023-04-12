package io.aklivity.zilla.runtime.engine.exporter;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.exporter.TestExporter;

public class ExporterFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        // GIVEN
        Configuration config = new Configuration();
        ExporterFactory factory = ExporterFactory.instantiate();

        // WHEN
        Exporter exporter = factory.create("test", config);

        // THEN
        assertThat(exporter, instanceOf(TestExporter.class));
    }
}
