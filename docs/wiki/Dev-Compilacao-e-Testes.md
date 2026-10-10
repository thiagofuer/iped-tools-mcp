# Compilação e Testes Automatizados

Este guia destina-se a desenvolvedores e mantenedores que desejam compilar o **IPED Tools MCP** a partir do código-fonte ou executar as baterias de testes integrados.

Para detalhes sobre governança de branches GitFlow e fluxo de Pull Requests, consulte as [Diretrizes de Contribuição (CONTRIBUTING.md)](https://github.com/thiagofuer/iped-tools-mcp/blob/main/CONTRIBUTING.md).

---

## 🛠️ Pré-requisitos de Desenvolvimento

* **Java Development Kit (JDK):** Versão 21 (LTS) de 64 bits (ex: BellSoft Liberica JDK 21, Eclipse Temurin 21 ou OpenJDK 21).
* **Apache Maven:** Versão 3.8.0 ou superior.
* **PowerShell:** Versão 5.1 ou superior (nativo do Windows 10/11).
* **Git:** Para controle de versão e submissão de PRs.

---

## 🔨 1. Compilação do Runner JAR

Para compilar o artefato JVM executável com todas as dependências Quarkus e IPED Core:

```powershell
mvn clean package -DskipTests
```

O artefato compilado é gerado em:
```text
target/iped-tools-mcp-1.0.0-runner.jar
```

---

## 🧪 2. Bateria de Testes Automatizados

### A. Testes Unitários e de Integração Maven
Executa todos os testes unitários do framework (incluindo testes de ferramentas, promts, versionamento e resolução de casos):

```powershell
mvn test
```

> ℹ️ **Resolução Dinâmica de Casos:** Os testes verificam a existência de um caso pericial configurado em `local-test.properties` (ou variável de ambiente `IPED_TEST_CASE_PATH`). Se nenhum caso for informado, os testes que exigem leitura direta degradam suavemente (*skipped*) sem quebrar a compilação do projeto.

### B. Teste Ponta a Ponta do Protocolo STDIO MCP
Script PowerShell que inicia o servidor em modo STDIO, envia frames JSON-RPC 2.0 reais pelo pipe e valida as respostas de todas as 27 ferramentas:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test_stdio_mcp.ps1 -CasePath "C:\casos_forenses\caso_operacao_01"
```

### C. Teste do Executável Nativo Windows
Valida o binário executável gerado pelo empacotador `jpackage`, testando a inicialização do processo nativo, isolamento de streams e respostas MCP:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test_native_exe.ps1 -CasePath "C:\casos_forenses\caso_operacao_01"
```

---

## 📜 Histórico de Versões
O registro cronológico de todas as releases e melhorias pode ser consultado no [CHANGELOG.md](https://github.com/thiagofuer/iped-tools-mcp/blob/main/CHANGELOG.md).
