# Zorykai Safari 0.2.0

[Baixar Safari + Clear ZIP](releases/v0.2.0/Zorykai-Safari-0.2.0-e-Clear.zip) · [JAR Safari](releases/v0.2.0/Shirokai-Safari-0.2.0.jar) · [JAR Clear](releases/v0.2.0/Shirokai-Clear-1.0.1-Zorykai-Safari.jar) · [Validação](VALIDATION.md) · [JSON de mensagens](examples/shirokai-safari-messages.json)

Os identificadores técnicos e nomes dos arquivos usam `shirokai_safari` / `shirokai-safari` para manter consistência com o projeto.

Mod exclusivo de servidor: Minecraft 1.21.1, Java 21, Fabric Loader 0.17.2+, Fabric API e Cobblemon 1.8.3. Para VIPs por grupo: LuckPerms. Para comprar bolas: Impactor 5.3.5, moeda `impactor:dollars` por padrão. Não instala nada no cliente.

## Regras do ciclo

A primeira entrada inicia uma janela de 24 horas. O saldo diminui somente durante a visita: sair ou desconectar pausa o saldo. Entrar novamente antes do fim da janela retoma o mesmo saldo e as mesmas bolas. Ao completar 24 horas, o ciclo pode ser renovado na próxima entrada, sem acumular o saldo antigo. Se a janela terminar com o jogador dentro, ele é retirado e pode iniciar o novo ciclo entrando novamente.

| Jogador/grupo | Saldo por ciclo |
| --- | --- |
| Normal | 1 hora |
| magma | 2 horas |
| aqua | 2 horas e 24 minutos |
| plasma | 2 horas, 52 minutos e 48 segundos |
| galactic | 3 horas, 27 minutos e 22 segundos |

São aumentos de 20% sobre o cargo anterior, arredondados para segundos. Quem tiver vários grupos usa o maior saldo. Os grupos herdados e contextos do LuckPerms são consultados na criação do ciclo; o cargo não altera retroativamente a cota. É possível usar também `shirokai.safari.vip.<grupo>`. Tudo fica configurável.

As 32 Safari Balls iniciais são entregues no início do ciclo. Se toda a cota de tempo acabar, as sobras são apagadas; receber tempo extra depois disso concede um novo pacote inicial uma única vez para aquele saldo extra. São **Safari Balls reais do Cobblemon** (`cobblemon:safari_ball`), vinculadas ao jogador e ciclo. A atualização migra automaticamente o item antigo `poke_ball` para Safari Ball, preservando o saldo numérico e o ciclo. Bolas não usadas ficam no saldo ao sair e são devolvidas na reentrada enquanto houver tempo disponível no ciclo. Batalhas e captura usam o motor normal do Cobblemon. A captura usa as regras nativas da Safari Ball do Cobblemon, sem uma nova fórmula personalizada.

## Instalação e seleção

1. Com o servidor desligado e backup feito, coloque apenas o JAR principal em `mods`. Nunca instale o JAR de testes.
2. Inicie. A configuração será criada em `config/shirokai-safari.json`.
3. Como administrador, selecione os dois cantos, incluindo a altura desejada:
   - `/safariadmin pos1`
   - `/safariadmin pos2`
   - `/safariadmin criar`
4. Dentro da área, em um ponto seguro com piso e espaço livre: `/safariadmin entrada`.
5. Fora da área, em um ponto seguro para servir de saída de emergência: `/safariadmin saida`.
6. Configure o preço do pacote: `/safariadmin preco 10000`.

Criar a área não gera construções, dimensões, biomas nem uma lista própria de espécies: utiliza os spawns naturais já definidos para o local. A entrada exige inventário, armadura, mão secundária e cursor vazios, sobrevivência, ausência de batalha e veículo.

**Apenas selecionar/criar a área não aumenta o spawn.** O efeito exige uma visita ativa por `/safari entrar`, com o visitante dentro da área. O spawn padrão é 2× a frequência do timer natural do spawner de cada visitante. A área de cálculo desse spawner é recortada para o Safari. Não promete exatamente o dobro de Pokémon existentes: limites de população, bioma, horário e condições do Cobblemon continuam valendo. Não muda a taxa global do servidor.

