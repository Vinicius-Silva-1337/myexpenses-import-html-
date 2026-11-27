# Testes

O projeto separa testes puros do parser e do categorizador dos testes de integração do Room. As fixtures do parser são HTMLs sintéticos: não carregam CPF, endereço ou outros dados de consumidores.

## Executar

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

Parser, categorização, conversão de centavos, validação de hashes Werkzeug e PBKDF2 rodam em JVM com JUnit. Os cenários Room usam banco em memória em testes instrumentados.

## Parser

`ParserAmTest` cobre leitura dos seletores, descrição e entidades HTML, códigos, unidades, quantidades fracionadas, escala decimal variável, divergência de total, chave módulo 11, contagem dinâmica até 300 itens, erro com número da linha, UTF-8/Windows-1252 e MHTML quoted-printable/base64.

Para teste manual, abra o app, toque em **Importar nota**, escolha um HTML/MHTML local e revise os itens e avisos antes de confirmar. O arquivo é lido pelo `ContentResolver` em memória e não é copiado para o armazenamento do app.

## Room, categorias e categorizador

`CategorizadorRulesTest` cobre normalização, limites de palavra, frases, prioridade, colisões e categorias de segmentos distintos. `CategoriaDatabaseTest` verifica o seeder e o fluxo nota → revisão → correção aprendida → nova consulta. `NotaPersistenciaTest` verifica persistência após reabrir, duplicidade, produtos/pagamentos, exclusão em cascata e preservação do aprendizado.

`FinanceRepositoryTest` cobre isolamento entre usuários, centavos, saldo por conta, transferências, salário idempotente, pagamento de despesa fixa e planejamento/relatórios. `RoomMigrationTest` parte do schema 2 com dados NFC-e existentes e valida a migração até a versão 4 sem apagar registros. `LegacyDatabaseImporterTest` importa um SQLite legado de fixture, verifica arredondamento HALF_UP, preservação do arquivo e bloqueio de nova importação pelo fingerprint.

Para compilar os testes instrumentados, use `./gradlew assembleDebugAndroidTest`. Com emulador ou dispositivo conectado, execute `./gradlew connectedDebugAndroidTest`.

## Emulador e aparelho

Para testar a interface, instale a variante debug em um aparelho. Crie dois usuários e confira que cada um vê somente suas contas, lançamentos, categorias, notas e correções. Os fluxos de teste manual incluem editar uma transação, transferir saldo, pagar uma despesa fixa, registrar a mesma nota duas vezes como despesa (a segunda deve ser recusada), importar `banco.db` e revisar o relatório de migração.

Importação local de HTML/MHTML e categorização funcionam offline. O scanner QR consulta diretamente a SEFAZ-AM e usa `INTERNET`/OkHttp; pode falhar sem rede ou se a SEFAZ estiver indisponível.
