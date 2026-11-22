# Missões e loja de Pontos Safari — 0.2.0

Os três JSONs são gerados na pasta `config` na primeira inicialização. **Nenhuma missão ou produto é ativado por padrão.** Os arquivos desta pasta são os padrões vazios, prontos para edição. Há dois menus independentes, sem atalhos no `/safari` principal.

## Comandos para jogadores/NPCs

- `/safari missoes`: menu com abas diárias e semanais, progresso, recompensa, prazo, saldo e resgate.
- `/safari loja`: loja de Pontos Safari; um clique seleciona e o segundo confirma, em até 15 segundos.

Os dois menus podem ser consultados fora da visita. Ações de captura/combate/visita só progridem em visita ativa, dentro da área, com tempo disponível. Pontos de missão sem itens/comandos podem ser resgatados dentro. **Recompensas com itens ou comandos exigem estar fora da área e sem visita/retorno pendente.** Coloque o NPC da loja de resgate no hub externo se quiser permitir a compra no local.

Configure os NPCs do Trainers como jogador, sem `/` antes de safari:

```mcfunction
/traineradmin npc create-command safari_missoes
/traineradmin npc command safari_missoes player safari missoes
/traineradmin npc create-command safari_pontos
/traineradmin npc command safari_pontos player safari loja
```

O Trainers não foi alterado. Use uma versão com NPCs de comando (testada: 0.3.2). Nenhum jogador precisa de OP para abrir esses menus; eles usam `shirokai.safari.use`, permitida por padrão. Para configurar NPCs durante uma visita, use `/safariadmin modo on` e depois `off`.

## Missões — `config/shirokai-safari-missions.json`

Campos gerais: `schemaVersion: 1`, `timezone` (padrão `America/Sao_Paulo`), `resetHour` (0 a 23), `weeklyResetDay` (`MONDAY` a `SUNDAY`) e `missions` (lista vazia por padrão). O padrão é meia-noite de Brasília; semana renova segunda-feira. Essa renovação é separada do ciclo de entrada de 24 horas.

Cada objeto adicionado a `missions` aceita:

| Campo | Uso |
|---|---|
| `id` | Nome técnico estável e único, até 48 letras minúsculas/números/`_`/`-` |
| `enabled` | `true`/`false` |
| `name`, `description` | Nome e lista de linhas exibidos no menu, com cores |
| `icon` | ID de item existente, como os ícones dos JSONs padrão |
| `period` | `DAILY` ou `WEEKLY` |
| `objective` | Um dos objetivos da tabela abaixo |
| `target` | Quantidade de 1 a 10.000 |
| `species` | Lista de IDs completos de espécies (`cobblemon:...`); vazia aceita todas |
| `types` | Lista de tipos em inglês/minúsculas; vazia aceita todos. Um dos tipos precisa coincidir |
| `forms` | Nomes de formas do Cobblemon em minúsculas; vazia aceita todas |
| `regions` | Nomes dos destinos cadastrados por `/safariadmin regiao criar`; vazia aceita todos |
| `regionRadius` | Raio de 1 a 64 blocos para objetivo de visitar regiões; padrão 6 |
| `reward` | Objeto com `points`, `items` e `commands`, descrito abaixo |

Objetivos:

- `CAPTURE`: capturas válidas; cada UUID de Pokémon conta uma vez por missão/período.
- `UNIQUE_SPECIES`: conta cada espécie uma vez, mesmo capturando vários exemplares.
- `DEFEAT`: derrotas de selvagens, confirmadas por vitória sem captura; não conta PvP, NPC ou apenas fazer um Pokémon desmaiar e depois perder/fugir.
- `VISIT_REGIONS`: destinos diferentes visitados dentro do raio configurado. Use filtros de `regions`; deixe filtros de espécie/tipo/forma vazios.
- `DAILY_COMPLETIONS`: missões diárias resgatadas com sucesso. Use filtros vazios. É apropriado para uma meta semanal.

Filtros não vazios são combinados: espécie **e** tipo **e** forma **e** região precisam coincidir. Para captura/combate, a região é o destino mais próximo do jogador, até 64 blocos; planeje os destinos para representar as regiões. Progresso não verifica a Pokédex existente e não é retroativo.

Capturas e derrotas usam Pokémon marcados pelo spawn natural do Safari. Pokémon dados por comando/troca, NPCs e Pokémon de jogadores não contam. Selvagens que já existiam antes de instalar esta versão não têm a marca; novos spawns naturais recebem-na automaticamente. O Clear continua com as proteções anteriores.

Alterar objetivo/filtros/meta de um `id` reinicia seu progresso. Se já foi resgatado no período, alterar o JSON não permite resgatar novamente. Mantenha IDs e horários estáveis; trocar o ID cria outra missão. Limite: 100 definições, com paginação no menu.

## Recompensas

Estrutura de `reward`:

