# Instalação do IPED Tools MCP

O IPED Tools MCP oferece duas modalidades de distribuição para Windows: o **Instalador Oficial (`.msi`)** e o **Pacote Portátil (`.zip`)**. Ambas já acompanham o runtime Java 21 embutido e isolado.

---

## 📦 Modalidade 1: Instalador Oficial Windows (`.msi`) — Recomendado

Ideal para estações de trabalho e gabinetes de perícia.

1. Baixe o arquivo `IPED-Tools-MCP-<versão>.msi` do portal oficial [www.mcp.ipedtools.com.br](https://www.mcp.ipedtools.com.br). `<versão>` é o número da versão publicada (ex.: `X.Y.Z`); use o nome exato do arquivo que aparece no portal.
2. Execute o instalador com privilégios de administrador.
3. Siga o assistente de instalação padrão do Windows.
4. Por padrão, o executável é instalado em:
   ```text
   C:\Program Files\IPED-Tools-MCP\IPED-Tools-MCP.exe
   ```
5. O instalador cria automaticamente atalhos na Área de Trabalho e no Menu Iniciar, além de registrar o aplicativo no Painel de Controle ("Adicionar ou Remover Programas").

---

## 💼 Modalidade 2: Pacote Portátil (`.zip`)

Indicado para execução direta sem necessidade de privilégios de administrador, pendrives ou unidades externas de perícia.

1. Baixe o arquivo `IPED-Tools-MCP-<versão>-windows-x64-portable.zip` do portal oficial.
2. Descompacte o arquivo `.zip` na pasta de sua preferência (exemplo: `C:\Ferramentas\IPED-Tools-MCP\`).
3. A estrutura da pasta extraída conterá:
   * `IPED-Tools-MCP.exe` — Executável principal unificado (GUI e MCP STDIO).
   * `runtime/` — Runtime Java 21 (Liberica JRE) embutido.
   * `app/` — Bibliotecas e dependências internas do Quarkus e IPED Core.

---

## ✅ Teste de Validação pós-Instalação

A validação é feita pelo [Configurador Gráfico](Configurador-Grafico):

1. Abra o **IPED Tools MCP** pelo atalho da Área de Trabalho ou do Menu Iniciar (ou dê um duplo-clique em `IPED-Tools-MCP.exe` na pasta portátil).
2. Confira a versão no título da janela: `IPED Tools MCP vX.Y.Z` (no lugar de `X.Y.Z`, a versão que você instalou). O botão **ℹ Sobre** mostra também a data do build e a versão do IPED Core.
3. Na seção **Testar o MCP**, clique em **▶ Executar teste**. Todas as etapas devem terminar com ✔: o teste executa o servidor do mesmo jeito que o cliente de IA vai executá-lo e confere que ele informa a mesma versão da janela. Se quiser, escolha antes a pasta de um caso processado pelo IPED para testar também a abertura do caso.

> 💡 O executável não imprime informações no terminal: argumentos como `--version` ou `--help` apenas abrem o configurador, que mostra a versão no título.

Com a instalação validada, use a seção **Configuração do Cliente de IA** do configurador para conectar o seu aplicativo de IA. Depois, na conversa, peça ao modelo para abrir a pasta do caso.
