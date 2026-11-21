# Validação

Ambiente de teste isolado, Minecraft 1.21.1, Java 21, Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1, Cobblemon 1.8.3. Integrações adicionais: Impactor Fabric 5.3.5+1.21.1 e LuckPerms Fabric 5.4.140.

`./gradlew test build remapIntegrationJar` compila o mod, executa os testes JUnit e gera uma fixture separada. Os resultados finais estão em `validation/`.

Os testes JUnit cobrem fronteiras do ciclo de 24 horas, consumo somente ativo, reentrada sem recarga de bolas, retorno pendente bloqueando renovação, cotas VIP, limites da área, validação da saída, persistência atômica e arquivo malformado preservado.

O servidor dedicado usa FakePlayers e o motor real do Cobblemon. Foram exercitados inventário vazio, itens externos, comandos e namespaces, modo administrativo e revogação, estrutura/cliques do menu, saldo de bolas/tempo após saída/reentrada, timer real do PlayerSpawner, PokemonSpawnAction com evento shiny/forma regional, exclusão de spawns fora da área e falha/repetição de retorno. Um segundo processo carrega o estado persistido do primeiro para testar recuperação após interrupção e revisão de compra pendente sintética.

Outro servidor dedicado usa LuckPerms e Impactor reais: quatro grupos com cotas progressivas, débito do preço configurado, entrega de 32 bolas, reentrada mantendo bolas compradas e saldo insuficiente sem bolas ou saldo negativo.

Limites: não houve cliente humano nem teste visual de interface; FakePlayer não representa conexão/login real. A reconexão é validada pelo carregamento de estado em outro processo e pelo mesmo fluxo de recuperação chamado na conexão; não por uma conta humana autenticando. A compra interrompida é uma fixture sintética de journal, não uma falha injetada no banco do Impactor durante um débito real. Não foi certificada a interação com EasyAuth, Guard ou todos os outros mods do servidor de produção. A batalha/captura completa com cliente não foi executada nesta validação; o mod usa Pokébolas e entidades normais do Cobblemon. Teste esse conjunto em staging antes da instalação definitiva.

O checkpoint de interrupção é criado desativando o encerramento gracioso do mod na fixture, seguido de reinicialização real do processo; não é um teste de perda física de energia/disco.

O build de integração foi seguido apenas por formatação dos fontes e recompilação, com os 15 testes JUnit aprovados novamente. Os hashes do JAR exercitado no servidor e do JAR final estão registrados separadamente em validation/.
