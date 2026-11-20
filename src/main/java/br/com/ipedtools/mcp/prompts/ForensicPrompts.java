package br.com.ipedtools.mcp.prompts;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ForensicPrompts {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForensicPrompts.class);
    /** The outdated user copy is reported once per process. */
    private static final AtomicBoolean OUTDATED_WARNED = new AtomicBoolean();

    private final StartPromptStore store;

    public ForensicPrompts() {
        this(StartPromptStore.forUser());
    }

    /** With another profile folder, for tests. */
    ForensicPrompts(StartPromptStore store) {
        this.store = store;
    }

    public static final String PROMPT_NAME = "start_case";

    @Prompt(
        name = PROMPT_NAME,
        title = "Iniciar Investigação Forense",
        description = "Diretrizes fundamentais para guiar o LLM na análise pericial de casos IPED, estabelecendo persona pericial, metodologia e proibição estrita de acesso direto ao sistema de arquivos do host."
    )
    public PromptResponse startCase(
        @PromptArg(name = "investigation_target", description = "Alvo ou objetivo temático da investigação (ex: 'fraude em licitação', 'comunicações do suspeito X')", required = false)
        String investigationTarget,
        @PromptArg(name = "case_path", description = "Caminho absoluto opcional da pasta do caso IPED", required = false)
        String casePath
    ) {
        String promptText = buildStartCasePrompt(investigationTarget, casePath);
        return PromptResponse.withMessages(PromptMessage.withUserRole(promptText));
    }

    /**
     * The prompt text: the methodology in use (the user copy when valid, otherwise the default), the sections of the
     * arguments and the fixed conduct rules. The user copy is read on every request.
     */
    public String buildStartCasePrompt(String investigationTarget, String casePath) {
        StartPromptStore.Methodology methodology = store.load();
        switch (methodology.state()) {
            case CUSTOM_INVALID -> LOGGER.warn("Prompt start_case personalizado ignorado ({}): {}. Usando a metodologia padrão.",
                    methodology.userFile().getAbsolutePath(), methodology.detail());
            case CUSTOM_OUTDATED -> {
                if (OUTDATED_WARNED.compareAndSet(false, true)) {
                    LOGGER.warn("Prompt start_case personalizado desatualizado ({}): {}. Compare com o padrão ou restaure-o pelo configurador.",
                            methodology.userFile().getAbsolutePath(), methodology.detail());
                }
            }
            default -> {
            }
        }
        return StartPromptStore.build(methodology.text(), investigationTarget, casePath);
    }
}
