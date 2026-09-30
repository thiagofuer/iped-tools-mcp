package br.com.ipedtools.mcp;

import java.awt.EventQueue;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.ipedtools.mcp.gui.MainWindow;
import br.com.ipedtools.mcp.service.IpedCoreService;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Main application entrypoint for IPED Tools MCP.
 * Implements dual-mode execution:
 * - GUI Mode: Launches the Swing visual configurator when executed without '--stdio'.
 * - STDIO Mode: Launches the headless Quarkus MCP Server when called with '--stdio --case <path>'.
 */
@QuarkusMain
public class McpApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpApplication.class);

    public static void main(String... args) {
        boolean stdioMode = false;
        String casePath = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--stdio".equalsIgnoreCase(arg)) {
                stdioMode = true;
            } else if (arg.startsWith("--case=")) {
                casePath = arg.substring("--case=".length());
            } else if ("--case".equalsIgnoreCase(arg) && i + 1 < args.length) {
                casePath = args[++i];
            } else if ("--version".equalsIgnoreCase(arg) || "-v".equalsIgnoreCase(arg)) {
                printVersion();
                return;
            } else if ("--help".equalsIgnoreCase(arg) || "-h".equalsIgnoreCase(arg)) {
                printHelp();
                return;
            }
        }

        if (stdioMode) {
            // Mode A: STDIO Headless Server for LLM clients
            LOGGER.info("Iniciando IPED Tools MCP em modo STDIO...");
            if (casePath != null && !casePath.isBlank()) {
                try {
                    File caseDir = new File(casePath);
                    LOGGER.info("Pré-carregando caso IPED: {}", caseDir.getAbsolutePath());
                    IpedCoreService.getInstance().openCase(caseDir);
                } catch (Exception e) {
                    LOGGER.error("Falha ao abrir caso no startup STDIO: {}", e.getMessage(), e);
                    System.err.println("ERRO: Não foi possível carregar o caso especificado: " + e.getMessage());
                }
            } else {
                LOGGER.info("Nenhum caso especificado com '--case'. Verificando caso ativo na GUI...");
                IpedCoreService.getInstance().syncActiveCaseIfNeeded();
                if (IpedCoreService.getInstance().isCaseOpen()) {
                    LOGGER.info("Caso ativo carregado automaticamente da GUI: {}",
                            IpedCoreService.getInstance().getCaseDirectory().getAbsolutePath());
                } else {
                    LOGGER.warn("Nenhum caso ativo encontrado. As ferramentas aguardarão seleção posterior ou comando 'open_case'.");
                }
            }

            Quarkus.run(args);

        } else {
            // Mode B: Interactive Swing GUI for examiners
            LOGGER.info("Iniciando IPED Tools MCP em modo Gráfico (GUI)...");
            EventQueue.invokeLater(() -> {
                try {
                    MainWindow window = new MainWindow();
                    window.setVisible(true);
                } catch (Exception e) {
                    LOGGER.error("Erro fatal ao abrir interface gráfica: {}", e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Resolves the current executing native executable path (or fallback JAR) for configuration generation.
     */
    public static String getAppExecutableOrJarPath() {
        try {
            File codeSource = new File(McpApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // 1. Running inside jpackage app-image: <install-dir>/app/<runner>.jar -> <install-dir>/IPED-Tools-MCP.exe
            if (codeSource.getParentFile() != null && "app".equalsIgnoreCase(codeSource.getParentFile().getName())) {
                File exeInRoot = new File(codeSource.getParentFile().getParentFile(), "IPED-Tools-MCP.exe");
                if (exeInRoot.exists()) {
                    return exeInRoot.getAbsolutePath();
                }
            }
            // 2. Running in same directory as IPED-Tools-MCP.exe
            if (codeSource.getParentFile() != null) {
                File exeSameDir = new File(codeSource.getParentFile(), "IPED-Tools-MCP.exe");
                if (exeSameDir.exists()) {
                    return exeSameDir.getAbsolutePath();
                }
            }
            // 3. Check dist/ folder if developing locally
            File distExe = new File("dist/IPED-Tools-MCP/IPED-Tools-MCP.exe");
            if (distExe.exists()) {
                return distExe.getAbsolutePath();
            }
            return codeSource.getAbsolutePath();
        } catch (Exception e) {
            return "IPED-Tools-MCP.exe";
        }
    }

    private static void printVersion() {
        System.out.println(VersionInfo.getFullVersionString());
        System.out.println("Website: " + VersionInfo.OFFICIAL_WEBSITE);
        System.out.println("Código-fonte: " + VersionInfo.GITHUB_REPO);
        System.out.println("Licença: " + VersionInfo.LICENSE);
    }

    private static void printHelp() {
        System.out.println(VersionInfo.APP_NAME + " — " + VersionInfo.APP_DESCRIPTION);
        System.out.println("Versão: " + VersionInfo.getVersion());
        System.out.println("Website: " + VersionInfo.OFFICIAL_WEBSITE);
        System.out.println();
        System.out.println("Uso:");
        System.out.println("  IPED-Tools-MCP.exe                                (Abre a interface gráfica)");
        System.out.println("  IPED-Tools-MCP.exe --stdio --case <dir>           (Inicia como servidor MCP STDIO)");
        System.out.println();
        System.out.println("Opções:");
        System.out.println("  --stdio              Executa como servidor MCP via STDIO (chamado pela LLM)");
        System.out.println("  --case <diretório>   Caminho para a pasta do caso processado pelo IPED");
        System.out.println("  --gui                Força a abertura da interface gráfica");
        System.out.println("  --version, -v        Exibe a versão e informações de build");
        System.out.println("  --help, -h           Exibe esta mensagem de ajuda");
    }
}
