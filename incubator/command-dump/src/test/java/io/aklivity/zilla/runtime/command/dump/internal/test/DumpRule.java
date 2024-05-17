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

import static org.agrona.LangUtil.rethrowUnchecked;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.agrona.BitUtil;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.Container;

import io.aklivity.zilla.runtime.engine.test.annotation.Configuration;

public final class DumpRule implements TestRule
{
    private static final Path ENGINE_PATH = Path.of("target/zilla-itests");
    private static final Path BINDINGS_PATH = ENGINE_PATH.resolve("bindings");
    private static final Path LABELS_PATH = ENGINE_PATH.resolve("labels");
    private static final Path PCAP_PATH = ENGINE_PATH.resolve("actual.pcap");
    private static final Path TXT_PATH = ENGINE_PATH.resolve("actual.txt");
    private static final DumpCommandRunner DUMP = new DumpCommandRunner();
    private static final TsharkRunner TSHARK = new TsharkRunner();

    // TODO: Ati
    private byte[] bindings;
    private String[] labels;
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
                base.evaluate();
                writeLabels();
                writeBindings(configURL);
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
    public DumpRule addLabels(
        String... labels)
    {
        this.labels = labels;
        return this;
    }

    // TODO: Ati
    public DumpRule bindings(
        String hex)
    {
        this.bindings = BitUtil.fromHex(hex.replaceAll(" ", ""));
        return this;
    }

    // TODO: Ati
    private void writeLabels() throws Exception
    {
        String allLabels = String.join("\n", labels) + "\n";
        byte[] allLabels0 = allLabels.getBytes();
        Files.write(LABELS_PATH, allLabels0, StandardOpenOption.APPEND);
    }

    // TODO: Ati
    private void writeBindings(
        URL configURL) throws Exception
    {
        String config = Files.readString(Path.of(configURL.toURI()));
        assert config != null;
        Files.createDirectories(ENGINE_PATH);
        Files.write(BINDINGS_PATH, bindings, StandardOpenOption.CREATE);
    }
}
