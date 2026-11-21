# Validação — 0.1.1

Versão compilada para Minecraft 1.21.1 / Java 21 / Fabric Loader 0.17.2 / Fabric API 0.116.6+1.21.1 / Cobblemon 1.8.3. Evidências atuais em `validation/v0.1.1`; evidências da versão anterior em `validation/v0.1.0`.

## Testes automatizados

18 testes JUnit, sem falhas ou skips: cotas/ciclo de 24 horas, consumo somente ativo, reentrada e retorno pendente, persistência atômica, configuração de área, template de mensagens, cores hexadecimais e negrito. Comando: `./gradlew test build remapIntegrationJar`.

## Servidor dedicado com Raids real

52 verificações com o mod de Raids 0.1.20-guard.1 e o motor real do Cobblemon. Incluem:

- Safari Ball nativa e vinculada ao jogador; inventário vazio; adições externas bloqueadas; coleta vanilla de itens autorizada somente dentro do escopo correto; loot preservado na saída.
- Bloqueio de comandos e namespaces, autenticação permitida, modo administrativo com revogação, menus protegidos, saldo após saída/reentrada.
- Barra real da Raid visível antes da visita, removida do cliente durante o Safari, atualizações da Raid suprimidas para esse jogador e ADD da barra atual restaurado na saída, mantendo associação no servidor.
- Barra do Safari e prioridade temporária do evento; retorno à barra normal após expiração.
- Comando de forma com namespace sem aspas; título e chat emitidos em pacotes reais.
- Timer real do PlayerSpawner em 2x/4x; shiny e forma regional em PokemonSpawnAction reais; evento exclui spawns fora da área. O contador de Pokémon só foi incrementado após a entidade entrar no mundo.
- Retorno com falha preservando origem, fallback seguro configurado, retirada automática por cota esgotada e por teleporte externo.

## Economia e LuckPerms reais

13 verificações com Impactor Fabric 5.3.5+1.21.1 e LuckPerms Fabric 5.4.140: cotas dos quatro grupos VIP, compra pelo fluxo de confirmação com débito real de 10.000, entrega de 32 bolas, saldo de bolas mantido na reentrada, insuficiência de fundos sem crédito nem saldo negativo, diagnóstico de moeda/saldo/motivo no chat, seleção de moeda com namespace, confirmação não reutilizável e pacote gratuito sem débito de valor zero.

## Reinício e limites

O segundo processo reutiliza o checkpoint do primeiro. A fixture simula interrupção após persistir visita e compra pendente, sem executar o encerramento gracioso do Safari. A recuperação usa o mesmo fluxo de retorno do mod. A evidência fica em `restart.json`.

Não houve cliente humano nem inspeção visual de tela: menus, barras, títulos e chat foram verificados no servidor e por captura dos pacotes enviados a jogadores sintéticos. Não houve conexão humana com EasyAuth/Guard. As verificações de Raids usam o JAR indicado, não garantem todas as versões futuras. A operação pendente é uma fixture sintética, não uma falha física injetada durante débito no Impactor. Não foi executada uma batalha/captura completa com interface de cliente nesta rodada. Os limites de população e condições do Cobblemon continuam valendo; 2x no timer não é uma promessa de exatamente 2x Pokémon na área.

Os JARs de integração são exclusivamente para o servidor descartável; não os instale em produção. O pacote principal não os contém. O SHA-256 do JAR final está junto das evidências e do release.