## Comandos do jogador

- `/safari`: somente entrada/continuação, tempo disponível/renovação e saída.
- `/safari entrar`: inicia ou retoma uma visita.
- `/safari sair`: retorna à origem e pausa o saldo.
- `/safari tempo`: informa saldo, renovação, bolas e evento.
- `/safari bolas`: equipa uma pilha de Safari Balls na mão, preservando o item deslocado.
- `/safari saldo`: consulta o saldo na moeda realmente usada pela loja.
- `/safari eventos`: consulta efeitos e prazo do evento ativo.
- `/safari comprar`: mostra o preço e solicita confirmação por 30 segundos.
- `/safari comprar confirmar`: compra outro pacote de 32. Quantidade e preço são configuráveis. Mudança de preço/moeda invalida a confirmação anterior.

A compra abre um menu de confirmação e um botão clicável no chat, válidos por 30 segundos. Preço 0 concede um pacote gratuito sem enviar um débito de valor zero ao Impactor. Não há entrada paga; o preço é apenas do pacote adicional. **Só é permitida uma compra adicional por ciclo**, inclusive quando o preço é zero. Pagamento recusado não consome a compra; uma operação incerta bloqueia novas tentativas até conferência. Sair, reconectar ou ganhar bônus de tempo não reinicia esse limite. A compra e a visita compartilham `cycleStart`/`cycleEnd`: renovam juntas na primeira entrada do próximo ciclo, sem um cooldown separado contado desde a compra.

## Proteções

Durante uma visita ou retorno pendente, comandos externos são bloqueados, incluindo home, sethome, TPA, tpaccept, spawn, enderchest e wrappers como execute. As exceções são os comandos do Safari e `/login`, `/l` (incluindo as variantes com namespace easyauth), para não bloquear autenticação. Aliases externos não são autorizados automaticamente.

Interações com blocos/containers, construção, troca de itens por cliques e troca de item segurado/cosmético dos Pokémon continuam bloqueadas. **Coletar itens do chão é permitido**, inclusive drops de Pokémon. A autorização fica limitada à coleta real; outras adições externas pela API normal continuam bloqueadas. Os itens recolhidos permanecem no inventário ao sair e precisam ser guardados antes da próxima entrada. Bolas externas e consumíveis de batalha recolhidos não podem ser usados dentro do Safari; as Safari Balls emitidas pelo mod são equipadas automaticamente na compra. O inventário vanilla recusa adição de itens externos pela API normal; não é um isolamento contra outros mods que deliberadamente escrevam diretamente nos slots. Os Pokémon da equipe não são substituídos nem curados. Não há fome durante a visita.

Quem tentar entrar fisicamente sem visita é retirado para a saída configurada. Um teleporte externo durante uma visita encerra a visita e processa o retorno no próximo ciclo de verificação, até aproximadamente um segundo. Administradores podem atuar fora da visita para construir e configurar a área.

### Administração

Permissão `shirokai.safari.admin`; fallback OP nível 2 se não houver decisão explícita do LuckPerms. O console também pode administrar as operações sem posição pessoal.

