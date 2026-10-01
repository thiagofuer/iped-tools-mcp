# Tasks

## 1. Forensic MCP Prompt Implementation

- [x] 1.1 Create `br.com.ipedtools.mcp.prompts.ForensicPrompts` with `@ApplicationScoped` and implement the `@Prompt(name = "start_case", ...)` method returning `PromptResponse` with structured forensic instructions and strict negative constraints prohibiting direct OS shell/filesystem access. Verify compilation with `mvn test-compile`.
- [x] 1.2 Implement support for `@PromptArg` parameters `investigation_target` and `case_path` within `ForensicPrompts.java` to inject dynamic context into the generated prompt message. Verify compilation with `mvn test-compile`.
- [x] 1.3 Create unit test `br.com.ipedtools.mcp.prompts.ForensicPromptsTest` verifying that default and parameterized calls to `start_case` generate expected prompt text, contain strict negative constraints against OS commands, and return valid `PromptResponse` structures. Verify tests pass with `mvn test -Dtest=ForensicPromptsTest`.

## 2. GUI Configurator Instruction Updates

- [x] 2.1 Update LM Studio configuration instruction templates in `br.com.ipedtools.mcp.gui.MainWindow` to highlight the `/start_case` prompt for prompt-capable MCP clients. Verify updated text via compilation and code review.
- [x] 2.2 Verify that GUI compilation and existing test suites pass with `mvn test-compile`.

## 3. End-to-End Validation

- [x] 3.1 Run full project test suite (`mvn test`) to ensure all existing MCP tools, GUI components, and the new prompt work without regressions.
- [x] 3.2 Validate the OpenSpec change artifacts using `openspec validate add-start-case-mcp-prompt`.
