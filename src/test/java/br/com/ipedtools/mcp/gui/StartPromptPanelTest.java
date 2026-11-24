package br.com.ipedtools.mcp.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.ipedtools.mcp.prompts.ForensicPromptsAccess;
import br.com.ipedtools.mcp.prompts.StartPromptStore;
import io.quarkiverse.mcp.server.TextContent;

/** "Copiar prompt" of the configurator copies exactly what the server returns for start_case. */
class StartPromptPanelTest {

    @TempDir
    Path profile;

    private String serverText(StartPromptStore store, String target, String casePath) {
        return ((TextContent) ForensicPromptsAccess.with(store).startCase(target, casePath).firstMessage().content())
                .text();
    }

    @Test
    void testCopiedTextEqualsTheServerPrompt() throws Exception {
        StartPromptStore store = new StartPromptStore(profile.toFile());
        List<String> log = new ArrayList<>();
        StartPromptPanel panel = new StartPromptPanel(store, log::add);

        panel.setArguments("", "");
        assertEquals(serverText(store, "", ""), panel.promptText());

        panel.setArguments("fraude em licitação", "C:\\casos\\exemplo");
        assertEquals(serverText(store, "fraude em licitação", "C:\\casos\\exemplo"), panel.promptText());

        // a user copy is used by both
        assertTrue(store.createUserCopy());
        Files.writeString(store.userFile().toPath(), "# Metodologia do laboratório\n", StandardCharsets.UTF_8);
        String copied = panel.promptText();
        assertEquals(serverText(store, "fraude em licitação", "C:\\casos\\exemplo"), copied);
        assertTrue(copied.startsWith("# Metodologia do laboratório") && copied.endsWith(StartPromptStore.conduct()));
    }
}
