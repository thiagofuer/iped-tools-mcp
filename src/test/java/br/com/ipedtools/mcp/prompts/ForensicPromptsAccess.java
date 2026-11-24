package br.com.ipedtools.mcp.prompts;

/** Lets tests of other packages create the prompt bean with another profile folder. */
public final class ForensicPromptsAccess {

    private ForensicPromptsAccess() {
    }

    public static ForensicPrompts with(StartPromptStore store) {
        return new ForensicPrompts(store);
    }
}
