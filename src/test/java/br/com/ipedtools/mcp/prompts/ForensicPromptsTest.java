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
        assertTrue(content instanceof TextContent, "Conteúdo deve ser TextContent");
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
        assertTrue(text.contains("get_device_and_owner_info"), "Deve orientar consulta do proprietário");
        assertTrue(text.contains("get_property_dictionary"), "Deve orientar consulta ao dicionário");
        assertTrue(text.contains("search_documents"), "Deve orientar busca estruturada");
        assertTrue(text.contains("Escape de Caracteres Lucene"), "Deve conter regras de escape Lucene");
        assertTrue(text.contains("get_document_metadata"), "Deve orientar inspeção progressiva de metadados");
        assertTrue(text.contains("get_document_text"), "Deve orientar paginação de texto com offset e max_chars");
        assertTrue(text.contains("add_to_bookmark"), "Deve orientar bookmark de evidências");
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
}
