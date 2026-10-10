# Instalação do IPED Tools MCP

O IPED Tools MCP oferece duas modalidades de distribuição para Windows: o **Instalador Oficial (`.msi`)** e o **Pacote Portátil (`.zip`)**. Ambas já acompanham o runtime Java 21 embutido e isolado.

---

## 📦 Modalidade 1: Instalador Oficial Windows (`.msi`) — Recomendado

Ideal para estações de trabalho e gabinetes de perícia.

1. Baixe o arquivo `IPED-Tools-MCP-1.0.0.msi` do portal oficial [www.mcp.ipedtools.com.br](https://www.mcp.ipedtools.com.br).
2. Execute o instalador com privilégios de administrador.
3. Siga o assistente de instalação padrão do Windows.
4. Por padrão, o executável é instalado em:
   ```text
   C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe
   ```
5. O instalador cria automaticamente atalhos na Área de Trabalho e no Menu Iniciar, além de registrar o aplicativo no Painel de Controle ("Adicionar ou Remover Programas").

---

## 💼 Modalidade 2: Pacote Portátil (`.zip`)

Indicado para execução direta sem necessidade de privilégios de administrador, pendrives ou unidades externas de perícia.

1. Baixe o arquivo `IPED-Tools-MCP-1.0.0-windows-x64-portable.zip` do portal oficial.
2. Descompacte o arquivo `.zip` na pasta de sua preferência (exemplo: `C:\Ferramentas\IPED-Tools-MCP\`).
3. A estrutura da pasta extraída conterá:
   * `IPED-Tools-MCP.exe` — Executável principal unificado (GUI e MCP STDIO).
   * `runtime/` — Runtime Java 21 (Liberica JRE) embutido.
   * `app/` — Bibliotecas e dependências internas do Quarkus e IPED Core.

---

## ✅ Teste de Validação pós-Instalação

Para confirmar se a instalação foi bem-sucedida, abra o Prompt de Comando (`cmd.exe`) ou o PowerShell e execute:

```cmd
"C:\Program Files\IPED Tools MCP\IPED-Tools-MCP.exe" --version
```

*(Ou utilize o caminho da sua pasta portátil, por exemplo: `"C:\Ferramentas\IPED-Tools-MCP\IPED-Tools-MCP.exe" --version`)*

A saída esperada deve exibir a versão instalada, data do build e runtime Java:

```text
IPED Tools MCP v1.0.0 (build 2026-10-08 17:37:34, Java 21.0.12, IPED Core 4.4.0-SNAPSHOT)
Website: https://www.mcp.ipedtools.com.br
Codigo-fonte: https://github.com/thiagofuer/iped-tools-mcp
Licenca: GPLv3 / Open Source
```

Com a instalação validada, avance para o [Configurador Gráfico](Configurador-Grafico) para carregar seu primeiro caso IPED.