- `/safariadmin` ou `/safariadmin menu`: abre o painel administrativo de consulta, sem mudar configurações ao clicar. Mostra área, destinos, visitantes, evento e prazo, multiplicador base/temporário/total, amostras reais do timer e tentativas/Pokémon gerados nos últimos 60 segundos. As métricas são de execução, reiniciadas com o servidor. Sem visitante ativo pode não haver amostra. O painel é atualizado automaticamente e pode ser atualizado por clique; não ativa nem desativa eventos ao clicar nos indicadores. Os contadores de geração só aumentam quando uma entidade de spawn natural elegível entra no mundo; criar uma entidade que depois não é adicionada não conta.
- `/safariadmin ajuda`: lista os comandos completos no chat com clique para preencher, incluindo saída e eventos.
- `/safariadmin economia <jogador_online>`: consulta moeda, saldo e preço do pacote para diagnosticar pagamento recusado.
- `/safariadmin moeda impactor:dollars`: escolhe uma moeda que exista no Impactor; não há troca automática de moeda em caso de saldo insuficiente.
- `/safariadmin mensagens`: recarrega apenas as mensagens, inclusive com visitantes ativos. Um JSON inválido é rejeitado e as mensagens anteriores continuam em uso.
- `/safariadmin modo on` e `/safariadmin modo off`: libera comandos na sessão administrativa, sem conceder OP ou permissões de outros mods. Desativa ao desconectar e revalida a permissão em cada comando. Não desliga as proteções de itens.
- `/safariadmin tempo <jogador_online> <minutos>`: acrescenta tempo ao ciclo vigente.
- `/safariadmin reload`: recarrega configuração validada; exige ausência de visitas e retornos pendentes.
- `/safariadmin preco <valor>`: altera o preço sem reiniciar, usando ponto decimal.

Exemplo LuckPerms: `/lp user SEUNICK permission set shirokai.safari.admin true`.

O acesso normal usa `shirokai.safari.use`, permitido por padrão quando não há decisão explícita. Uma negativa explícita bloqueia a entrada/comandos de acesso.

## Eventos

Os eventos são persistidos e terminam pelo horário real, inclusive se o servidor reiniciar. Alteram somente novas gerações naturais do PlayerSpawner de um visitante dentro da área; não alteram Pokémon de jogadores, NPCs, raids, Pokémon já existentes ou spawns por comando.

- `/safariadmin evento spawn 2 60`: por 60 minutos, aplica outro fator 2 sobre o padrão 2×, resultando em 4×, limitado a 10× no total.
- `/safariadmin evento shiny 1 60`: por 60 minutos, dá uma chance adicional de 1% de tornar shiny cada spawn elegível. Não remove shinies naturais; a chance final é a composição com a chance natural, não exatamente 1%.
- `/safariadmin evento forma cobblemon:rattata alolan 30 60`: por 60 minutos, 30% dos Rattata naturalmente gerados no Safari recebem a forma Alola. Aceita o nome da forma ou aspecto registrado. A espécie precisa aparecer naturalmente no local; o evento não cria uma espécie que não esteja na tabela. Formas inexistentes são recusadas.
- `/safariadmin evento tempo 30 60`: acrescenta 30 minutos aos ciclos vigentes de todos e dá esse bônus aos novos ciclos iniciados durante os próximos 60 minutos. O mesmo ciclo não recebe o bônus novamente só por sair e entrar. Executar o comando de novo é uma nova concessão administrativa.
- `/safariadmin evento status`: mostra se o evento está ativo, seus efeitos e o tempo restante. Ativar um evento produz confirmação e anúncio.
- `/safariadmin evento parar`: encerra os efeitos temporários de spawn/shiny/forma e o bônus para novos ciclos. Tempo já concedido não é retirado.

Os efeitos podem coexistir. Configurar um efeito redefine o prazo comum do evento; não há agendas independentes. Para encerrar só uma chance, configure-a como 0; para spawn, use 1.


## Boss bar e anúncios editáveis

Durante a visita, a boss bar mostra o saldo de tempo, as Safari Balls e o fator de spawn. Se houver evento ativo, a mesma barra passa a mostrar os efeitos e o tempo do evento; ao expirar, volta ao Safari normal. Se a janela diária terminar antes do saldo, o tempo disponível da barra considera esse limite.

Com o Shirokai Raids 0.1.20-guard.1 instalado, as barras nativas da Raid são ocultadas **somente para o visitante**. Na saída, a barra atual da Raid é restaurada. Nenhuma participação na Raid ou barra de outro jogador é alterada. Essa integração visual é opcional: se uma versão futura mudar a estrutura do Raids, o Safari mantém as barras externas e registra o motivo, em vez de quebrar o servidor.

