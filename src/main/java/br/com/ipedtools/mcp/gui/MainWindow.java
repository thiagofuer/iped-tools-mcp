package br.com.ipedtools.mcp.gui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import br.com.ipedtools.mcp.McpApplication;
import br.com.ipedtools.mcp.VersionInfo;
import br.com.ipedtools.mcp.prompts.StartPromptStore;

/**
 * Modern Swing configuration and diagnostic GUI for IPED Tools MCP.
 * Lets forensic examiners test the server as their AI client runs it (STDIO mode) and copy the client
 * configuration in a single click. The window never opens a case in its own process: each conversation opens its
 * case with open_case.
 */
public class MainWindow extends JFrame {

    private final JTextField testCaseField;
    private final JButton testBrowseButton;
    private final JButton runTestButton;
    private final JButton cancelTestButton;
    private final JTextArea testResultArea;
    private volatile StdioSelfTest currentTest;

    private final StartPromptPanel startPromptPanel;

    private final JComboBox<String> clientSelectCombo;
    private final JTextArea configJsonArea;
    private final JButton copyConfigButton;

    private final JTextArea logArea;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public MainWindow() {
        super("IPED Tools MCP v" + VersionInfo.getVersion() + " — Assistente de Inteligência Artificial Forense");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 900);
        setMinimumSize(new Dimension(780, 760));
        setLocationRelativeTo(null);

