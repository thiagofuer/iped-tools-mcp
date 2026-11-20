package br.com.ipedtools.mcp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import br.com.ipedtools.mcp.VersionInfo;
import br.com.ipedtools.mcp.prompts.StartPromptStore;

/**
 * Section of the configurator for the start_case prompt: shows which methodology is in use, copies the full prompt
 * (the same text the server returns) for clients without MCP prompts, and creates, opens or restores the user copy.
 * The conduct rules are always added by the application and are not part of the editable file.
 */
final class StartPromptPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String COPY_LABEL = "📋 Copiar prompt";

    private final transient StartPromptStore store;
    private final transient Consumer<String> log;
    private final JLabel statusLabel = new JLabel();
    private final JTextField targetField = new JTextField();
    private final JTextField caseField = new JTextField();
    private final JButton copyButton = new JButton(COPY_LABEL);
    private final JButton editButton = new JButton("Editar");
    private final JButton folderButton = new JButton("Abrir pasta");
    private final JButton restoreButton = new JButton("Restaurar padrão");

    StartPromptPanel(StartPromptStore store, Consumer<String> log) {
        super(new BorderLayout(6, 4));
        this.store = store;
        this.log = log;
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                " 2. Prompt inicial da investigação (start_case) ", TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION, new Font("SansSerif", Font.BOLD, 12)));

        JLabel hint = new JLabel("<html>Para clientes que não oferecem os prompts do MCP, copie o prompt e cole-o como "
                + "primeira mensagem da conversa. A metodologia pode ser editada; as regras de conduta (acesso só pelo "
                + "MCP, checagem de conteúdo sensível, aceite e confirmação de escritas) são sempre acrescentadas pelo "
                + "aplicativo e não fazem parte do arquivo editável.</html>");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 11));

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(hint, BorderLayout.NORTH);
        north.add(statusLabel, BorderLayout.SOUTH);

        targetField.setToolTipText("Alvo ou objetivo da investigação (opcional), por exemplo: fraude em licitação");
        caseField.setToolTipText("Pasta do caso (opcional): o prompt pede ao modelo para abri-la com open_case");
        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1, 0, 1, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        fields.add(new JLabel("Foco (opcional):"), c);
        c.gridy = 1;
        fields.add(new JLabel("Pasta do caso (opcional):"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        fields.add(targetField, c);
        c.gridy = 1;
        fields.add(caseField, c);

        copyButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttons.add(copyButton);
        buttons.add(editButton);
        buttons.add(folderButton);
        buttons.add(restoreButton);

        add(north, BorderLayout.NORTH);
        add(fields, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        copyButton.addActionListener(e -> copyPrompt());
        editButton.addActionListener(e -> edit());
        folderButton.addActionListener(e -> openFolder());
        restoreButton.addActionListener(e -> restore());
        refreshStatus();
    }

    /** The prompt copied by the window: the same text the server returns for start_case with these arguments. */
    String promptText() {
        return store.build(targetField.getText(), caseField.getText());
    }

    /** Fills the focus and case folder fields. */
    void setArguments(String target, String casePath) {
        targetField.setText(target);
        caseField.setText(casePath);
    }

    /** Updates the status line from the user file; called again when the window gets the focus back. */
    void refreshStatus() {
        StartPromptStore.Methodology m = store.load();
        String text;
        Color color;
        switch (m.state()) {
            case DEFAULT -> {
                text = "Em uso: metodologia padrão da versão " + VersionInfo.getVersion() + ".";
                color = new Color(39, 120, 70);
            }
            case CUSTOM -> {
                text = "Em uso: metodologia personalizada (" + m.userFile().getAbsolutePath() + ")"
                        + (m.detail() != null ? ". Aviso: " + m.detail() + "." : ".");
                color = new Color(41, 128, 185);
            }
            case CUSTOM_OUTDATED -> {
                text = "Em uso: metodologia personalizada desatualizada: " + m.detail()
                        + ". Compare com o padrão ou use Restaurar padrão.";
                color = new Color(211, 84, 0);
            }
            default -> {
                text = "Metodologia personalizada inválida (" + m.detail() + "): usando a metodologia padrão.";
                color = new Color(192, 57, 43);
            }
        }
        statusLabel.setText("<html>" + escape(text) + "</html>");
        statusLabel.setForeground(color);
        restoreButton.setEnabled(m.userFile().exists());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void copyPrompt() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(promptText()), null);
        copyButton.setText("✔ Prompt copiado!");
        Timer timer = new Timer(2500, evt -> copyButton.setText(COPY_LABEL));
        timer.setRepeats(false);
        timer.start();
        log.accept("Prompt start_case copiado para a área de transferência (" + store.load().state() + ").");
    }

    private void edit() {
        File file = store.userFile();
        try {
            if (store.createUserCopy()) {
                log.accept("Cópia personalizada do prompt criada a partir do padrão: " + file.getAbsolutePath());
            }
        } catch (Exception ex) {
            showError("Não foi possível criar a cópia do prompt", ex);
            return;
        }
        refreshStatus();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                Desktop.getDesktop().edit(file);
                log.accept("Prompt aberto no editor: " + file.getAbsolutePath());
                return;
            }
        } catch (Exception ex) {
            log.accept("Nenhum editor associado a arquivos .md: " + ex.getMessage());
        }
        openFolder();
        JOptionPane.showMessageDialog(this, "Não há um editor associado a arquivos .md neste computador.\n"
                + "Abra o arquivo num editor de texto:\n" + file.getAbsolutePath(), "Editar prompt",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void openFolder() {
        File dir = store.userFile().getParentFile();
        try {
            Files.createDirectories(dir.toPath());
            Desktop.getDesktop().open(dir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Pasta do prompt:\n" + dir.getAbsolutePath(), "Abrir pasta",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void restore() {
        if (!store.userFile().exists()) {
            refreshStatus();
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Voltar para a metodologia padrão desta versão?\n"
                + "A cópia personalizada será guardada como backup na mesma pasta.", "Restaurar padrão",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            File backup = store.restoreDefault();
            log.accept("Metodologia padrão restaurada. Backup da cópia personalizada: "
                    + (backup != null ? backup.getAbsolutePath() : "(nenhum)"));
        } catch (Exception ex) {
            showError("Não foi possível restaurar o padrão", ex);
        }
        refreshStatus();
    }

    private void showError(String title, Exception ex) {
        log.accept(title + ": " + ex);
        JOptionPane.showMessageDialog(this, title + ":\n" + ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
