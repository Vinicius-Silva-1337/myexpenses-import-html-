# Arquitetura

O aplicativo único se chama **My Expenses**. Ele concentra dados financeiros e fiscais no dispositivo, com interface Compose/Material 3 e Room. Não usa Flask, WebView ou servidor.

## Módulos

- `auth`: cadastro, login, logout e sessão local. Hashes novos usam PBKDF2-HMAC-SHA256 com salt aleatório e 310.000 iterações. No primeiro login correto, hashes Werkzeug scrypt ou PBKDF2 aceitos são atualizados para o formato local.
- `financeiro/data`: entidades e DAOs Room para usuários, contas, transações, transferências, categorias/subcategorias, limites, salário, planejamento e despesas fixas.
- `financeiro/domain`: regras de negócio, saldo e relatórios. Valores financeiros são centavos (`Long`); percentuais de economia usam centésimos de percentual.
- `financeiro/presentation`: ViewModel com Flow e telas Compose para dashboard, transações, contas, planejamento, relatórios e categorias.
- `notas/parser`: decodificação de bytes, parser HTML SEFAZ-AM, validação da chave, números decimais e leitura opcional de MHTML. Recebe bytes/texto e devolve modelos de domínio; não acessa Room nem categorias.
- `notas/importacao`: detecta HTML/MHTML e encaminha o HTML extraído ao mesmo parser.
- `categorias/domain`: normalização, resolução de correspondências e categorizador. A descrição original fica inalterada.
- `categorias/data`: entidades, DAOs, Room e seeder JSON idempotente.
- `notas/data`: persistência transacional de nota, itens e pagamentos, além de ProductOverride solicitado pelo usuário.
- `MainViewModel` e `MainActivity`: navegação Compose, seletor de documentos, revisão, QR, histórico fiscal, correções e compartilhamento JSON.
- `LegacyDatabaseImporter`: recebe uma cópia SQLite escolhida pelo seletor de arquivos, valida sua integridade e a lê em modo somente leitura. O arquivo legado não é apagado nem atualizado.

## Fluxo

1. `ActivityResultContracts.OpenDocument` entrega uma URI. O ViewModel lê os bytes via `ContentResolver` em memória, fora da thread principal.
2. O importador decodifica o documento e o parser extrai somente os dados do emitente, da NFC-e e dos itens necessários. Não interpreta CPF, nome/endereço do consumidor nem o comentário de origem do navegador.
3. O categorizador consulta ProductOverride do usuário, depois frases e palavras ativas do mesmo usuário. Um empate de mesma relevância entre categorias produz situação ambígua e exige revisão.
4. A tela permite corrigir a categoria e optar por guardar a descrição normalizada como ProductOverride.
5. A confirmação salva nota, itens, pagamentos e correções na mesma transação. A chave de acesso é única por usuário; duplicatas são recusadas.
6. Importar uma nota não cria transação. A ação explícita **Registrar despesa** cria uma transação com `sourceInvoiceId` único e valida que a nota pertence ao usuário autenticado.

## Usuários e isolamento

O `userId` autenticado é passado nas consultas e operações financeiras, categorias, palavras-chave, correções e notas. Os DAOs filtram pelo usuário; as entidades financeiras também têm chaves estrangeiras. Usuários e dados permanecem locais. A sessão guarda somente o ID do usuário em preferências privadas do aplicativo.

O registro inicial assume dados NFC-e de uma instalação anterior sob um proprietário temporário e transfere esses dados ao primeiro usuário local. Os usuários e notas seguintes ficam isolados por `userId`.

## Correspondência

O normalizador aplica caixa alta, remoção de acentos e espaços consistentes apenas para comparação. Frases usam limites de palavra; termos do tipo PALAVRA são comparados a tokens completos. A prioridade desempata correspondências de mesma especificidade. Correspondência aproximada não está habilitada.

## Rede, privacidade e armazenamento

O HTML não é persistido nem registrado. O banco guarda somente emitente/CNPJ, campos da nota, produtos, categorias, termos e correções. Valores/quantidades fiscais são texto decimal; valores financeiros são centavos inteiros.

O leitor de QR usa ML Kit e `SefazNfceClient` para consultar a SEFAZ-AM. Essa função precisa de `android.permission.INTERNET`, OkHttp e regras de segurança de rede no manifesto. O cliente limita tamanho, tempo e redirecionamentos autorizados; consultas não são necessárias para importar arquivos HTML/MHTML.

A senha nunca é armazenada em texto puro. Os dados SQLite não têm criptografia adicional por chave; ficam no armazenamento privado do app e usam controle de acesso por usuário na camada de aplicação.