        initAppIcons();

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Header Panel
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Center Content Panel (Local STDIO test + Client Config)
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Local test of the server as the AI client runs it
        JPanel testPanel = new JPanel(new BorderLayout(6, 6));
        testPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 1. Testar o MCP (modo STDIO) ",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("SansSerif", Font.BOLD, 12)
        ));

        JLabel testHintLabel = new JLabel("<html>Executa o servidor do mesmo jeito que o cliente de IA vai executá-lo. "
                + "A pasta do caso é opcional: ela é aberta só no processo do teste e não muda o caso de nenhuma "
                + "conversa.</html>");
        testHintLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        testCaseField = new JTextField();
        testCaseField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        testCaseField.setToolTipText("Pasta de um caso processado pelo IPED (opcional)");
        testBrowseButton = new JButton("Procurar Pasta...");
        runTestButton = new JButton("▶ Executar teste");
        runTestButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        cancelTestButton = new JButton("Cancelar");
        cancelTestButton.setEnabled(false);

        JPanel testButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        testButtons.add(testBrowseButton);
        testButtons.add(runTestButton);
        testButtons.add(cancelTestButton);

        JPanel testCaseRow = new JPanel(new BorderLayout(8, 0));
        testCaseRow.add(new JLabel("Pasta do caso (opcional): "), BorderLayout.WEST);
        testCaseRow.add(testCaseField, BorderLayout.CENTER);
        testCaseRow.add(testButtons, BorderLayout.EAST);

        JPanel testTop = new JPanel(new BorderLayout(0, 4));
        testTop.add(testHintLabel, BorderLayout.NORTH);
        testTop.add(testCaseRow, BorderLayout.SOUTH);

        testResultArea = new JTextArea(5, 40);
        testResultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        testResultArea.setEditable(false);
        testResultArea.setBackground(new Color(248, 249, 250));
        testResultArea.setText("Nenhum teste executado.");

        testPanel.add(testTop, BorderLayout.NORTH);
        testPanel.add(new JScrollPane(testResultArea), BorderLayout.CENTER);
        contentPanel.add(testPanel);
        contentPanel.add(Box.createVerticalStrut(8));

        // start_case prompt: copy for clients without MCP prompts, edit or restore the methodology
        startPromptPanel = new StartPromptPanel(StartPromptStore.forUser(), this::log);
        contentPanel.add(startPromptPanel);
        contentPanel.add(Box.createVerticalStrut(8));

        // Config generation section
        JPanel configPanel = new JPanel(new BorderLayout(6, 6));
        configPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 3. Configuração do Cliente de IA ",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("SansSerif", Font.BOLD, 12)
        ));

        JPanel configTopPanel = new JPanel(new BorderLayout(5, 5));
        JLabel clientLabel = new JLabel("Selecione seu aplicativo de IA: ");
        clientSelectCombo = new JComboBox<>(new String[]{
                "LM Studio (Formulário Visual - Copiar Campos)",
                "LM Studio (Arquivo ng-mcp.json)",
                "Claude Desktop / Cursor / Antigravity (JSON)"
        });

        configTopPanel.add(clientLabel, BorderLayout.WEST);
        configTopPanel.add(clientSelectCombo, BorderLayout.CENTER);

        JLabel configHintLabel = new JLabel("<html>💡 Configure o aplicativo de IA apenas 1 vez (--stdio). Na conversa, "
                + "peça ao modelo para abrir a pasta do caso (open_case) ou use o prompt start_case.</html>");
        configHintLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        configHintLabel.setForeground(new Color(41, 128, 185));
        configHintLabel.setBorder(new EmptyBorder(2, 4, 2, 4));

        JPanel topContainer = new JPanel(new BorderLayout(0, 4));
        topContainer.add(configTopPanel, BorderLayout.NORTH);
        topContainer.add(configHintLabel, BorderLayout.SOUTH);

        configJsonArea = new JTextArea(7, 40);
        configJsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        configJsonArea.setEditable(false);
        configJsonArea.setBackground(new Color(248, 249, 250));
        JScrollPane configScroll = new JScrollPane(configJsonArea);

        copyConfigButton = new JButton("📋 Copiar Configuração para a Área de Transferência");
        copyConfigButton.setFont(new Font("SansSerif", Font.BOLD, 12));
        copyConfigButton.setPreferredSize(new Dimension(200, 36));

        configPanel.add(topContainer, BorderLayout.NORTH);
        configPanel.add(configScroll, BorderLayout.CENTER);
        configPanel.add(copyConfigButton, BorderLayout.SOUTH);

        contentPanel.add(configPanel);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // 3. Diagnostic Log Console (Bottom)
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " Console de Diagnóstico ",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("SansSerif", Font.PLAIN, 11)
        ));
        logArea = new JTextArea(5, 40);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setEditable(false);
        logArea.setBackground(new Color(240, 242, 245));
        JScrollPane logScroll = new JScrollPane(logArea);
        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.setPreferredSize(new Dimension(800, 130));

        mainPanel.add(logPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Event listeners
        setupListeners();

        log("IPED Tools MCP iniciado em modo gráfico.");

        updateGeneratedConfig();

        // A test still running when the window closes must not leave its server process behind.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cancelRunningTest();
            }

            // The prompt file may have been edited outside the window.
            @Override
            public void windowActivated(java.awt.event.WindowEvent e) {
                startPromptPanel.refreshStatus();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(this::cancelRunningTest, "mcp-selftest-cleanup"));
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(new Color(25, 42, 86));
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

        ImageIcon logoIcon = getAppLogo(48, 48);
        if (logoIcon != null) {
            JLabel logoLabel = new JLabel(logoIcon);
            logoLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
            header.add(logoLabel, BorderLayout.WEST);
        }

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        textPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("IPED Tools MCP v" + VersionInfo.getVersion());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel("Ponte Inteligente entre Casos IPED e Modelos de Linguagem (LLMs)");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(220, 221, 225));

        textPanel.add(titleLabel);
        textPanel.add(subtitleLabel);

        JButton aboutBtn = new JButton("ℹ Sobre");
        aboutBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        aboutBtn.setFocusPainted(false);
        aboutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        aboutBtn.addActionListener(e -> showAboutDialog());

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        rightPanel.setOpaque(false);
        rightPanel.add(aboutBtn);

        header.add(textPanel, BorderLayout.CENTER);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private void showAboutDialog() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        ImageIcon aboutLogo = getAppLogo(64, 64);

        JLabel nameLabel = new JLabel("IPED Tools MCP v" + VersionInfo.getVersion());
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel(VersionInfo.APP_DESCRIPTION);
        descLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel buildLabel = new JLabel("• Data de Build: " + VersionInfo.getBuildTimestamp());
        buildLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        buildLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel ipedLabel = new JLabel("• Compatibilidade IPED Core: " + VersionInfo.getIpedCoreVersion());
        ipedLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ipedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel javaLabel = new JLabel("• Ambiente Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        javaLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        javaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel siteLabel = new JLabel("• Website Oficial: " + VersionInfo.OFFICIAL_WEBSITE);
        siteLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        siteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel gitLabel = new JLabel("• Código-Fonte: " + VersionInfo.GITHUB_REPO);
        gitLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        gitLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel licLabel = new JLabel("• Licença: " + VersionInfo.LICENSE);
        licLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        licLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(nameLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(descLabel);
        panel.add(Box.createVerticalStrut(12));
        panel.add(buildLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(ipedLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(javaLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(siteLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(gitLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(licLabel);

        JOptionPane.showMessageDialog(this, panel, "Sobre o IPED Tools MCP",
                aboutLogo != null ? JOptionPane.PLAIN_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                aboutLogo);
    }

    private void initAppIcons() {
        try {
            java.net.URL imgUrl = getClass().getResource("/images/icon.jpg");
            if (imgUrl != null) {
                Image master = javax.imageio.ImageIO.read(imgUrl);
                if (master != null) {
                    java.util.List<Image> icons = new java.util.ArrayList<>();
                    for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
                        icons.add(master.getScaledInstance(size, size, Image.SCALE_SMOOTH));
                    }
                    setIconImages(icons);
                }
            }
        } catch (Exception ex) {
            log("Aviso ao carregar ícone da aplicação: " + ex.getMessage());
        }
    }

    private ImageIcon getAppLogo(int width, int height) {
        try {
            java.net.URL imgUrl = getClass().getResource("/images/icon.jpg");
            if (imgUrl != null) {
                Image img = javax.imageio.ImageIO.read(imgUrl);
                if (img != null) {
                    Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void setupListeners() {
        testBrowseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Selecione a Pasta do Caso Processado pelo IPED");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            if (!testCaseField.getText().isBlank()) {
                chooser.setCurrentDirectory(new File(testCaseField.getText().trim()));
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                testCaseField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        runTestButton.addActionListener(e -> runSelfTest());
        cancelTestButton.addActionListener(e -> {
            StdioSelfTest test = currentTest;
            if (test != null) {
                log("Cancelando o teste...");
                test.cancel();
            }
        });

        clientSelectCombo.addActionListener(e -> updateGeneratedConfig());

        copyConfigButton.addActionListener(e -> {
            String configText = configJsonArea.getText();
            if (configText != null && !configText.isBlank()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(configText), null);
                copyConfigButton.setText("✔ Configuração Copiada com Sucesso!");
                Timer timer = new Timer(2500, evt -> copyConfigButton.setText("📋 Copiar Configuração para a Área de Transferência"));
                timer.setRepeats(false);
                timer.start();
                log("Configuração JSON copiada para a área de transferência.");
            }
        });
    }

    private void cancelRunningTest() {
        StdioSelfTest test = currentTest;
        if (test != null) {
            test.cancel();
        }
    }

    /** Runs the local STDIO test in a background thread, showing each step as it ends. */
    private void runSelfTest() {
        List<String> command = StdioSelfTest.command(McpApplication.getAppExecutableOrJarPath(),
                StdioSelfTest.currentJava());
        if (command == null) {
            JOptionPane.showMessageDialog(this, "O teste precisa da aplicação empacotada (executável ou JAR).\n"
                    + "Gere o pacote e abra a janela por ele.", "Teste indisponível", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String path = testCaseField.getText().trim();
        File caseDir = null;
        if (!path.isEmpty()) {
            caseDir = new File(path);
            if (!caseDir.isDirectory() || !iped.engine.data.IPEDSource.checkIfIsCaseFolder(caseDir)) {
                JOptionPane.showMessageDialog(this, "A pasta informada não é uma pasta de caso do IPED:\n" + path,
                        "Pasta inválida", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        testResultArea.setText("");
        runTestButton.setEnabled(false);
        testBrowseButton.setEnabled(false);
        cancelTestButton.setEnabled(true);
        log("Teste STDIO iniciado: " + String.join(" ", command)
                + (caseDir != null ? " | caso: " + caseDir.getAbsolutePath() : " | sem caso"));

        StdioSelfTest.Listener listener = new StdioSelfTest.Listener() {
            @Override
            public void stepStarted(String name) {
                SwingUtilities.invokeLater(() -> testResultArea.append(runningLine(name)));
            }

            @Override
            public void stepFinished(StdioSelfTest.StepResult result) {
                SwingUtilities.invokeLater(() -> showStep(result));
            }
        };
        StdioSelfTest test = new StdioSelfTest(command, caseDir, VersionInfo.getVersion(),
                StdioSelfTest.Timeouts.DEFAULT, listener);
        currentTest = test;

        new SwingWorker<List<StdioSelfTest.StepResult>, Void>() {
            @Override
            protected List<StdioSelfTest.StepResult> doInBackground() {
                return test.run();
            }

            @Override
            protected void done() {
                currentTest = null;
                runTestButton.setEnabled(true);
                testBrowseButton.setEnabled(true);
                cancelTestButton.setEnabled(false);
                try {
                    List<StdioSelfTest.StepResult> results = get();
                    boolean ok = results.stream().allMatch(r -> r.status() == StdioSelfTest.Status.PASSED);
                    if (ok) {
                        testResultArea.append("\n✔ O MCP está funcionando como o cliente de IA vai executá-lo.\n");
                        log("Teste STDIO concluído com sucesso.");
                        return;
                    }
                    testResultArea.append("\n❌ O teste falhou. Os detalhes estão no console de diagnóstico.\n");
                    log("Teste STDIO falhou. Últimas linhas do log do servidor:");
                    for (String line : test.stderrTail()) {
                        log("  [servidor] " + line);
                    }
                    log("Log do servidor do teste: " + (test.logFile() != null ? test.logFile()
                            : new File(System.getProperty("user.home"), ".iped-tools-mcp" + File.separator + "logs")
                                    .getAbsolutePath()));
                } catch (Exception ex) {
                    log("Erro inesperado no teste: " + ex);
                }
            }
        }.execute();
    }

    private static String runningLine(String step) {
        return "⏳ " + step + " ...\n";
    }

    /** Replaces the "running" line of a step by its result. */
    private void showStep(StdioSelfTest.StepResult r) {
        String text = testResultArea.getText();
        String running = runningLine(r.name());
        if (text.endsWith(running)) {
            testResultArea.setText(text.substring(0, text.length() - running.length()));
        }
        String icon = switch (r.status()) {
            case PASSED -> "✔";
            case FAILED -> "❌";
            case NOT_RUN -> "–";
        };
        String time = r.status() == StdioSelfTest.Status.NOT_RUN ? "" : String.format(" (%,d ms)", r.millis());
        testResultArea.append(icon + " " + r.name() + time + ": " + r.detail() + "\n");
        testResultArea.setCaretPosition(testResultArea.getDocument().getLength());
        if (r.status() != StdioSelfTest.Status.NOT_RUN) {
            log("Teste STDIO | " + r.name() + " | " + r.status() + time + " | " + r.detail());
        }
    }

    private void updateGeneratedConfig() {
        String execPath = McpApplication.getAppExecutableOrJarPath();
        String jsonEscapedExec = execPath.replace("\\", "\\\\");

        boolean isJar = execPath.toLowerCase().endsWith(".jar");
        String selectedClient = (String) clientSelectCombo.getSelectedItem();
        if (selectedClient == null) {
            selectedClient = "";
        }

        String configSnippet;
        if (selectedClient.contains("Formulário Visual")) {
            configSnippet = """
=== PREENCHIMENTO NO FORMULÁRIO DO LM STUDIO (CONFIGURAÇÃO ÚNICA) ===

1. No LM Studio, acesse a aba 'MCP' e adicione ou edite o servidor 'iped':
   • Nome: iped
   • Conexão: Neste computador
   • Comando: %s
   • Argumentos (adicione apenas 1 argumento clicando em '+ Adicionar argumento'):
     --stdio

2. Salve e ative a chavinha da conexão.

✨ PRONTO! COM APENAS O ARGUMENTO '--stdio', VOCÊ NÃO PRECISA RECONFIGURAR O LM STUDIO PARA TROCAR DE CASO.
Cada conversa tem o seu próprio caso: peça ao modelo para abrir a pasta do caso (ex: 'abra o caso D:\\meu_caso').

💡 DICA FORENSE:
No chat com o modelo, chame o prompt '/start_case' para carregar a metodologia forense, instruções de busca e regras de custódia!
""".formatted(execPath);
        } else if (selectedClient.contains("ng-mcp.json")) {
            configSnippet = """
{
  "servers": [
    {
      "id": "iped-tools-mcp",
      "name": "iped",
      "enabled": true,
      "connection": {
        "type": "stdio",
        "command": "%s",
        "args": [
          "--stdio"
        ],
        "env": {}
      }
    }
  ]
}""".formatted(jsonEscapedExec);
        } else if (isJar) {
            StringBuilder args = new StringBuilder();
            for (String arg : StdioSelfTest.JAR_JVM_ARGS) {
                args.append("        \"").append(arg).append("\",\n");
            }
            args.append("        \"-jar\",\n        \"").append(jsonEscapedExec).append("\",\n        \"--stdio\"");
            configSnippet = """
{
  "mcpServers": {
    "iped": {
      "command": "java",
      "args": [
%s
      ]
    }
  }
}""".formatted(args);
        } else {
            configSnippet = """
{
  "mcpServers": {
    "iped": {
      "command": "%s",
      "args": [
        "--stdio"
      ]
    }
  }
}""".formatted(jsonEscapedExec);
        }

        configJsonArea.setText(configSnippet);
        configJsonArea.setCaretPosition(0);
    }

    private static final org.slf4j.Logger WINDOW_LOGGER = org.slf4j.LoggerFactory.getLogger(MainWindow.class);

    public void log(String message) {
        // Everything shown in the window also goes to the GUI log file (~/.iped-tools-mcp/logs)
        WINDOW_LOGGER.info(message);
        String timestamp = timeFormat.format(new Date());
        logArea.append("[" + timestamp + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
