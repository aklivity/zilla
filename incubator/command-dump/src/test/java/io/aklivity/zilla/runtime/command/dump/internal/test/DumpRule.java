/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal.test;

import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.Container;

import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class DumpRule implements TestRule
{
    private static final Path ENGINE_PATH = Path.of("target/zilla-itests");
    private static final Path BINDINGS_PATH = ENGINE_PATH.resolve("bindings");
    private static final Path LABELS_PATH = ENGINE_PATH.resolve("labels");
    private static final Path PCAP_PATH = ENGINE_PATH.resolve("actual.pcap");
    private static final Path TXT_PATH = ENGINE_PATH.resolve("actual.txt");
    private static final DumpCommandRunner DUMP = new DumpCommandRunner();
    private static final TsharkRunner TSHARK = new TsharkRunner();

    private String[] bindings;
    private String[] labels;
    private Map<String, Integer> lookupLabel;

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        Class<?> testClass = description.getTestClass();
        String testMethod = description.getMethodName();
        String resourceName = String.format("%s_%s.txt", testClass.getSimpleName(), testMethod);
        URL resource = testClass.getResource(resourceName);
        assert resource != null;
        String expected = null;
        try
        {
            expected = Files.readString(Path.of(resource.toURI()));
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        final String expected0 = expected;

        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                Files.createDirectories(ENGINE_PATH);
                writeLabels();
                writeBindings();
                base.evaluate();
                DUMP.createPcap(PCAP_PATH);
                Container.ExecResult result = TSHARK.createTxt(PCAP_PATH);
                Files.writeString(TXT_PATH, result.getStdout());
                assertThat(result.getExitCode(), equalTo(0));
                assert expected0 != null;
                assertThat(result.getStdout(), containsString(expected0));
            }
        };
    }

    public DumpRule labels(
        String... labels)
    {
        this.labels = labels;
        this.lookupLabel = new HashMap<>();
        for (int i = 0; i < labels.length; i++)
        {
            lookupLabel.put(labels[i], i + 1);
        }
        lookupLabel.put("0", 0);
        return this;
    }

    public DumpRule bindings(
        String... bindings)
    {
        this.bindings = bindings;
        return this;
    }

    private void writeLabels() throws Exception
    {
        if (labels.length > 0)
        {
            String allLabels = String.join("\n", labels) + "\n";
            Files.write(LABELS_PATH, allLabels.getBytes(), CREATE, TRUNCATE_EXISTING);
        }
    }

    private void writeBindings() throws Exception
    {
        long[] records = new long[bindings.length];
        for (int i = 0; i < bindings.length; i++)
        {
            String[] parts = bindings[i].split("\\.");
            int namespaceId = lookupLabel.get(parts[0]);
            int localId = lookupLabel.get(parts[1]);
            records[i] = NamespacedId.id(namespaceId, localId);
        }
        try (ByteChannel channel = Files.newByteChannel(BINDINGS_PATH, CREATE, WRITE))
        {
            ByteBuffer byteBuf = ByteBuffer.wrap(new byte[records.length * Long.BYTES]).order(nativeOrder());
            byteBuf.clear();
            for (long record : records)
            {
                byteBuf.putLong(record);
            }
            byteBuf.flip();
            channel.write(byteBuf);
        }
    }
}
