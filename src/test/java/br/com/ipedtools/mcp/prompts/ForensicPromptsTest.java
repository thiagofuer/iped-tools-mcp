package br.com.ipedtools.mcp.prompts;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForensicPromptsTest {

    private ForensicPrompts prompts;

    @BeforeEach
    void setUp() {
        prompts = new ForensicPrompts();
    }

    @Test
    void testPromptAnnotationsAndMetadata() throws NoSuchMethodException {
        // Verify class annotation
        assertTrue(ForensicPrompts.class.isAnnotationPresent(ApplicationScoped.class),
                "ForensicPrompts deve possuir @ApplicationScoped");

        // Verify method annotation
        Method method = ForensicPrompts.class.getMethod("startCase", String.class, String.class);
        Prompt promptAnnotation = method.getAnnotation(Prompt.class);
        assertNotNull(promptAnnotation, "startCase deve possuir @Prompt");
        assertEquals("start_case", promptAnnotation.name());
        assertFalse(promptAnnotation.title().isBlank(), "Título do prompt não deve estar vazio");
        assertFalse(promptAnnotation.description().isBlank(), "Descrição do prompt não deve estar vazia");

        // Verify parameter annotations
        Parameter[] params = method.getParameters();
        assertEquals(2, params.length);

        PromptArg targetArg = params[0].getAnnotation(PromptArg.class);
        assertNotNull(targetArg, "Primeiro argumento deve possuir @PromptArg");
        assertEquals("investigation_target", targetArg.name());
        assertFalse(targetArg.required(), "investigation_target deve ser opcional");

        PromptArg pathArg = params[1].getAnnotation(PromptArg.class);
        assertNotNull(pathArg, "Segundo argumento deve possuir @PromptArg");
        assertEquals("case_path", pathArg.name());
        assertFalse(pathArg.required(), "case_path deve ser opcional");
    }

    @Test
    void testStartCaseDefaultPromptContent() {
        PromptResponse response = prompts.startCase(null, null);
        assertNotNull(response);
        assertEquals(1, response.messages().size());

        PromptMessage message = response.firstMessage();
        assertEquals(Role.USER, message.role());

        Content content = message.content();
        assertInstanceOf(TextContent.class, content, "Conteúdo deve ser TextContent");
        String text = ((TextContent) content).text();

        // 1. Persona & standards
        assertTrue(text.contains("Perito em Computação Forense"), "Deve definir persona pericial");
        assertTrue(text.contains("ISO/IEC 27037"), "Deve citar norma de cadeia de custódia");

        // 2. Strict Negative Constraints (Host OS / Filesystem prohibition)
        assertTrue(text.contains("PROIBIÇÃO DE ACESSO DIRETO AO SISTEMA DE ARQUIVOS"), "Deve conter cabeçalho de restrição crítica");
        assertTrue(text.contains("NUNCA tente executar comandos no sistema operacional do host"), "Deve proibir expressamente comandos no SO");
        assertTrue(text.contains("dir"), "Deve citar exemplo dir");
        assertTrue(text.contains("ls"), "Deve citar exemplo ls");
        assertTrue(text.contains("PowerShell"), "Deve citar exemplo PowerShell");
        assertTrue(text.contains("iped.db"), "Deve proibir leitura direta de bancos e índices");
        assertTrue(text.contains("EXCLUSIVAMENTE"), "Deve exigir uso exclusivo das ferramentas MCP");

        // 3. Phased investigation methodology
        assertTrue(text.contains("get_server_status"), "Deve orientar início com get_server_status");
        assertTrue(text.contains("get_case_summary"), "Deve indicar get_case_summary");
        assertTrue(text.contains("list_sources"), "Deve indicar list_sources");
        assertTrue(text.contains("get_device_and_accounts"), "Deve orientar o levantamento dos identificadores");
        assertTrue(text.contains("compare as fontes"), "Deve orientar a comparação das fontes");
        assertTrue(text.contains("cite os `item_id`"), "Deve orientar a citação dos itens de origem");
        assertTrue(text.contains("\"proprietário\" ou \"dono\""), "Deve manter a proibição de busca por dono em texto livre");
        assertFalse(text.contains("get_device_and_owner_info"), "Prompt não deve citar a ferramenta removida");
        assertTrue(text.contains("list_properties"), "Deve orientar a descoberta pelo catálogo de propriedades");
        assertTrue(text.contains("describe_property"), "Deve orientar o detalhamento de propriedades");
        assertTrue(text.contains("grupo `AI`"), "Deve orientar a busca de resultados de IA pelo grupo AI");
        for (String removed : java.util.List.of("list_ai_filters", "query_ai_detections", "get_property_dictionary",
                "list_available_properties")) {
            assertFalse(text.contains(removed), "Prompt não deve citar ferramenta removida: " + removed);
        }
        assertTrue(text.contains("search_documents"), "Deve orientar busca estruturada");
        assertTrue(text.contains("next_offset"), "Deve orientar a paginação da busca");
        assertTrue(text.contains("total_found"), "Deve orientar a relatar o total exato");
        assertTrue(text.contains("Escape de Caracteres Lucene"), "Deve conter regras de escape Lucene");
        assertTrue(text.contains("get_document_metadata"), "Deve orientar inspeção progressiva de metadados");
        assertTrue(text.contains("get_document_text"), "Deve orientar paginação de texto com offset e max_chars");
        assertTrue(text.contains("add_to_bookmark"), "Deve orientar bookmark de evidências");

        // Contatos e redes
        assertTrue(text.contains("get_top_contacts"), "Deve citar get_top_contacts");
        assertTrue(text.contains("get_communications_graph"), "Deve citar get_communications_graph");
        assertTrue(text.contains("get_contact_relationship"), "Deve citar get_contact_relationship");
        assertTrue(text.contains("`unknown`"), "Deve explicar o significado da direção unknown");
    }

    @Test
    void testStartCaseWithInvestigationTarget() {
        String target = "desvio de recursos em contratos de TI";
        PromptResponse response = prompts.startCase(target, "");
        assertNotNull(response);

        String text = ((TextContent) response.firstMessage().content()).text();
        assertTrue(text.contains("FOCO DA INVESTIGAÇÃO ESPECIFICADO"), "Deve conter seção de foco da investigação");
        assertTrue(text.contains(target), "Deve incluir o texto do alvo da investigação");
    }

    @Test
    void testStartCaseWithCasePath() {
        String casePath = "D:\\Evidencias\\Operacao_Alpha";
        PromptResponse response = prompts.startCase("", casePath);
        assertNotNull(response);

        String text = ((TextContent) response.firstMessage().content()).text();
        assertTrue(text.contains("DIRETÓRIO DO CASO FORNECIDO"), "Deve conter seção do diretório do caso");
        assertTrue(text.contains("open_case"), "Deve instruir o uso do open_case se o caso não estiver carregado");
        assertTrue(text.contains("Operacao_Alpha"), "Deve incluir o caminho do caso");
    }

    @Test
    void testStartCaseWithBothParameters() {
        String target = "mensagens de extorsão";
        String casePath = "C:\\IPED_Cases\\Case007";
        PromptResponse response = prompts.startCase(target, casePath);
        assertNotNull(response);

        String text = ((TextContent) response.firstMessage().content()).text();
        assertTrue(text.contains(target));
        assertTrue(text.contains(casePath));
    }

    @Test
    void testStartCaseLanguageMirroringDirective() {
        PromptResponse response = prompts.startCase(null, null);
        assertNotNull(response);

        String text = ((TextContent) response.firstMessage().content()).text();
        assertTrue(text.contains("LANGUAGE MIRRORING"), "Deve conter seção de espelhamento de idioma");
        assertTrue(text.contains("Acompanhe ativamente o idioma do examinador"), "Deve instruir a espelhar o idioma do usuário");
        assertTrue(text.contains("Português do Brasil"), "Deve referenciar Português do Brasil para usuários em português");
        assertTrue(text.contains("Inglês"), "Deve permitir resposta em inglês quando o examinador falar em inglês");
    }

    private String defaultPrompt() {
        return prompts.buildStartCasePrompt(null, null);
    }

    /** Text of the sensitive content check (section 2.1 of the prompt). */
    static String sensitiveCheckSection(String prompt) {
        int start = prompt.indexOf("#### 2.1");
        int end = prompt.indexOf("#### 2.2");
        assertTrue(start >= 0 && end > start, "o prompt deve ter a seção 2.1 antes da 2.2");
        return prompt.substring(start, end);
    }

    /** Property names cited in the check: fields of the backticked queries and describe_property names. */
    static java.util.Set<String> checkedPropertyNames(String section) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher q = java.util.regex.Pattern.compile("`((?:[A-Za-z_]|\\\\:)+):[^`]*`").matcher(section);
        while (q.find()) {
            names.add(q.group(1).replace("\\:", ":"));
        }
        java.util.regex.Matcher d = java.util.regex.Pattern.compile("describe_property\\(name=\"([^\"]+)\"\\)").matcher(section);
        while (d.find()) {
            names.add(d.group(1));
        }
        return names;
    }

    /** Names that have no curated semantics (so are not known IPED properties). */
    static java.util.List<String> unknownNames(java.util.Set<String> names) {
        br.com.ipedtools.mcp.service.PropertySemantics semantics = br.com.ipedtools.mcp.service.PropertySemantics.get();
        return names.stream().filter(n -> semantics.find(n) == null).toList();
    }

    @Test
    void testConductSectionComesBeforeSourcesStep() {
        String text = defaultPrompt();
        int conduct = text.indexOf("### 2. Regras de Conduta");
        int sources = text.indexOf("### 3. Reconhecimento de Fontes");
        assertTrue(conduct > 0 && sources > conduct, "a conduta deve vir antes do reconhecimento das fontes");
        assertTrue(text.indexOf("get_case_summary") < conduct, "a checagem vem depois da verificação do caso");
        assertTrue(text.contains("Na dúvida, aja como modelo em nuvem"), "deve tratar a dúvida como nuvem");
        assertTrue(text.contains("concordância expressa do usuário"), "deve exigir consentimento na nuvem");
        assertTrue(text.contains("só pode chamar ferramentas que não trazem conteúdo de itens"),
                "deve limitar as chamadas antes do aceite");
    }

    @Test
    void testSensitiveContentCheck() {
        String section = sensitiveCheckSection(defaultPrompt());
        for (String expected : java.util.List.of("childPornHashHits:[1 TO *]", "ai\\:csamDetector\\:label:csam",
                "describe_property(name=\"hashDb:status\")", "nudityScore:[650 TO *]", "nsfw_nudity_score:[70 TO *]",
                "limit=1", "total_found", "list_properties(group=\"AI\")", "list_properties(group=\"HashDB\")")) {
            assertTrue(section.contains(expected), "checagem sem: " + expected);
        }
        assertTrue(section.contains("\"não verificado\""), "propriedade ausente deve ser 'não verificado'");
        assertTrue(section.contains("Nunca relate isso como zero"));
        assertTrue(section.contains("não abra itens, não peça miniaturas"), "a checagem não pode abrir itens");
    }

    @Test
    void testCheckedPropertiesExistInCuratedSemantics() {
        java.util.Set<String> names = checkedPropertyNames(sensitiveCheckSection(defaultPrompt()));
        assertEquals(java.util.Set.of("childPornHashHits", "ai:csamDetector:label", "hashDb:status", "nudityScore",
                "nsfw_nudity_score"), names);
        assertEquals(java.util.List.of(), unknownNames(names), "propriedades da checagem sem semântica curada");
    }

    @Test
    void testUnknownPropertyInCheckIsDetected() {
        String fake = "#### 2.1\n- Nudez: `hasNudity:true`\n- `nudityScore:[650 TO *]`\n#### 2.2";
        assertEquals(java.util.List.of("hasNudity"), unknownNames(checkedPropertyNames(sensitiveCheckSection(fake))));
    }

    @Test
    void testAlertAndSessionAcceptance() {
        String text = defaultPrompt();
        assertTrue(text.contains("termos de uso do provedor"), "deve citar os termos de uso do provedor");
        assertTrue(text.contains("Pare e só prossiga"), "deve pausar até a concordância");
        assertTrue(text.contains("O aceite vale para esta conversa"), "aceite por sessão");
        assertTrue(text.contains("Uma nova sessão (nova conversa) sempre exige um novo aceite"));
        assertTrue(text.contains("Ao trocar de caso com `open_case`, faça uma nova checagem"));
        assertTrue(text.contains("Prosseguir é decisão do usuário"), "modelo local: decisão do usuário");
    }

    /** Server instructions as Quarkus reads them from application.properties (UTF-8). */
    static String serverInstructions() throws java.io.IOException {
        java.util.Properties props = new java.util.Properties();
        try (java.io.Reader r = new java.io.InputStreamReader(
                ForensicPromptsTest.class.getResourceAsStream("/application.properties"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            props.load(r);
        }
        String text = props.getProperty("quarkus.mcp.server.server-info.instructions");
        assertNotNull(text, "application.properties deve definir quarkus.mcp.server.server-info.instructions");
        return text;
    }

    @Test
    void testServerInstructionsStateTheFourRules() throws Exception {
        String instructions = serverInstructions();
        assertTrue(instructions.contains("start_case"), "deve recomendar o prompt start_case");
        // 1. sensitive content check, with the same signals as the prompt
        assertTrue(instructions.contains("\"não verificado\", nunca zero"));
        java.util.Set<String> promptSignals = checkedPropertyNames(sensitiveCheckSection(defaultPrompt()));
        for (String signal : promptSignals) {
            assertTrue(instructions.contains(signal), "instructions sem o sinal da checagem: " + signal);
        }
        // 2. cloud consent
        assertTrue(instructions.contains("ou na dúvida"));
        assertTrue(instructions.contains("concordância expressa do usuário"));
        // 3. per-session acceptance
        assertTrue(instructions.contains("Aceite por sessão"));
        // 4. writes
        assertTrue(instructions.contains("add_to_bookmark") && instructions.contains("set_item_checked"));
        assertTrue(instructions.contains("confirmação expressa do usuário"));
        assertTrue(instructions.contains("sem indicar autoria de IA"));
        assertTrue(instructions.contains("\n2. Modelo em nuvem"), "as regras devem vir em linhas separadas");
    }

    @Test
    void testWritesRequireConfirmation() {
        String text = defaultPrompt();
        assertTrue(text.contains("Nunca escreva no caso por iniciativa própria"));
        assertTrue(text.contains("aguarde a confirmação expressa"));
        assertTrue(text.contains("não use nem acrescente termos que indiquem autoria de IA"));
        assertFalse(text.contains("adicione-os aos marcadores oficiais usando `add_to_bookmark`"),
                "não pode restar a orientação de marcar sem perguntar");
    }
}
