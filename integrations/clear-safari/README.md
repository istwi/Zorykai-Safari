# Zorykai Clear — adaptação Safari

Base: `Shirokai-Clear-1.0.0-Zorykai-Visual.jar`, enviado pelo usuário. O projeto reconstrói somente as partes necessárias para separar o escopo global e o Safari. Os IDs, o arquivo `config/shirokaiclear.json`, as permissões e as mensagens globais existentes são preservados.

A área e a dimensão são lidas do Shirokai/Zorykai Safari instalado. Funciona com uma dimensão Multiworld por sua chave real, sem dependência da criação de mundos pelo Multiworld. Fora desse cuboide o intervalo global não muda. Dentro dele, há um contador padrão de 120 segundos para itens e Pokémon selvagens elegíveis. O resultado local e os avisos só chegam a ocupantes físicos desse cuboide; mensagens globais excluem esses ocupantes.

O novo bloco `safari` é acrescentado sem substituir os campos anteriores. Ele permite intervalo, tipos, idades mínimas e mensagens locais. As proteções/filtros de Pokémon e itens são herdados da configuração global, inclusive shiny/lendário, dono, batalha, NPC e raid. As exclusões globais de dimensão não são aplicadas à rotina local; a área define seu próprio escopo. Sem Safari instalado/configurado ou com `safari.enabled=false`, o Clear mantém o escopo global original. Uma API de área incompatível suspende a limpeza para evitar aplicar o escopo errado.

`/shirokaiclear status`, `reload`, `pause`, `resume`, `now`, `items`, `pokemon` e `preview` mantêm a permissão `shirokaiclear.admin`. Limpezas manuais globais também excluem a área reservada ao Safari. O painel administrativo do Safari consulta o contador local pela nova API somente de leitura.

Compilar com Java 21: `./gradlew test build`. O arquivo de distribuição é **`build/libs/Shirokai-Clear-1.0.1-Zorykai-Safari.jar`**, gerado por `patchedJar`. Não use os JARs intermediários de compilação. O task compara os arquivos de entrada para regenerar o patch quando o código muda.

O empacotamento parte do JAR original e substitui `ClearConfig*`, `ClearService*` e `ShirokaiClear`; acrescenta `SafariArea` e `LocalText`. Todos os outros arquivos, incluindo `Eligibility`, `BattleGuard`, `LoadedIndex`, `ProtectionApi`, mixins, refmap e dependência aninhada, conservam o conteúdo original. A versão do metadata é atualizada. Veja a prova de hashes nas evidências do release.

Requer os mesmos mínimos do JAR original: Minecraft 1.21.1, Java 21, Fabric Loader 0.18.3, Fabric API 0.116.7+1.21.1 e Cobblemon 1.8.3. Não há alteração no mod Multiworld nem carregamento de chunks para a limpeza.

A fixture de integração usa uma dimensão real `multiworld:safari`, criada por datapack em servidor descartável. Não instala nem testa o plugin Multiworld em si. Um item num chunk remoto é registrado explicitamente pelo mesmo sink idempotente `loaded` usado pelo evento para testar o processamento fora da área sem depender de acknowledgements de um cliente humano. Os demais testes incluem ciclo local, contador global, proteções e destinatários dos pacotes. Os JARs de teste não são para produção.
