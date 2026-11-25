# Arquitetura

O app Android concentra os dados no dispositivo e mantém os componentes de parsing, classificação e persistência separados. Não há cliente HTTP, servidor ou permissão `INTERNET`.

## Módulos

- `notas/parser`: decodificação de bytes, parser HTML SEFAZ-AM, validação da chave, números decimais e leitura opcional de MHTML. Recebe bytes/texto e devolve modelos de domínio; não acessa Room nem categorias.
- `notas/importacao`: detecta HTML/MHTML e encaminha o HTML extraído ao mesmo parser.
- `categorias/domain`: normalização, resolução de correspondências e categorizador. A descrição original fica inalterada.
- `categorias/data`: entidades, DAOs, Room e seeder JSON idempotente.
- `notas/data`: persistência transacional de nota, itens e pagamentos, além de ProductOverride solicitado pelo usuário.
- `MainViewModel` e `MainActivity`: seletor de documentos, revisão, correções, gerenciamento local e compartilhamento de exportação.

## Fluxo

1. `ActivityResultContracts.OpenDocument` entrega uma URI. O ViewModel lê os bytes via `ContentResolver` em memória, fora da thread principal.
2. O importador decodifica o documento e o parser extrai somente os dados do emitente, da NFC-e e dos itens necessários. Não interpreta CPF, nome/endereço do consumidor nem o comentário de origem do navegador.
3. O categorizador consulta primeiro ProductOverride, depois frases e palavras ativas. Um empate de mesma relevância entre categorias produz situação ambígua e exige revisão.
4. A tela permite corrigir a categoria e optar por guardar a descrição normalizada como ProductOverride.
5. A confirmação salva nota, itens, pagamentos e correções na mesma transação. A chave de acesso é única; duplicatas são recusadas.

## Correspondência

O normalizador aplica caixa alta, remoção de acentos e espaços consistentes apenas para comparação. Frases usam limites de palavra; termos do tipo PALAVRA são comparados a tokens completos. A prioridade desempata correspondências de mesma especificidade. Correspondência aproximada não está habilitada.

## Privacidade e armazenamento

O HTML não é persistido nem registrado. O banco guarda somente emitente/CNPJ, campos da nota, produtos, categorias, termos e correções. Valores e quantidades são persistidos como texto decimal sem conversão por ponto flutuante. A exportação JSON contém os campos de negócio e não inclui conteúdo bruto do HTML ou dados do consumidor.