Mensagens: `config/shirokai-safari-messages.json`, criado na primeira inicialização. Inclui `chatEnabled`, `titlesEnabled`, `audience` (`ALL` ou `SAFARI`), linhas de início/fim, título/subtítulo, tempos de exibição e os textos `safariBar`/`eventBar`.

Aceita cores `&a`, `&6`, negrito `&l`, reset `&r` e hexadecimal `&#FFAA00`. Para degradê, use códigos hex por letra; tags MiniMessage não são interpretadas.

Campos dos anúncios/evento: `{evento}`, `{duracao}`, `{tempo_evento}`, `{spawn}`, `{shiny}`, `{formas}`, `{bonus_tempo}`. A barra normal aceita também `{tempo}` e `{bolas}`. O público padrão dos anúncios de início/fim é todo o servidor; a boss bar aparece somente para visitantes.

Exemplo de título: `"eventStartTitle": "&2&lEVENTO SAFARI"`. Após editar: `/safariadmin mensagens`.

Se a loja recusar o pagamento, use `/safari saldo` ou `/safariadmin economia NomeDoPlayer`. A mensagem informa o resultado do provedor (por exemplo, saldo insuficiente), a moeda, o saldo e o preço. Compare com a carteira que o servidor usa antes de trocar a moeda; o Safari não desconta de outra carteira por tentativa.

## Limpeza independente na área/dimensão do Safari

Instale o **Clear adaptado incluído neste release**, substituindo o Clear anterior. Não deixe dois JARs do mesmo mod em `mods`. O Safari 0.2.0 também deve substituir seu JAR anterior. Os dois arquivos mantêm os IDs técnicos e as configurações existentes.

A seção `safari` é acrescentada automaticamente a `config/shirokaiclear.json`, sem substituir o intervalo, os avisos ou os filtros globais que você já configurou. Por padrão:

- Fora da área do Safari: continua o intervalo global existente.
- Dentro da dimensão e cuboide selecionados no Safari: itens no chão e Pokémon selvagens elegíveis são limpos a cada 120 segundos do relógio do Clear (2.400 ticks a 20 TPS).
- Avisos e resultados locais só são enviados a quem está fisicamente dentro dessa área, mesmo que não tenha visita ativa. Os avisos/resultados globais deixam de ser enviados a esses ocupantes.
- A dimensão vem da seleção `pos1`/`pos2`, inclusive Multiworld; não há Overworld fixo nem limpeza da dimensão inteira. As alturas selecionadas também contam.
- Não carrega chunks para limpar. Duas rotinas compartilham o orçamento de processamento em lotes para evitar picos.
- As proteções originais de dono, batalha, captura ocupada, NPC, raid, metadados, espécies e raridades são mantidas. Se o seu Clear protege shinies/lendários, eles continuam protegidos também no Safari.

A rotina local usa idade mínima 0 por padrão, configurável separadamente. As exclusões globais de dimensão não anulam a limpeza local: a área do Safari já define a dimensão e os limites válidos. O `enabled` geral e `/shirokaiclear pause` continuam controlando a automação; `safari.enabled` permite desativar a divisão regional e voltar ao comportamento global original.

Edite somente a seção `safari` para ajustar intervalo e mensagens locais, por exemplo `intervalSeconds: 120`. Os textos aceitam `{seconds}`, `{items}`, `{pokemon}`, cores/negrito com `&` e `§`. Use `/shirokaiclear reload` para aplicar. `/shirokaiclear status` e o painel `/safariadmin menu` mostram o contador e o último resultado local.

O Clear mantém as exigências do JAR enviado: Fabric Loader 0.18.3+ e Fabric API 0.116.7+1.21.1, além de Minecraft 1.21.1 e Cobblemon 1.8.3. As classes originais de proteção/índice e os mixins desse JAR são preservados byte a byte; o projeto de adaptação está em `integrations/clear-safari`.

## Bolas de uso único e limite sincronizado

Cada Safari Ball fornecida pelo mod é consumida no arremesso nativo. O projétil recebe a marca de uso único já na construção; erro/impacto não gera uma Safari Ball normal no chão. Pokébolas externas e projéteis fora de uma visita preservam o comportamento normal.

