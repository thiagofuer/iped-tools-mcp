package br.com.ipedtools.mcp.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import br.com.ipedtools.mcp.McpApplication;
import br.com.ipedtools.mcp.VersionInfo;
import br.com.ipedtools.mcp.gui.StdioSelfTest.Status;
import br.com.ipedtools.mcp.gui.StdioSelfTest.StepResult;
import br.com.ipedtools.mcp.test.TestCaseResolver;

/**
 * Local STDIO test of the configurator. The tests against the server run the runner JAR of target/, skipped when it
 * does not exist or is older than the compiled classes (run {@code mvn package} and then {@code mvn test}).
 */
class StdioSelfTestTest {

    private static final StdioSelfTest.Listener SILENT = new StdioSelfTest.Listener() {
        @Override
        public void stepStarted(String name) {
        }

        @Override
        public void stepFinished(StepResult result) {
        }
    };

    /** The runner JAR of the current version, when it is newer than the compiled classes; otherwise null. */
    private static File currentRunnerJar() throws Exception {
        File jar = new File("target", "iped-tools-mcp-" + VersionInfo.getVersion() + "-runner.jar");
        File classes = new File(McpApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File compiled = new File(classes, "br/com/ipedtools/mcp/gui/StdioSelfTest.class");
        if (!jar.isFile() || (compiled.isFile() && compiled.lastModified() > jar.lastModified())) {
            return null;
        }
        return jar;
    }

    private static List<String> jarCommand() throws Exception {
        File jar = currentRunnerJar();
        assumeTrue(jar != null, "runner JAR atual ausente em target/: rode mvn package antes");
        return StdioSelfTest.command(jar.getAbsolutePath(), StdioSelfTest.currentJava());
    }

    private static StepResult step(List<StepResult> results, String name) {
        return results.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void testCommandForExecutableAndJar() {
        assertEquals(List.of("C:\\app\\IPED-Tools-MCP.exe", "--stdio"),
                StdioSelfTest.command("C:\\app\\IPED-Tools-MCP.exe", "java"));
        List<String> jar = StdioSelfTest.command("C:\\app\\runner.jar", "C:\\jdk\\bin\\java.exe");
        assertEquals("C:\\jdk\\bin\\java.exe", jar.get(0));
        assertEquals(List.of("-jar", "C:\\app\\runner.jar", "--stdio"), jar.subList(jar.size() - 3, jar.size()));
        assertTrue(jar.containsAll(StdioSelfTest.JAR_JVM_ARGS));
        assertNull(StdioSelfTest.command("C:\\projeto\\target\\classes", "java"), "sem pacote, não há teste");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testTextOnStdoutFailsTheFirstStep() {
        StdioSelfTest test = new StdioSelfTest(List.of("cmd", "/c", "echo texto qualquer"), null, "1.0",
                StdioSelfTest.Timeouts.DEFAULT, SILENT);
        List<StepResult> results = test.run();
        assertEquals(Status.PASSED, step(results, StdioSelfTest.STEP_START).status());
        StepResult init = step(results, StdioSelfTest.STEP_INITIALIZE);
        assertEquals(Status.FAILED, init.status());
        assertTrue(init.detail().contains("JSON-RPC") && init.detail().contains("texto qualquer"), init.detail());
        assertEquals(Status.NOT_RUN, step(results, StdioSelfTest.STEP_TOOLS).status());
        assertFalse(test.isAlive());
    }

    @Test
    void testHandshakeWithoutCase() throws Exception {
        List<String> events = new ArrayList<>();
        StdioSelfTest test = new StdioSelfTest(jarCommand(), null, VersionInfo.getVersion(),
                StdioSelfTest.Timeouts.DEFAULT, new StdioSelfTest.Listener() {
                    @Override
                    public void stepStarted(String name) {
                        events.add("start:" + name);
                    }

                    @Override
                    public void stepFinished(StepResult result) {
                        events.add("end:" + result.name());
                    }
                });
        List<StepResult> results = test.run();
        assertEquals(4, results.size(), results.toString());
        assertTrue(results.stream().allMatch(r -> r.status() == Status.PASSED), results.toString());
        Matcher m = Pattern.compile("(\\d+) ferramentas").matcher(step(results, StdioSelfTest.STEP_TOOLS).detail());
        assertTrue(m.find() && Integer.parseInt(m.group(1)) > 10);
        assertEquals("start:" + StdioSelfTest.STEP_START, events.get(0));
        assertTrue(test.logFile() != null, "get_server_status informa o arquivo de log");
        assertFalse(test.isAlive(), "o processo do teste não pode ficar vivo");
    }

    @Test
    void testVersionMismatchFailsInitialize() throws Exception {
        StdioSelfTest test = new StdioSelfTest(jarCommand(), null, "0.0.0-outra", StdioSelfTest.Timeouts.DEFAULT,
                SILENT);
        StepResult init = step(test.run(), StdioSelfTest.STEP_INITIALIZE);
        assertEquals(Status.FAILED, init.status());
        assertTrue(init.detail().contains("0.0.0-outra"), init.detail());
        assertFalse(test.isAlive());
    }

    @Test
    void testInvalidCaseFolderFailsOpenCase(@TempDir Path notACase) throws Exception {
        StdioSelfTest test = new StdioSelfTest(jarCommand(), notACase.toFile(), VersionInfo.getVersion(),
                StdioSelfTest.Timeouts.DEFAULT, SILENT);
        List<StepResult> results = test.run();
        StepResult open = step(results, StdioSelfTest.STEP_OPEN_CASE);
        assertEquals(Status.FAILED, open.status());
        assertTrue(open.detail().contains(notACase.getFileName().toString()), open.detail());
        assertEquals(Status.NOT_RUN, step(results, StdioSelfTest.STEP_SUMMARY).status());
        assertFalse(test.isAlive());
    }

    @Test
    void testWithTheLocalTestCase() throws Exception {
        assumeTrue(TestCaseResolver.isCaseAvailable(), "caso de teste local não configurado");
        StdioSelfTest test = new StdioSelfTest(jarCommand(), TestCaseResolver.getPrimaryCaseDir(),
                VersionInfo.getVersion(), StdioSelfTest.Timeouts.DEFAULT, SILENT);
        List<StepResult> results = test.run();
        assertTrue(results.stream().allMatch(r -> r.status() == Status.PASSED), results.toString());
        Matcher m = Pattern.compile("^([\\d.,]+) itens indexados")
                .matcher(step(results, StdioSelfTest.STEP_SUMMARY).detail());
        assertTrue(m.find() && Long.parseLong(m.group(1).replaceAll("[.,]", "")) > 0, results.toString());
        assertFalse(test.isAlive());
    }

    @Test
    void testCancelEndsTheProcess() throws Exception {
        List<String> command = jarCommand();
        StdioSelfTest[] holder = new StdioSelfTest[1];
        holder[0] = new StdioSelfTest(command, null, VersionInfo.getVersion(), StdioSelfTest.Timeouts.DEFAULT,
                new StdioSelfTest.Listener() {
                    @Override
                    public void stepStarted(String name) {
                        if (name.equals(StdioSelfTest.STEP_TOOLS)) {
                            holder[0].cancel();
                        }
                    }

                    @Override
                    public void stepFinished(StepResult result) {
                    }
                });
        List<StepResult> results = holder[0].run();
        assertEquals(Status.FAILED, step(results, StdioSelfTest.STEP_TOOLS).status());
        assertTrue(step(results, StdioSelfTest.STEP_TOOLS).detail().contains("cancelado"));
        assertEquals(Status.NOT_RUN, step(results, StdioSelfTest.STEP_STATUS).status());
        assertFalse(holder[0].isAlive());
    }
}
