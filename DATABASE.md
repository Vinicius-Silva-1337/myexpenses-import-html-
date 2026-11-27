# Banco de dados

O arquivo Room/SQLite continua se chamando `feira_nfce.db` para que uma atualização da versão Android anterior possa executar migrações e preservar notas já armazenadas. A versão Room atual é 4, sem fallback destrutivo.

## Entidades

| Tabela | Dados e garantias |
| --- | --- |
| `users` | Nome, e-mail único, hash, data e ID legado opcional. Uma conta de sistema temporária guarda ownership de dados NFC-e existentes até o primeiro cadastro. |
| `accounts` | `userId`, nome, tipo, saldo inicial em centavos e ID legado. Nome é único dentro do usuário; nomes que diferem apenas por caixa permanecem distintos. |
| `transactions` | `userId`, receita/despesa, valor em centavos, referência opcional à conta/categoria/subcategoria, rótulos legados, data, origem e observação. `sourceInvoiceId` é único e opcional. |
| `transfers` | Usuário, contas de origem/destino, centavos, data e observação. FKs impedem apagar contas ainda usadas. |
| `categorias` / `subcategories` | Categorias e subcategorias do usuário. Categoria inclui rótulo, nome normalizado, origem, ativa e ID legado. As mesmas categorias servem ao orçamento, à transação e à classificação de produtos. |
| `budgets` | Limite geral (`categoryKey='*'`) e limites por categoria, em centavos, únicos por usuário/chave. O plano preserva renda e percentual de economia. |
| `fixed_expenses` | Descrição, valor, categoria, conta e dia de vencimento. O pagamento cria transação com origem `fixa:<id>:AAAA-MM`. |
| `salary_settings` | Valor em centavos, dia, conta e ativo. O lançamento mensal usa origem `salario:AAAA-MM`. |
| `planning_settings` | Renda em centavos e percentual em centésimos (12,50% é armazenado como 1250). |
| `palavras_chave` / `product_overrides` | Termos, regras e correções associados a categoria e usuário. |
| `notas_fiscais` | Dados extraídos, validação, chave única por usuário e `userId`. |
| `itens_nota` / `pagamentos` | Produtos e pagamentos vinculados à nota por cascade; os itens preservam valores decimais como texto. |
| `legacy_imports` | Fingerprint SHA-256 do arquivo importado e relatório JSON para bloquear reimportação idêntica e permitir auditoria. |

As tabelas financeiras usam FKs para usuário, conta, categoria e nota onde aplicável. Itens e pagamentos herdam a propriedade da nota. Consultas de conteúdo privado recebem e filtram `userId`; relações categóricas do parser também consultam somente dados do usuário ativo.

## Dinheiro e cálculos

Todas as operações financeiras usam `Long` em centavos, sem `Float` ou `Double` no domínio. A entrada converte decimal BR ou ponto decimal com arredondamento explícito a duas casas. Notas fiscais mantêm BigDecimal textual para não perder escala. O percentual de economia usa duas casas percentuais e o orçamento resultante é calculado com decimal, arredondamento HALF_EVEN.

O banco Python guarda `REAL`, então a precisão lexical anterior não existe mais. O importador aplica `BigDecimal.valueOf(valorSQLite)`, arredonda HALF_UP para centavos, conta conversões com fração de centavo e lista essa contagem no relatório. Valores inválidos são ignorados com aviso.

## Migrações

- **1→2:** adiciona campos NFC-e opcionais já documentados no app do importador.
- **2→3:** cria usuários, converte dados NFC-e instalados para ownership temporário e adiciona as tabelas financeiras, com índices e unicidades.
- **3→4:** adiciona IDs legados e tabela de importações.

O primeiro cadastro reivindica as notas e regras anteriores guardadas sob o proprietário temporário. As migrações Room e o importador Python preservam as fontes; não há `fallbackToDestructiveMigration`.

## Importação do banco Python

Em Perfil, escolha o arquivo `banco.db` extraído do ZIP do projeto antigo. O app copia os bytes temporariamente para cache, calcula SHA-256, confirma `PRAGMA integrity_check` e valida as tabelas necessárias. A conexão de origem é aberta somente para leitura e o arquivo temporário é removido após a migração.

Usuários são associados por e-mail quando já existem; caso contrário, são criados com o hash Werkzeug original. O login verifica scrypt `N/r/p` e PBKDF2-SHA256 legado; após login correto grava um hash local PBKDF2-HMAC-SHA256 e apaga a senha da memória. IDs de origem são guardados como `legacyId` quando há um campo correspondente. Contas e categorias são resolvidas sem perder o texto original; referências sem correspondência ficam registradas ou são reparadas criando conta/categoria e descritas no relatório.

O relatório informa contagens importadas/ignoradas, conflitos, correções de referências e valores arredondados. Arquivos com o mesmo fingerprint não são importados novamente. O banco Python original nunca é modificado.

## Nota fiscal e despesa

Importar e revisar uma nota não cria transação financeira. A opção explícita de registro cria uma única transação de despesa vinculada ao ID da nota. A unicidade de `transactions.sourceInvoiceId`, verificação do `userId` e transação Room impedem duplicar o efeito no saldo. Se uma nota for excluída depois, a transação preserva o valor e segue como despesa sem nota vinculada.
