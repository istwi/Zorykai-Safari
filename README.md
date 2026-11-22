# Zorykai Safari 0.1.3

[Baixar Safari + Clear ZIP](releases/v0.1.3/Zorykai-Safari-0.1.3-e-Clear.zip) · [JAR Safari](releases/v0.1.3/Shirokai-Safari-0.1.3.jar) · [JAR Clear](releases/v0.1.3/Shirokai-Clear-1.0.1-Zorykai-Safari.jar) · [Validação](VALIDATION.md) · [JSON de mensagens](examples/shirokai-safari-messages.json)

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

As 32 Pokébolas grátis são entregues uma única vez por ciclo. São **Safari Balls reais do Cobblemon** (`cobblemon:safari_ball`), vinculadas ao jogador e ciclo. A atualização migra automaticamente o item antigo `poke_ball` para Safari Ball, preservando o saldo numérico e o ciclo. Bolas não usadas ficam no saldo ao sair e são devolvidas na reentrada. Batalhas e captura usam o motor normal do Cobblemon. A captura usa as regras nativas da Safari Ball do Cobblemon, sem uma nova fórmula personalizada.

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

- `/safari`: menu de entrada, saldo, compra e saída.
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

Instale o **Clear adaptado incluído neste release**, substituindo o Clear anterior. Não deixe dois JARs do mesmo mod em `mods`. O Safari 0.1.3 também deve substituir seu JAR anterior. Os dois arquivos mantêm os IDs técnicos e as configurações existentes.

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
