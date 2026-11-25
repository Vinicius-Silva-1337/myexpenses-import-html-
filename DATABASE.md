# Banco de dados

O banco local Room/SQLite chama-se `feira_nfce.db`, versão inicial 1. A criação é feita sem estratégia destrutiva de fallback. Quando o esquema mudar, adicionar uma migração Room explícita e preservar as tabelas existentes.

## Entidades

| Tabela | Chave e restrições | Campos principais |
| --- | --- | --- |
| `categorias` | PK `id`; índice único em `nomeNormalizado` (maiúsculas) | nome, nome normalizado, descrição opcional, origem PADRAO/PERSONALIZADA, ativa |
| `palavras_chave` | FK `categoriaId` → categorias com cascade; único (`termoNormalizado`, `categoriaId`) | termo original/normalizado, prioridade, tipo, origem BASE/USUARIO, ativa |
| `product_overrides` | FK categoria com RESTRICT; índice único em `descricaoNormalizada` | descrição original/normalizada, categoria, instante de criação |
| `notas_fiscais` | PK `id`; chave de acesso única | CNPJ e emitente, número/série/data, total, indicadores de validação e instante de importação |
| `itens_nota` | FK para nota com cascade; FK de categoria com SET NULL | descrição, código, quantidade/unidade, preços, categoria sugerida e origem |
| `pagamentos` | FK para nota com cascade | forma e valor |

`BigDecimal` é gravado como texto decimal para preservar escala e evitar `Float`/`Double`. A chave NFC-e tem unicidade no SQLite, além da verificação antes da inserção transacional.

## Inicialização e migrações

`CategoriaSeeder` insere categorias ausentes e lê `assets/palavras_chave.json`; índices únicos e `INSERT IGNORE` evitam duplicação. O seeder não apaga nem substitui categorias, palavras ou ProductOverrides existentes. Categorias padrão não podem ser renomeadas ou desativadas pelo DAO de edição; categorias personalizadas podem ser renomeadas/desativadas. Se produtos usam a categoria personalizada, a tela exige escolher outra categoria e move produtos, palavras e correções aprendidas na mesma transação antes de desativá-la.

A versão 1 é o primeiro esquema deste diretório. Não há dados anteriores neste app vazio para migrar. Próximas mudanças devem incrementar a versão e registrar scripts de migração que preservem dados do usuário.