O pacote adicional fica registrado no mesmo ciclo diário da entrada. Na próxima renovação, o jogador recebe as 32 iniciais e volta a poder comprar uma vez. Se um pagamento for reconhecido só depois de o ciclo expirar, o pacote pago fica reservado para a próxima entrada, já contando como a compra extra desse novo ciclo, sem cobrar novamente ou perder as bolas.

A versão anterior não registrava qual compra já tinha sido concluída no ciclo. Na atualização, históricos antigos sem esse campo começam sem compra marcada; a limitação vale para as compras concluídas com a nova versão. Os tempos e saldos existentes não são zerados.

A entrada recusada por inventário cheio agora mantém o menu aberto e informa o motivo. Fechamentos redundantes durante entrada/retorno foram removidos. O teste de servidor abre o menu nativo após sair, com uma Safari Ball normal no inventário, e confirma que a rotina do Safari não envia um fechamento espontâneo; a compatibilidade visual com todos os mods/clientes ainda precisa de teste no ambiente real.

## Persistência, reinício e compras incertas

Dados: `<pasta_do_mundo>/data/shirokai-safari/state.json`. Faça backup com o servidor desligado. Não apague esse arquivo para atualizar o JAR.

O encerramento normal tenta devolver os visitantes. Após queda/reinício, uma visita ativa persistida vira retorno pendente, sem cobrar o tempo offline. O jogador retorna ao conectar; se a origem estiver indisponível ou insegura, usa a saída de emergência configurada. Se ambos falharem ou outra proteção negar o teleporte, o retorno continua salvo e há novas tentativas a cada 10 segundos. Os registros só são limpos depois de confirmar a posição de chegada.

A contabilização é salva aproximadamente a cada segundo. Uma queda abrupta pode deixar até o intervalo desde o último checkpoint sem cobrar; o mod não inventa consumo durante o período offline. Não existe uma transação conjunta entre os arquivos de jogador do Minecraft e o arquivo de estado do Safari.

Compras usam a API do Impactor, sem comandos de console ou saldo paralelo. A intenção com ID, preço, moeda e quantidade é salva antes do débito. O saldo de bolas é persistido após o reconhecimento do pagamento, antes de reconstruir as bolas físicas. Falha incerta ou reinício no meio da compra NÃO provoca nova cobrança ou repetição automática. A administração precisa conferir o ID no banco/console/economia:

- `/safariadmin compra <jogador_online> confirmar`: após verificar que pagou, credita o pacote pendente.
- `/safariadmin compra <jogador_online> cancelar`: após verificar que não pagou, limpa a pendência sem dar bolas.

O jogador deve estar fora da visita e a operação não pode estar em andamento. Esses comandos não estornam dinheiro. Não confirme sem verificar o débito: não existe idempotência distribuída garantida pelo Impactor.

### Shirokai Guard / outros protetores

Não use `entry-mode authorized-only` na região do Safari: esse modo do Guard atual reconhece a API de RAIDS, não a entrada do Safari. A própria área do Safari controla quem tem uma visita. Se usar uma região Guard adicional para blocos, mantenha a entrada normal e não bloqueie a autenticação/Safari. Exemplo, substituindo o ID correto da região/dimensão:

```
/sg flag safari entry-mode normal
/sg flag safari entry allow
/sg flag safari blocked-commands unset
/sg flag safari item-pickup allow
/sg flag safari allowed-commands safari,login,l,safariadmin
```

Regras herdadas/prioridades podem exigir ajuste. Outros mods podem negar um teleporte; Safari não recebe bypass automático. O mod não modifica regiões do Guard nem garante integração com toda implementação de autenticação. Valide o conjunto real de mods em uma cópia do servidor antes de produção.

## Compilação e testes

Java 21: `./gradlew test build`. O projeto contém testes de domínio/persistência e uma fixture separada de integração. Consulte `VALIDATION.md`. O JAR de integração não deve ser instalado em produção.

## Remover a área para edição

