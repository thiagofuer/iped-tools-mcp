# IPED Tools MCP

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](pom.xml)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.39.3-red.svg)](https://quarkus.io/)
[![MCP](https://img.shields.io/badge/MCP-2024--11--05-green.svg)](https://modelcontextprotocol.io/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Distribution](https://img.shields.io/badge/Download-mcp.ipedtools.com.br-brightgreen.svg)](https://www.mcp.ipedtools.com.br)
[![Wiki](https://img.shields.io/badge/Docs-Wiki-orange.svg)](https://github.com/thiagofuer/iped-tools-mcp/wiki)

**IPED Tools MCP** é um servidor nativo baseado na especificação **Model Context Protocol (MCP)** que conecta Modelos de Linguagem e Inteligências Artificiais (como Claude Desktop, Claude Code, Cursor, Antigravity e LM Studio) diretamente a casos processados pelo **IPED (Indexador e Processador de Evidências Digitais)**.

Elimina intermediários de rede e servidores HTTP legados, permitindo que a IA interrogue diretamente os índices Apache Lucene e metadados estruturados do IPED em memória de processo, com alto desempenho, preservação da cadeia de custódia e garantia de operação 100% desconectada (air-gapped).

---

## 🌐 Distribuição Oficial e Código-Fonte

- **Download Oficial de Executáveis e Instaladores:** [https://www.mcp.ipedtools.com.br](https://www.mcp.ipedtools.com.br)
- **Repositório de Código-Fonte:** [https://github.com/thiagofuer/iped-tools-mcp](https://github.com/thiagofuer/iped-tools-mcp)
- **Documentação Completa e Guias:** [Wiki Oficial do GitHub](https://github.com/thiagofuer/iped-tools-mcp/wiki)

Os binários compilados para Windows (pacote portátil `.zip` e instalador `.msi` com runtime Java 21 embutido) e os manifestos criptográficos `SHA256SUMS.txt` são distribuídos através do portal oficial [www.mcp.ipedtools.com.br](https://www.mcp.ipedtools.com.br).

---

## 🎯 Personas e Perfis de Uso

O IPED Tools MCP foi projetado para atender aos diferentes atores do ecossistema pericial, persecução penal, perícia judicial e contencioso digital:

| Perfil | Foco de Atuação | Como o IPED Tools MCP Potencializa o Trabalho |
|---|---|---|
| **Perito Criminal / Perito Oficial** | Rigor técnico, preservação de cadeia de custódia, fundamentação do laudo pericial e auditoria de evidências. | Consultas precisas em metadados estruturados (EXIF, chats, geolocalização), extração de texto paginada, marcação de triagem para o laudo (`set_item_checked`) e inclusão em marcadores periciais (`add_to_bookmark`). |
| **Perito Judicial** | Imparcialidade, equidistância das partes e rigor metodológico em demandas cíveis, trabalhistas ou criminais. Elaboração do laudo e resposta aos quesitos do juiz e das partes. | Resposta célere e fundamentada a quesitos complexos com suporte de IA consultando evidências indexadas, checagem de hashes e integridade, garantindo rastreabilidade das conclusões apresentadas ao juízo. |
| **Assistente Técnico** | Análise crítica, formulação de quesitos estratégicos, acompanhamento das diligências e elaboração de parecer técnico para a parte representada. | Varredura ágil e profunda de grandes volumes probatórios, conferência da integridade e metodologia adotada nos laudos oficiais/judiciais, localização rápida de evidências favoráveis à tese da parte e subsídios para impugnações técnicas. |
| **Analista de Inteligência Policial** | Identificação de padrões, vínculo entre suspeitos, fluxos financeiros e cronologia dos fatos. | Análise de grafos de comunicação (`get_communications_graph`), ranking de interlocutores mais frequentes (`get_top_contacts`), reconstrução de eventos ao redor de um marco temporal (`get_events_around_time`) e cruzamento de duplicatas por hash (`get_item_relations`). |
| **Autoridade Policial / Delegado / Promotor** | Visão executiva da investigação, respostas a quesitos formulados e tomada rápida de decisões. | Resumos executivos de casos (`get_case_summary`), identificação rápida de alvos/dispositivos (`get_device_and_owner_info`), filtros automáticos de IA para detecção de armas/drogas/faces e busca multimodal por similares. |

---

## ⚡ Início Rápido (Quickstart)

Configurar o IPED Tools MCP com seu aplicativo de IA favorito leva menos de 2 minutos:

1. **Baixe o Software:** Obtenha o instalador `.msi` ou o pacote `.zip` portátil em [www.mcp.ipedtools.com.br](https://www.mcp.ipedtools.com.br) e confirme o hash SHA-256 com `dist/SHA256SUMS.txt`.
2. **Abra o Configurador Gráfico:** Dê um duplo-clique em `IPED-Tools-MCP.exe` na Área de Trabalho ou no diretório extraído.
3. **Selecione o Caso IPED:** Clique em **"Selecionar Caso IPED..."** e aponte para a pasta raiz de um caso pericial indexado.
4. **Copie a Configuração (1-Click):** Escolha o seu aplicativo de IA no menu suspenso (Claude Desktop, Cursor, Antigravity ou LM Studio) e clique em **"📋 Copiar Configuração para a Área de Transferência"**.
5. **Inicie a Investigação:** Cole a configuração no seu aplicativo de IA. No chat, digite o prompt `/start_case` para ativar a metodologia pericial anti-alucinação.

> 💡 **Nota:** Graças à sincronização dinâmica de caso ativo, você só precisa configurar seu cliente de IA **uma única vez** (com o argumento `--stdio`). Trocas de caso na interface gráfica ou via comando `open_case` no chat são refletidas automaticamente.

---

## 📖 Documentação Completa na Wiki

Toda a documentação técnica, manuais operacionais e guias passo a passo estão organizados na **[Wiki Oficial](https://github.com/thiagofuer/iped-tools-mcp/wiki)**:

* **[Requisitos e Instalação](https://github.com/thiagofuer/iped-tools-mcp/wiki/Instalacao):** Detalhes de hardware, compatibilidade do IPED e validação pós-instalação.
* **[Guias de Clientes de IA](https://github.com/thiagofuer/iped-tools-mcp/wiki/Configurador-Grafico):** Passo a passo para [Claude Desktop](https://github.com/thiagofuer/iped-tools-mcp/wiki/Cliente-Claude-Desktop), [Cursor](https://github.com/thiagofuer/iped-tools-mcp/wiki/Cliente-Cursor), [Antigravity](https://github.com/thiagofuer/iped-tools-mcp/wiki/Cliente-Antigravity), [LM Studio (Air-Gapped & Offline)](https://github.com/thiagofuer/iped-tools-mcp/wiki/Cliente-LM-Studio) e [Claude Code](https://github.com/thiagofuer/iped-tools-mcp/wiki/Cliente-Claude-Code).
* **[Metodologia Forense e start_case](https://github.com/thiagofuer/iped-tools-mcp/wiki/Fluxo-de-Investigacao):** Diretrizes da ISO/IEC 27037 e ciclo de auditoria probatória com o IPED Desktop.
* **[Catálogo Completo das 27 Ferramentas MCP](https://github.com/thiagofuer/iped-tools-mcp/wiki/Catalogo-de-Ferramentas):** Propósito e exemplos práticos de perguntas para cada ferramenta forense.
* **[Consultas Lucene e Metadados](https://github.com/thiagofuer/iped-tools-mcp/wiki/Consultas-Lucene-e-Propriedades):** Guia de sintaxe de pesquisa, escape de caracteres e catálogo semântico de propriedades.
* **[Boas Práticas e Custódia](https://github.com/thiagofuer/iped-tools-mcp/wiki/Boas-Praticas-Forenses):** Princípios de acesso estritamente somente-leitura e salvaguardas probatórias.
* **[Solução de Problemas (FAQ)](https://github.com/thiagofuer/iped-tools-mcp/wiki/Solucao-de-Problemas):** Resolução de dúvidas frequentes, diagnóstico de conexão e logs de erro.
* **[Guia do Desenvolvedor](https://github.com/thiagofuer/iped-tools-mcp/wiki/Dev-Arquitetura):** Arquitetura interna, compilação Maven, testes automatizados e empacotamento WiX MSI.

---

## 📄 Governança e Contribuição

- O fluxo de desenvolvimento segue o **GitFlow Tradicional** (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`).
- O versionamento segue rigorosamente a especificação **Semantic Versioning 2.0.0**.
- Para detalhes sobre padrões de código, fluxo de PRs e manutenção da documentação, consulte [CONTRIBUTING.md](CONTRIBUTING.md).
- O histórico de alterações de cada versão pode ser consultado em [CHANGELOG.md](CHANGELOG.md).

---

## ⚖ Licença

Distribuído sob a licença **GPLv3** (GNU General Public License v3.0). Consulte o arquivo [LICENSE](LICENSE) para mais informações.
