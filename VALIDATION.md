# Validação — 0.1.4

28 testes unitários e 85 verificações em servidor dedicado passaram. Evidências em `validation/v0.1.4`, incluindo hash do Trainers 0.3.2 real usado, sem modificações. As novas verificações incluem criação/duplicidade de região, teleporte por comando e menu, recusa fora de visita, obstrução de destino, menu preservado no tick, shift-click sem extração de ícones e preservação de origem/ciclo/bolas/compra.

O Trainers real criou o NPC guia, salvou a ação `player safari regioes`, e seu callback UseEntity abriu RegionsMenu para ServerPlayer sem OP. A ponte reconheceu também a ação de vendedor e recusou ação não autorizada; o clique de compra completo já foi validado no fluxo Safari anterior, não foi repetido como clique no NPC vendedor nesta rodada. Foram usados jogadores nativos simulados e captura de pacotes, sem cliente humano nem inspeção visual. O Clear não mudou. Não houve nova rodada completa de economia/reinício; o histórico abaixo registra essas validações anteriores.

# Histórico — 0.1.3

23 testes unitários Safari e 68 verificações em servidor dedicado passaram. Evidências em `validation/v0.1.3`. As 7 novas verificações usam o comando real `/safariadmin delete`: recusa com visitante ativo, recusa com retorno pendente, execução administrativa, remoção de área/entrada, persistência em disco, preservação do ciclo/bolas/lobby e recusa quando não há área. O JAR Clear é idêntico ao 0.1.2 abaixo. Não houve cliente humano; foram usados ServerPlayers nativos e captura de pacotes.

# Histórico — 0.1.2

Minecraft 1.21.1, Java 21, Fabric Loader 0.18.3, Fabric API 0.116.18+1.21.1, Cobblemon 1.8.3. Os dois JARs finais e seus SHA-256 acompanham `validation/v0.1.2`. Histórico preservado nas pastas anteriores.

## Testes unitários

23 testes Safari e 4 testes Clear, sem falhas ou skips. Comandos nos respectivos projetos: `./gradlew test build remapIntegrationJar`. Cobrem o ciclo compartilhado, compra única paga/gratuita, pagamento tardio, renovação bloqueada por transação pendente, persistência, mensagens e migração do Clear preservando a configuração global.

## Servidores dedicados

- 61 verificações Safari com Raids 0.1.20-guard.1 real: isolamento do inventário, coleta de itens, comandos, HUD de Raid/Safari/evento, cota e retorno, spawn/shiny/forma no motor Cobblemon, menus vanilla e bloqueio das duas rotas de reembolso de projéteis Safari. A marca de uso único é aplicada ao construir a bola, antes do callback de entrada no mundo. Bolas fora da visita mantêm o comportamento original.
- 16 verificações com Impactor 5.3.5 e LuckPerms 5.4.140 reais: grupos VIP, débito e entrega, recusa sem consumir compra, compra única após reentrada e renovação sincronizada com a entrada; pacote gratuito também consome a única compra do ciclo.
- 22 verificações Clear: dimensão real `multiworld:safari`, cuboide com altura, intervalo regional de 2.400 ticks, contador global independente, mensagens privadas por localização, filtros originais de proteção, rechecagem de entidade que sai da área e orçamento de processamento compartilhado sem impedir uma das filas de avançar.
- Um segundo processo Safari reutiliza o checkpoint do primeiro, verificando retorno e revisão única de pagamento pendente após interrupção simulada.

## Limites da validação

Não houve cliente humano. Foram usados ServerPlayers nativos e captura dos pacotes de menus, barras, títulos e chat. O menu permaneceu aberto depois da saída mesmo com Safari Ball comum no inventário; tentar entrar sem esvaziá-lo manteve o menu e recusou a entrada. Isso não reproduz todas as interferências possíveis de outros mods no cliente do usuário.

A dimensão customizada foi criada por datapack, com a mesma chave ResourceKey usada por mundos Multiworld; não foi testado o ciclo de criação/remoção do plugin Multiworld. Os relógios do Clear foram avançados por chamadas de tick no servidor de testes, sem aguardar cada intervalo em tempo real. Um item remoto da fixture, sem cliente para reconhecer o chunk, foi registrado explicitamente no método original idempotente de ENTITY_LOAD antes da limpeza global. As demais entidades locais usam a entrada real no mundo. Somente chunks carregados entram na limpeza.

A interrupção de compra é um checkpoint sintético, não uma falha física durante débito Impactor. Não foi realizada uma captura/batalha completa com interface de cliente nesta rodada. Os limites populacionais do Cobblemon continuam valendo; multiplicar o timer não garante quantidade exata de Pokémon.

A adaptação Clear conserva byte a byte todas as entradas originais fora das classes alteradas e do número da versão: veja `clear-byte-preservation.json`, incluindo Eligibility, BattleGuard, LoadedIndex, ProtectionApi, mixins/refmap e dependência aninhada. Não instale os JARs de integração em produção.