Use `/safariadmin delete` com a permissão administrativa `shirokai.safari.admin` ou OP (console também pode executar). O comando remove somente a seleção de área e o ponto de entrada salvos; não apaga blocos, dimensão, lobby, configurações de eventos, ciclos, compras ou bolas dos jogadores. Recusa se houver qualquer participante ativo ou retorno pendente, inclusive offline. Retire os participantes antes.

Sem área configurada, o Safari fica indisponível para entrada e o Clear volta ao escopo global original; a limpeza regional fica sem área. Para editar sem nenhuma limpeza global no local, pause o Clear com `/shirokaiclear pause` e retome ao terminar.

Depois da edição, selecione `/safariadmin pos1` e `/safariadmin pos2`, use `/safariadmin criar` e defina `/safariadmin entrada`. As seleções temporárias antigas são descartadas ao deletar.

## Regiões e NPCs

Dentro de cada destino seguro, use `/safariadmin regiao criar floresta` (ou `deserto`, `montanha`). Os nomes aceitam letras minúsculas, números, `_` e `-`, até 32 caracteres. Há até 21 destinos. `/safariadmin regiao listar` consulta os nomes; `/safariadmin regiao remover floresta` remove o destino para recriar em outra posição. Os destinos ficam em `regions` no JSON principal, incluindo dimensão e direção do olhar. Configurações antigas recebem o mapa vazio, sem reset de jogadores.

`/safari regioes` abre o menu; `/safari regiao floresta` teleporta diretamente. As regiões ficam em seu próprio menu, usado pelo NPC guia. Exigem visita ativa, presença dentro da área e tempo disponível. O destino é revalidado em cada clique, inclusive colisão, chão, borda e dimensão. Batalha/veículo impedem teleporte. Viajar mantém origem de retorno, ciclo, cota, bolas e compra; o relógio continua correndo.

Com Shirokai Trainers 0.3.2, crie NPCs independentes no hub interno do Safari e configure ações como jogador:

```mcfunction
/traineradmin npc create-command safari_guia
/traineradmin npc command safari_guia player safari regioes
/traineradmin npc create-command safari_loja
/traineradmin npc command safari_loja player safari comprar
```

Para destino direto, configure `player safari regiao deserto`. Não inclua `/` antes de `safari` no argumento do NPC. Se estiver visitando o Safari durante a configuração, use `/safariadmin modo on` antes e `off` depois para liberar seus comandos administrativos. O jogador comum não recebe OP.

O Safari permite a interação com NPCs indexados pelo Trainers com as ações públicas de compra, regiões/destino, eventos, missões e loja de pontos, no executor `player`. NPCs de batalha e outras ações não recebem essa liberação. A integração é opcional; sem Trainers os comandos e menus de regiões continuam disponíveis. A compra usa a confirmação existente, com preço e limite de uma compra por ciclo. Fora de uma visita ativa, os NPCs não dão acesso ao Safari nem vendem bolas. [Exemplo completo](examples/safari-npcs.txt).

Ao usar `/safariadmin delete`, os destinos são removidos junto com a área/entrada. Ao substituir a área com `criar`, destinos fora da nova seleção são descartados. Mundo e dados dos jogadores são preservados.

## Menus separados (0.1.5)

`/safari` mostra somente **Entrar/continuar**, **Tempo/renovação** e **Sair**. Não possui atalhos de compra, regiões ou eventos.

Os NPCs usam comandos específicos para abrir suas telas: `player safari comprar` (confirmação de compra), `player safari regioes` (destinos) e `player safari eventos` (informações do evento, com botão para atualizar). O menu de regiões tem um botão para retornar ao principal. Eventos administrativos continuam com os comandos de administração existentes.

Exemplo de NPC informativo adicional:

```mcfunction
/traineradmin npc create-command safari_eventos
/traineradmin npc command safari_eventos player safari eventos
```

Essas telas exigem uma visita ativa; os limites de tempo, preço e compra diária são mantidos. Não é necessário recriar NPCs de guia/loja já configurados. O Clear permanece igual.

## Expiração das bolas e tempo extra (0.1.6)

