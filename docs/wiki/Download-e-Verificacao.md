# Download e Verificação de Integridade

Em ambientes de perícia oficial, auditoria judicial e custódia de evidências digitais, a validação de autenticidade e integridade dos executáveis é indispensável antes da instalação.

---

## 🌐 Portal Oficial de Distribuição

Todos os binários oficiais, executáveis e instaladores do IPED Tools MCP são distribuídos exclusivamente através do portal oficial:

👉 **[https://www.mcp.ipedtools.com.br](https://www.mcp.ipedtools.com.br)**

Pacotes disponíveis para download:
1. **Instalador Oficial Windows (`.msi`):** `IPED-Tools-MCP-1.0.0.msi` (recomendado para computadores de trabalho e estações periciais fixas).
2. **Pacote Portátil (`.zip`):** `IPED-Tools-MCP-1.0.0-windows-x64-portable.zip` (ideal para execução a partir de unidades externas ou ambientes sem privilégios de administrador).
3. **Manifesto Criptográfico:** `SHA256SUMS.txt` (contendo as assinaturas hash SHA-256 de cada arquivo).

---

## 🛡️ Verificação de Integridade Criptográfica (SHA-256)

Após realizar o download do arquivo desejado, abra o **PowerShell** no diretório onde o arquivo foi salvo e execute o comando correspondente:

### Verificando o Pacote Portátil (`.zip`)
```powershell
Get-FileHash -Algorithm SHA256 "IPED-Tools-MCP-1.0.0-windows-x64-portable.zip"
```

### Verificando o Instalador Windows (`.msi`)
```powershell
Get-FileHash -Algorithm SHA256 "IPED-Tools-MCP-1.0.0.msi"
```

### Comparação com o Manifesto Oficial
Compare a saída hexadecimal com o conteúdo publicado em `SHA256SUMS.txt`:

```text
451B7A9A8AFAEC91E8D2556010BF3EA0DECDF7F680770985AB8D221F48339CEB  IPED-Tools-MCP-1.0.0-windows-x64-portable.zip
4C04E4B38589BB8B63C005647509A228456A791B1F435D7A758D77D5090EC179  IPED-Tools-MCP-1.0.0.msi
```

Se o hash gerado coincidir perfeitamente com o valor de referência, a integridade do pacote está confirmada e o software pode ser instalado com segurança.
