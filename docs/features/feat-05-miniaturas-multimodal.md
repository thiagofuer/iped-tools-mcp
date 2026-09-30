# FEAT-05: Inspeção Visual Multimodal & Miniaturas (Gallery/Thumbnails)

**Código:** FEAT-05  
**Status:** Especificado (SDD)  
**Módulo IPED de Referência:** `iped.app.ui.App` (`GalleryTable`, `GalleryModel`, `ImageThumbTask`, `galleryBlurButton`, `galleryGrayButton`)  
**Data:** 2026-09-27  

---

## 1. Contexto e Motivação Forense

No IPED Desktop, uma das abas mais utilizadas pelos peritos é a **Galeria de Imagens**, que permite navegar visualmente por milhares de fotos, capturas de tela e quadros de vídeos de forma rápida.

Com o avanço dos modelos de linguagem multimodais (como **Claude 3.5 Sonnet**, **GPT-4o**, e modelos locais como **Qwen2-VL** e **Llama 3.2 Vision** rodando no LM Studio), a IA não precisa mais ficar limitada a metadados textuais. Se o servidor MCP fornecer os bytes da imagem:
1. **O modelo de IA "enxerga" a evidência:** É capaz de transcrever manuscritos ilegíveis, identificar placas de automóveis em fotos borradas, analisar prints de transferências bancárias, descrever tatuagens de suspeitos ou avaliar marcas de armas.
2. **O perito vê a evidência no chat:** Clientes MCP (como Claude Desktop e LM Studio) renderizam a imagem retornada diretamente no histórico da conversa, tornando a análise interativa e rica.

---

## 2. Engenharia e APIs Internas do IPED Core

O IPED possui uma infraestrutura dedicada de extração e cache de miniaturas:

```java
// Obtenção da miniatura pré-processada pelo IPED
IItem item = ipedSource.getItem(itemId);
BufferedImage thumb = ImageThumbTask.getThumbnail(item);

// Se não houver thumb pré-gerado, redimensiona sob demanda a partir do stream original
if (thumb == null) {
    try (InputStream is = item.getStream()) {
        thumb = ImageIO.read(is);
        thumb = UiUtil.scaleImage(thumb, maxDimension);
    }
}

// Conversão para Base64 (formato aceito pelo protocolo MCP)
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ImageIO.write(thumb, "JPEG", baos);
String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
```

---

## 3. Especificação das Ferramentas MCP (`@Tool`)

### Assinatura Proposta
```java
@Tool(name = "get_item_thumbnail",
      description = "Retrieves a scaled visual thumbnail of an image, photo, or document page in Base64 JPEG format. Enables multimodal LLMs to visually inspect evidence.")
public ImageContentResponse getItemThumbnail(
    @ToolArg(name = "item_id", description = "The ID of the image or document item.") int itemId,
    @ToolArg(name = "max_dimension", description = "Maximum width or height in pixels (default 512, preserves aspect ratio).") Integer maxDimension
)
```

### Formato do Retorno MCP (Multimodal Content)
No protocolo MCP, a ferramenta pode retornar blocos de conteúdo do tipo `image`:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Thumbnail gerado com sucesso para o item 14205 (comprovante_ted.jpg, 512x384 px)."
    },
    {
      "type": "image",
      "data": "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/...",
      "mimeType": "image/jpeg"
    }
  ]
}
```

---

## 4. Prompt Engineering & Workflow Guidance

* **Perguntas-Gatilho do Usuário:**
  * *"Mostre a foto da arma encontrada no quarto."*
  * *"Pode ler o que está escrito nesse recibo rasurado da imagem ID 502?"*
  * *"Descreva o que aparece na foto 9871."*
  * *"Exiba a miniatura do documento suspeito."*
* **Workflow Guidance:**
  * Quando o usuário pedir para analisar visualmente um documento escaneado ou uma foto onde o texto não foi extraído por OCR, invocar `get_item_thumbnail` para inspecionar os caracteres diretamente pela visão computacional do LLM.