Quando a cota de tempo se esgota, ou a janela diária termina, as Safari Balls vinculadas restantes são removidas do inventário e do saldo. Os demais itens coletados continuam preservados. Sair voluntariamente com tempo disponível mantém o saldo de bolas.

`/safariadmin tempo <jogador> <minutos>` e `/safariadmin evento tempo <bonus_minutos> <duracao_minutos>` concedem um novo pacote inicial (32 por padrão, `ballBundle`) somente aos ciclos vigentes cuja cota estava esgotada. O bônus continua no mesmo ciclo diário. Quem ainda tem tempo mantém as bolas atuais, sem substituição nem pacote extra. Sair, voltar, reconectar ou reiniciar não concede outro pacote; o saldo e a cota são persistidos antes da entrega.

A compra adicional permanece uma por ciclo de 24 horas. O tempo extra não libera uma segunda compra. Se um pagamento terminar depois de esgotar a cota, o pacote pago ainda não entregue fica reservado em `reservedPaidBalls` até receber tempo extra ou iniciar o próximo ciclo diário. Ele é entregue uma única vez, somado ao pacote inicial, e continua consumindo a compra daquele ciclo de entrega. Pagamentos após o fim da janela mantêm o fluxo anterior de `nextCycleBundle`. Compras incertas continuam exigindo revisão administrativa.

Caso um bônus renove as bolas durante uma visita e não haja espaço, o saldo novo fica salvo e o jogador é encaminhado à saída (ou ao retorno pendente se o destino estiver indisponível). Depois de guardar os itens, ele pode entrar para receber esse saldo, sem nova concessão ou cobrança.

Na atualização, ciclos já esgotados perdem o saldo antigo de bolas; ciclos com tempo restante preservam seu saldo. O Clear permanece igual.

## Missões e loja de Pontos Safari (0.2.0)

Dois menus independentes, sem botões novos no menu principal:

- `/safari missoes`: abas diárias/semanais, progresso, prazo, saldo e resgate.
- `/safari loja`: produtos por Pontos Safari, seleção e confirmação em dois cliques, com limites individuais.

**Nenhuma missão nem produto vem cadastrado.** Na primeira inicialização são gerados `config/shirokai-safari-missions.json`, `config/shirokai-safari-shop.json` e `config/shirokai-safari-menus.json`. Os catálogos começam com listas vazias. Títulos, lore, cores `&`/hex/negrito, ícones, slots e paginação são configuráveis. [Guia completo e JSONs vazios](examples/tasks/CONFIGURACAO.md).

```mcfunction
/traineradmin npc create-command safari_missoes
/traineradmin npc command safari_missoes player safari missoes
/traineradmin npc create-command safari_pontos
/traineradmin npc command safari_pontos player safari loja
```

Os menus podem ser consultados fora da sessão; itens/comandos externos são resgatados somente fora do Safari. Pontos de missão sem recompensas externas podem ser resgatados durante a visita. Somente ações de visita ativa dentro da área progridem. Há objetivos de captura, espécies diferentes, derrotas selvagens, regiões distintas e diárias resgatadas; filtros de espécie/tipo/forma/região. Sem progresso retroativo. Captura/combate exigem a marca do spawn natural do Safari; Pokémon antigos passam a ser substituídos pelo spawn normal/clear, sem contar Pokémon dados por comando ou recebidos por troca.

Os prazos são independentes da janela de entrada: padrão meia-noite de Brasília e segunda-feira. Pontos e compras ficam persistidos em `world/data/shirokai-safari/missions.json`, sob a pasta real do mundo. O Clear, a cota de visita e as regras de bolas não mudaram.

Administração: `/safariadmin missoes reload`, `/safariadmin pontos <jogador> [ajuste]` e `/safariadmin entrega confirmar|cancelar <jogador>`. Entregas externas incertas geram recibo persistente e bloqueiam novos resgates/compras até revisão; não são reenviadas automaticamente. Confira os efeitos antes de cancelar um recibo. O guia explica campos, limites e revisão.
