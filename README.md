# Zorykai Safari 0.1.0

[Baixar JAR](releases/v0.1.0/Shirokai-Safari-0.1.0.jar) · [Projeto completo ZIP](releases/v0.1.0/Shirokai-Safari-0.1.0-projeto-completo.zip) · [Validação](VALIDATION.md)

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

As 32 Pokébolas grátis são entregues uma única vez por ciclo. São Pokébolas reais do Cobblemon com nome e metadados de Safari, vinculadas ao jogador e ciclo. Bolas não usadas ficam no saldo ao sair e são devolvidas na reentrada. Batalhas e captura usam o motor normal do Cobblemon. Não há uma nova fórmula de captura.

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

O spawn padrão é 2× a frequência do timer natural do spawner de cada visitante. A área de cálculo desse spawner é recortada para o Safari. Não promete exatamente o dobro de Pokémon existentes: limites de população, bioma, horário e condições do Cobblemon continuam valendo. Não muda a taxa global do servidor.

## Comandos do jogador

- `/safari`: menu de entrada, saldo, compra e saída.
- `/safari entrar`: inicia ou retoma uma visita.
- `/safari sair`: retorna à origem e pausa o saldo.
- `/safari tempo`: informa saldo, renovação e bolas.
- `/safari comprar`: mostra o preço e solicita confirmação por 30 segundos.
- `/safari comprar confirmar`: compra outro pacote de 32. Quantidade e preço são configuráveis. Mudança de preço/moeda invalida a confirmação anterior.

Não há entrada paga; o preço é apenas do pacote adicional. É possível comprar mais de um pacote, desde que haja espaço e o limite de saldo de bolas seja respeitado.

## Proteções

Durante uma visita ou retorno pendente, comandos externos são bloqueados, incluindo home, sethome, TPA, tpaccept, spawn, enderchest e wrappers como execute. As exceções são os comandos do Safari e `/login`, `/l` (incluindo as variantes com namespace easyauth), para não bloquear autenticação. Aliases externos não são autorizados automaticamente.

Interações com blocos/containers, construção, troca de itens por cliques, coleta de itens no chão e troca de item segurado/cosmético dos Pokémon são bloqueadas. O inventário vanilla recusa adição de itens externos pela API normal; não é um isolamento contra outros mods que deliberadamente escrevam diretamente nos slots. Os Pokémon da equipe não são substituídos nem curados. Não há fome durante a visita.

Quem tentar entrar fisicamente sem visita é retirado para a saída configurada. Um teleporte externo durante uma visita encerra a visita e processa o retorno no próximo ciclo de verificação, até aproximadamente um segundo. Administradores podem atuar fora da visita para construir e configurar a área.

### Administração

Permissão `shirokai.safari.admin`; fallback OP nível 2 se não houver decisão explícita do LuckPerms. O console também pode administrar as operações sem posição pessoal.

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
- `/safariadmin evento parar`: encerra os efeitos temporários de spawn/shiny/forma e o bônus para novos ciclos. Tempo já concedido não é retirado.

Os efeitos podem coexistir. Configurar um efeito redefine o prazo comum do evento; não há agendas independentes. Para encerrar só uma chance, configure-a como 0; para spawn, use 1.

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
/sg flag safari allowed-commands safari,login,l,safariadmin
```

Regras herdadas/prioridades podem exigir ajuste. Outros mods podem negar um teleporte; Safari não recebe bypass automático. O mod não modifica regiões do Guard nem garante integração com toda implementação de autenticação. Valide o conjunto real de mods em uma cópia do servidor antes de produção.

## Compilação e testes

Java 21: `./gradlew test build`. O projeto contém testes de domínio/persistência e uma fixture separada de integração. Consulte `VALIDATION.md`. O JAR de integração não deve ser instalado em produção.