- `points`: inteiro entre 0 e 1.000.000, somado ao saldo interno de Pontos Safari.
- `items`: lista de objetos com `item` (ID completo de item existente) e `amount` (1 a 2.304).
- `commands`: lista de comandos do servidor sem `/`, com `{player}` ou `{uuid}` para destinatário. Esses são os únicos placeholders aceitos. Não use quebras de linha.

Para dólares/chaves/títulos/cosméticos, configure o comando real do respectivo mod em `commands`; o Safari não inventa IDs de caixas nem preços de outra economia. Descreva o prêmio em `description` para o jogador saber o que recebe. Comandos são validados no dispatcher antes de ativar a configuração; os mods que fornecem esses comandos precisam estar instalados.

## Loja — `config/shirokai-safari-shop.json`

`schemaVersion: 1` e `products: []`. Cada produto configurado aceita `id`, `enabled`, `name`, `description`, `icon`, `cost` (1 a 1.000.000 pontos), `limit` (1 a 1.000), `limitPeriod` (`DAILY`, `WEEKLY`, `MONTHLY` ou `LIFETIME`) e `reward`.

Produtos precisam entregar itens/comandos e `reward.points` deve ser 0. Cada produto tem seu próprio limite por jogador. `LIFETIME` serve para desbloqueio único; os demais renovam pelo calendário/fuso configurados nas missões. Pontos não expiram com as missões nem com a sessão de Safari. Não há produtos pré-cadastrados, compra de tempo ou pacote extra de bolas ativados por padrão.

## Menus — `config/shirokai-safari-menus.json`

Todos os títulos, textos de navegação, mensagens de estado, templates de descrição e layouts estão nesse arquivo. `missions` e `shop` são layouts separados.

- `title`: título da tela; `rows`: de 3 a 6 linhas.
- `contentSlots`: slots que recebem missões/produtos, em ordem; a paginação é automática.
- `dailySlot`, `weeklySlot`, `previousSlot`, `balanceSlot`, `resetSlot`, `nextSlot`, `closeSlot`: posições dos controles. A loja reserva os mesmos espaços, mas não exibe abas de missões/prazo.
- `emptySlot`: deve pertencer a `contentSlots`; mostra o estado vazio.
- `filler`, `dailyIcon`, `weeklyIcon`, `balanceIcon`, `emptyIcon`, `previousIcon`, `nextIcon`, `closeIcon`, `resetIcon`: IDs de itens dos controles.
- Slots começam em 0. Em seis linhas, vão de 0 a 53. Ao reduzir as linhas, ajuste todos os slots. Sobreposição e slots fora da tela são rejeitados.

Cores: `&a`, `&6`, `&l` (negrito), `&r` e hex `&#RRGGBB` (também aceita `§`).

Templates de `missionLore`: `{description}` (expande a lista), `{progress}`, `{target}`, `{reward_points}`, `{status}`, `{points}`, `{reset}`, `{name}`. Templates de `productLore`: `{description}`, `{cost}`, `{purchased}`, `{limit}`, `{status}`, `{points}`, `{name}`. `balance` usa `{points}` e `reset` usa `{reset}`. Os preços/quantidades reais vêm da definição, não do texto do ícone.

Menus são protegidos contra retirada de ícones. Um menu antigo é atualizado antes de agir se houver reload ou troca de período. Abrir menus e ver opções não resgata nem cobra.

## Administração e dados

Permissão administrativa: `shirokai.safari.admin` ou OP (console também aceita).

```mcfunction
/safariadmin missoes reload
/safariadmin pontos NomeDoPlayer
/safariadmin pontos NomeDoPlayer 100
/safariadmin pontos NomeDoPlayer -100
/safariadmin entrega confirmar NomeDoPlayer
/safariadmin entrega cancelar NomeDoPlayer
```

`reload` valida os três JSONs em conjunto e mantém a configuração anterior se houver erro. Também permite recuperar o sistema após corrigir arquivos inicialmente inválidos. Saldo/progresso ficam em `world/data/shirokai-safari/missions.json` (sob o diretório real do mundo do servidor). Não apague esse arquivo para alterar missões: isso apagaria pontos e limites de compra. `/safariadmin delete` da área não reseta esse registro.

Cada entrega tem recibo persistido antes dos efeitos. Se falhar/reiniciar durante item/comando externo, não há reenvio automático: a conta fica bloqueada para novos resgates/compras até conferência. `confirmar` declara que a entrega ocorreu, sem executar novamente; `cancelar` estorna pontos/limite/resgate apenas depois de verificar que **nenhum efeito foi entregue**. Se houve entrega parcial, trate os efeitos já aplicados antes de cancelar. O ID está na mensagem e nos logs. Cancelar uma missão de um período encerrado não reabre o período antigo.

Itens/comandos externos não podem ser prometidos como exatamente uma vez em caso de queda entre efeito e confirmação. O recibo evita repetição automática; a revisão é necessária nesse intervalo. Os testes usam jogadores simulados e eventos reais, sem cliente humano.
