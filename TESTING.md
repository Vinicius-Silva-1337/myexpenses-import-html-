# Testes

O projeto separa testes puros do parser e do categorizador dos testes de integração do Room. As fixtures do parser são HTMLs sintéticos: não carregam CPF, endereço ou outros dados de consumidores.

## Executar

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Parser e regras de correspondência rodam em JVM com JUnit. Os cenários Room usam banco em memória em testes instrumentados.

## Parser

`ParserAmTest` cobre leitura dos seletores, descrição e entidades HTML, códigos, unidades, quantidades fracionadas, escala decimal variável, divergência de total, chave módulo 11, contagem dinâmica até 300 itens, erro com número da linha, UTF-8/Windows-1252 e MHTML quoted-printable/base64.

Para teste manual, abra o app, toque em **Importar nota**, escolha um HTML/MHTML local e revise os itens e avisos antes de confirmar. O arquivo é lido pelo `ContentResolver` em memória e não é copiado para o armazenamento do app.

## Room, categorias e categorizador

`CategorizadorRulesTest` cobre normalização, limites de palavra, frases, prioridade, colisões e categorias de segmentos distintos. `CategoriaDatabaseTest` cria o banco Room em memória e verifica inicialização idempotente, preservação de categoria personalizada, substituição/exclusão de ProductOverride e persistência do fluxo nota → revisão → correção aprendida → nova consulta. `NotaPersistenciaTest` verifica persistência após fechar/reabrir o banco, duplicidade pela chave, gravação de vários produtos/pagamentos, exclusão em cascata e preservação de categorias, palavras e correções aprendidas.

Para compilar os testes instrumentados, use `./gradlew assembleDebugAndroidTest`. Com emulador ou dispositivo conectado, execute `./gradlew connectedDebugAndroidTest`.

## Emulador e aparelho

Para testar a interface, instale a variante debug em um emulador ou aparelho Android. Escolha um HTML/MHTML pelo seletor de documentos, altere uma categoria, marque se a correção deve ser lembrada e confirme. Feche e reabra o app para verificar o histórico. A área **Categorias** permite criar/renomear/desativar categorias personalizadas, administrar palavras e excluir correções aprendidas. A exportação do histórico usa a ação de compartilhamento do Android.

O parser e o categorizador não usam conexão de rede. O manifesto não declara a permissão `INTERNET`.
