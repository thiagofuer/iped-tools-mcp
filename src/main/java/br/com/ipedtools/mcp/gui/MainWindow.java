package br.com.ipedtools.mcp.gui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import br.com.ipedtools.mcp.McpApplication;
import br.com.ipedtools.mcp.VersionInfo;
import br.com.ipedtools.mcp.service.IpedCoreService;

/**
 * Modern Swing configuration and diagnostic GUI for IPED Tools MCP.
 * Allows non-technical forensic examiners to select their processed IPED case,
 * inspect status, and copy client configuration JSON in a single click.
 */
public class MainWindow extends JFrame {

    private final JTextField casePathField;
    private final JButton browseButton;
    private final JButton loadButton;

    private final JLabel statusBadgeLabel;
    private final JLabel itemsCountLabel;
    private final JLabel categoriesCountLabel;
    private final JLabel bookmarksCountLabel;

    private final JComboBox<String> clientSelectCombo;
    private final JCheckBox pinCaseCheck;
    private final JTextArea configJsonArea;
    private final JButton copyConfigButton;

    private final JTextArea logArea;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.of("pt", "BR"));

    public MainWindow() {
        super("IPED Tools MCP v" + VersionInfo.getVersion() + " — Assistente de Inteligência Artificial Forense");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 720);
        setMinimumSize(new Dimension(750, 600));
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Header Panel
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Center Content Panel (Case Selection + Case Stats + Client Config)
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Case selector section
        JPanel selectorPanel = new JPanel(new BorderLayout(8, 8));
        selectorPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 1. Selecionar Caso IPED ",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("SansSerif", Font.BOLD, 12)
        ));

        casePathField = new JTextField();
        casePathField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        browseButton = new JButton("Procurar Pasta...");
        loadButton = new JButton("Carregar Caso");
        loadButton.setFont(new Font("SansSerif", Font.BOLD, 11));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(browseButton);
        buttonPanel.add(loadButton);

        selectorPanel.add(casePathField, BorderLayout.CENTER);
        selectorPanel.add(buttonPanel, BorderLayout.EAST);
        contentPanel.add(selectorPanel);
        contentPanel.add(Box.createVerticalStrut(8));

        // Case stats section
        JPanel statsPanel = new JPanel(new GridLayout(2, 2, 10, 6));
        statsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), " 2. Status do Caso Selecionado ",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("SansSerif", Font.BOLD, 12)
        ));

        statusBadgeLabel = new JLabel("⚪ Nenhum caso carregado");
        statusBadgeLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusBadgeLabel.setForeground(Color.DARK_GRAY);

        itemsCountLabel = new JLabel("• Itens indexados: -");
        categoriesCountLabel = new JLabel("• Categorias encontradas: -");
        bookmarksCountLabel = new JLabel("• Marcadores existentes: -");

        statsPanel.add(statusBadgeLabel);
        statsPanel.add(itemsCountLabel);
        statsPanel.add(categoriesCountLabel);
        statsPanel.add(bookmarksCountLabel);
        contentPanel.add(statsPanel);
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

        pinCaseCheck = new JCheckBox("Fixar caso no comando (--case)");
        pinCaseCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        pinCaseCheck.setToolTipText("Desmarcado (Recomendado): O servidor MCP acompanha automaticamente qualquer caso aberto na janela ou via chat. Marcado: Bloqueia o comando com o caminho fixo.");

        JPanel comboAndCheck = new JPanel(new BorderLayout(8, 0));
        comboAndCheck.add(clientSelectCombo, BorderLayout.CENTER);
        comboAndCheck.add(pinCaseCheck, BorderLayout.EAST);

        configTopPanel.add(clientLabel, BorderLayout.WEST);
        configTopPanel.add(comboAndCheck, BorderLayout.CENTER);

        JLabel configHintLabel = new JLabel("💡 Dica: Configure seu aplicativo de IA apenas 1 VEZ. O servidor sincroniza os casos automaticamente!");
        configHintLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        configHintLabel.setForeground(new Color(41, 128, 185));
        configHintLabel.setBorder(new EmptyBorder(2, 4, 2, 4));

        JPanel topContainer = new JPanel(new BorderLayout(0, 4));
        topContainer.add(configTopPanel, BorderLayout.NORTH);
        topContainer.add(configHintLabel, BorderLayout.SOUTH);

        configJsonArea = new JTextArea(8, 40);
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

        // Restaura automaticamente o último caso ativo (se existir)
        File activeFile = IpedCoreService.ACTIVE_CASE_FILE;
        if (activeFile.exists()) {
            try {
                String savedPath = java.nio.file.Files.readString(activeFile.toPath(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!savedPath.isBlank()) {
                    File caseDir = new File(savedPath);
                    if (caseDir.exists() && iped.engine.data.IPEDSource.checkIfIsCaseFolder(caseDir)) {
                        casePathField.setText(savedPath);
                        log("Restaurando último caso ativo: " + savedPath);
                        loadCaseAsync(caseDir);
                    }
                }
            } catch (Exception ex) {
                log("Aviso ao ler caso ativo: " + ex.getMessage());
            }
        }

        updateGeneratedConfig();

        // Monitor de sincronização em tempo real (atualiza a janela se a IA trocar de caso pelo chat)
        Timer externalCaseWatcher = new Timer(1500, evt -> {
            try {
                if (activeFile.exists()) {
                    String currentOnDisk = java.nio.file.Files.readString(activeFile.toPath(), java.nio.charset.StandardCharsets.UTF_8).trim();
                    String currentInField = casePathField.getText().trim();
                    if (!currentOnDisk.isBlank() && !currentOnDisk.equalsIgnoreCase(currentInField)) {
                        File newCaseDir = new File(currentOnDisk);
                        if (newCaseDir.exists() && iped.engine.data.IPEDSource.checkIfIsCaseFolder(newCaseDir)) {
                            log("Caso atualizado externamente via Chat de IA: " + currentOnDisk);
                            casePathField.setText(currentOnDisk);
                            loadCaseAsync(newCaseDir);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
        externalCaseWatcher.start();
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(new Color(25, 42, 86));
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

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

        JOptionPane.showMessageDialog(this, panel, "Sobre o IPED Tools MCP", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setupListeners() {
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Selecione a Pasta do Caso Processado pelo IPED");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            if (!casePathField.getText().isBlank()) {
                chooser.setCurrentDirectory(new File(casePathField.getText()));
            }

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                casePathField.setText(selected.getAbsolutePath());
                loadCaseAsync(selected);
            }
        });

        loadButton.addActionListener(e -> {
            String path = casePathField.getText().trim();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Selecione uma pasta de caso antes de carregar.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            loadCaseAsync(new File(path));
        });

        clientSelectCombo.addActionListener(e -> updateGeneratedConfig());
        pinCaseCheck.addActionListener(e -> updateGeneratedConfig());

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

    private void loadCaseAsync(File caseDir) {
        loadButton.setEnabled(false);
        browseButton.setEnabled(false);
        statusBadgeLabel.setText("⏳ Carregando índice do caso...");
        statusBadgeLabel.setForeground(new Color(230, 126, 34));
        log("Iniciando abertura do caso: " + caseDir.getAbsolutePath());

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;

            @Override
            protected Boolean doInBackground() {
                try {
                    IpedCoreService.getInstance().openCase(caseDir);
                    return true;
                } catch (Exception ex) {
                    errorMessage = ex.getMessage();
                    return false;
                }
            }

            @Override
            protected void done() {
                loadButton.setEnabled(true);
                browseButton.setEnabled(true);

                try {
                    if (get()) {
                        IpedCoreService service = IpedCoreService.getInstance();
                        statusBadgeLabel.setText("✔ Caso carregado com sucesso (" + service.getSourceId() + ")");
                        statusBadgeLabel.setForeground(new Color(39, 174, 96));

                        int totalItems = service.getTotalIndexedItems();
                        int totalCats = service.listCategories().size();
                        int totalBms = service.listBookmarks().size();

                        itemsCountLabel.setText("• Itens indexados: " + numberFormat.format(totalItems));
                        categoriesCountLabel.setText("• Categorias encontradas: " + numberFormat.format(totalCats));
                        bookmarksCountLabel.setText("• Marcadores existentes: " + numberFormat.format(totalBms));

                        log("Caso aberto com sucesso! " + numberFormat.format(totalItems) + " itens, " + totalCats + " categorias.");
                        updateGeneratedConfig();
                    } else {
                        statusBadgeLabel.setText("❌ Erro ao carregar caso");
                        statusBadgeLabel.setForeground(new Color(192, 57, 43));
                        log("Falha ao abrir caso: " + errorMessage);
                        JOptionPane.showMessageDialog(MainWindow.this,
                                "Não foi possível carregar o caso selecionado:\n" + errorMessage,
                                "Erro ao Carregar Caso", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    log("Erro inesperado: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void updateGeneratedConfig() {
        String casePath = casePathField.getText().trim();
        if (casePath.isEmpty()) {
            casePath = "D:\\caminho\\para\\seu_caso_iped";
        }

        String execPath = McpApplication.getAppExecutableOrJarPath();
        String jsonEscapedCase = casePath.replace("\\", "\\\\");
        String jsonEscapedExec = execPath.replace("\\", "\\\\");

        boolean isJar = execPath.toLowerCase().endsWith(".jar");
        boolean pinCase = pinCaseCheck != null && pinCaseCheck.isSelected();
        String selectedClient = (String) clientSelectCombo.getSelectedItem();
        if (selectedClient == null) {
            selectedClient = "";
        }

        String configSnippet;
        if (selectedClient.contains("Formulário Visual")) {
            if (pinCase) {
                configSnippet = """
=== PREENCHIMENTO NO FORMULÁRIO DO LM STUDIO (CASO FIXO) ===

1. No LM Studio, acesse a aba 'MCP' e adicione ou edite o servidor 'iped':
   • Nome: iped
   • Conexão: Neste computador
   • Comando: %s
   • Argumentos (adicione um por linha clicando em '+ Adicionar argumento'):
     1º argumento: --stdio
     2º argumento: --case
     3º argumento: %s

* ATENÇÃO: Digite os caminhos com barra normal (ex: %s), NUNCA use barras duplas (\\\\) nos campos de texto do formulário!
""".formatted(execPath, casePath, casePath);
            } else {
                configSnippet = """
=== PREENCHIMENTO NO FORMULÁRIO DO LM STUDIO (CONFIGURAÇÃO ÚNICA RECOMENDADA) ===

1. No LM Studio, acesse a aba 'MCP' e adicione ou edite o servidor 'iped':
   • Nome: iped
   • Conexão: Neste computador
   • Comando: %s
   • Argumentos (adicione apenas 1 argumento clicando em '+ Adicionar argumento'):
     --stdio

2. Salve e ative a chavinha da conexão.

✨ PRONTO! COM APENAS O ARGUMENTO '--stdio', VOCÊ NUNCA MAIS PRECISA RECONFIGURAR O LM STUDIO!
O servidor MCP sincronizará automaticamente com o caso que você carregar nesta janela ou com qualquer caso que você pedir para a IA abrir no chat (ex: 'abra o caso D:\\meu_caso').
""".formatted(execPath);
            }
        } else if (selectedClient.contains("ng-mcp.json")) {
            if (pinCase) {
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
          "--stdio",
          "--case",
          "%s"
        ],
        "env": {}
      }
    }
  ]
}""".formatted(jsonEscapedExec, jsonEscapedCase);
            } else {
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
            }
        } else if (isJar) {
            String argsList = pinCase
                    ? """
                        "-Djava.security.manager=allow",
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                        "--add-opens=java.base/java.math=ALL-UNNAMED",
                        "--add-opens=java.base/java.util=ALL-UNNAMED",
                        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                        "--add-opens=java.base/java.net=ALL-UNNAMED",
                        "--add-opens=java.base/java.text=ALL-UNNAMED",
                        "--add-opens=java.base/java.nio=ALL-UNNAMED",
                        "--add-opens=java.base/java.io=ALL-UNNAMED",
                        "-jar",
                        "%s",
                        "--stdio",
                        "--case",
                        "%s"
                      """.formatted(jsonEscapedExec, jsonEscapedCase)
                    : """
                        "-Djava.security.manager=allow",
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                        "--add-opens=java.base/java.math=ALL-UNNAMED",
                        "--add-opens=java.base/java.util=ALL-UNNAMED",
                        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
                        "--add-opens=java.base/java.net=ALL-UNNAMED",
                        "--add-opens=java.base/java.text=ALL-UNNAMED",
                        "--add-opens=java.base/java.nio=ALL-UNNAMED",
                        "--add-opens=java.base/java.io=ALL-UNNAMED",
                        "-jar",
                        "%s",
                        "--stdio"
                      """.formatted(jsonEscapedExec);

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
}""".formatted(argsList);
        } else {
            if (pinCase) {
                configSnippet = """
{
  "mcpServers": {
    "iped": {
      "command": "%s",
      "args": [
        "--stdio",
        "--case",
        "%s"
      ]
    }
  }
}""".formatted(jsonEscapedExec, jsonEscapedCase);
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
        }

        configJsonArea.setText(configSnippet);
        configJsonArea.setCaretPosition(0);
    }

    public void log(String message) {
        String timestamp = timeFormat.format(new Date());
        logArea.append("[" + timestamp + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
