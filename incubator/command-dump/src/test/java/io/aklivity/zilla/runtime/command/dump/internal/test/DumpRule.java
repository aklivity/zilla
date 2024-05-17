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
import static java.nio.file.StandardOpenOption.CREATE_NEW;
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
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.agrona.BitUtil;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.Container;

import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public final class DumpRule implements TestRule
{
    private static final Path ENGINE_PATH = Path.of("target/zilla-itests");
    //private static final LabelManager labelManager = new LabelManager(ENGINE_PATH);
    private static final Path BINDINGS_PATH = ENGINE_PATH.resolve("bindings");
    private static final Path LABELS_PATH = ENGINE_PATH.resolve("labels");
    private static final Path PCAP_PATH = ENGINE_PATH.resolve("actual.pcap");
    private static final Path TXT_PATH = ENGINE_PATH.resolve("actual.txt");
    private static final DumpCommandRunner DUMP = new DumpCommandRunner();
    private static final TsharkRunner TSHARK = new TsharkRunner();

    // TODO: Ati
    //private byte[] bindings;
    private String bindings;
    private String[] labels;
    private Map<String, Integer> labelMap;
    private String configurationRoot;

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        // expected file
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

        // configuration file
        Configuration config = description.getAnnotation(Configuration.class);
        assert config != null;
        assert configurationRoot != null;
        String configName = String.format("%s/%s", configurationRoot, config.value());
        URL configURL = testClass.getClassLoader().getResource(configName);
        assert configURL != null;

        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                // TODO: create my own label file here with the necessary labels
                writeLabels();
                writeBindings(configURL);
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

    public DumpRule addConfigurationRoot(
        String configurationRoot)
    {
        this.configurationRoot = configurationRoot;
        return this;
    }

    // TODO: Ati
    public DumpRule labels(
        String... labels)
    {
        this.labels = labels;
        this.labelMap = new HashMap<>();
        for (int i = 0; i < labels.length; i++)
        {
            labelMap.put(labels[i], i + 1);
        }
        labelMap.put("0", 0);
        return this;
    }
    /*public DumpRule additionalLabels(
        String additionalLabels)
    {
        this.additionalLabels = additionalLabels;
        return this;
    }*/

    // TODO: Ati
    public DumpRule bindings(
        String bindings)
    {
        //this.bindings = BitUtil.fromHex(hex.replaceAll(" ", ""));
        this.bindings = bindings;
        return this;
    }

    // TODO: Ati
    private void writeLabels() throws Exception
    {
        String join = String.join("\n", labels) + "\n";
        byte[] bytes = join.getBytes();
        //String allLabels = labels.replace(" ", "\n") + "\n";
        //byte[] allLabels0 = allLabels.getBytes();
        System.out.println("!!!");
        System.out.println(new java.io.File(".").getCanonicalPath());
        System.out.println(LABELS_PATH);
        Files.createDirectories(ENGINE_PATH);
        Files.write(LABELS_PATH, bytes, CREATE, TRUNCATE_EXISTING);
    }
    /*private void addLabels()
    {
        Arrays.stream(additionalLabels.split("\\s+")).forEach(labels::supplyLabelId);
    }*/

    // TODO: Ati
    private void writeBindings(
        URL configURL) throws Exception
    {
        //String config = Files.readString(Path.of(configURL.toURI()));
        //assert config != null;
        System.out.println("!!!");
        System.out.println(new java.io.File(".").getCanonicalPath());
        //Files.createDirectories(ENGINE_PATH);
        String[] elements = bindings.split("\\s+");
        StringBuilder sb = new StringBuilder();
        long[] longs = new long[elements.length];
        for (int i = 0; i < elements.length; i++)
        {
            String[] parts = elements[i].split("\\.");
            //int namespaceId = "0".equals(parts[0]) ? 0 : labelManager.supplyLabelId(parts[0]);
            //int localId = "0".equals(parts[1]) ? 0 : labelManager.supplyLabelId(parts[1]);
            int namespaceId = labelMap.get(parts[0]);
            int localId = labelMap.get(parts[1]);
            longs[i] = NamespacedId.id(namespaceId, localId);
        }
        //Files.write(BINDINGS_PATH, b, CREATE, TRUNCATE_EXISTING);
        try (ByteChannel channel = Files.newByteChannel(BINDINGS_PATH, CREATE, WRITE))
        {
            ByteBuffer byteBuf = ByteBuffer.wrap(new byte[longs.length * Long.BYTES]).order(nativeOrder());
            byteBuf.clear();
            for (int i = 0; i < longs.length; i++)
            {
                byteBuf.putLong(longs[i]);
            }
            byteBuf.flip();
            channel.write(byteBuf);
        }
    }
}
