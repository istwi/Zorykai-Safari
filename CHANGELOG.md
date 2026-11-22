# 0.1.6

- Ao esgotar a cota ou a janela diária, as bolas restantes expiram, inclusive se o retorno falhar.
- Bônus individual/evento de tempo após esgotamento concede novo pacote inicial; bônus com tempo restante não duplica bolas.
- Ciclo de 24h e limite de compra são mantidos. Pacotes pagos ainda não entregues ficam reservados se o pagamento terminar após o esgotamento.
- Entrega de bônus persistida antes da reconstrução física; inventário cheio preserva o saldo e encaminha à saída.

# 0.1.5

- Menu `/safari` reduzido a entrada/continuação, tempo/renovação e saída.
- Compra e regiões ficam em menus separados, sem botões no principal.
- `/safari eventos` abre uma tela própria de consulta/atualização, permitida no NPC informativo do Trainers.

# 0.1.4

- Destinos administrativos persistidos com `/safariadmin regiao criar|remover|listar`.
- Menu `/safari regioes`, botão no menu principal e teleporte `/safari regiao <nome>` dentro da visita ativa, mantendo ciclo e saldos.
- Integração opcional com NPCs de comandos do Trainers: guia, vendedor e destino direto; executor player e ações Safari específicas.
- Validação de destinos seguros e contenção na área, com limpeza de destinos ao deletar/redefinir área.

# 0.1.3

- `/safariadmin delete` remove área e entrada persistidas para edição, preservando mundo, lobby e dados dos jogadores.
- Recusa remoção com participantes ativos/retorno pendente e limpa seleções temporárias após salvar.
- Comando incluído na ajuda administrativa; testes de integração para remoção e recusas.

# 0.1.2

- Uma única compra de pacote adicional por ciclo de entrada, persistida e renovada junto com o Safari; pagamentos recusados não consomem o limite e créditos tardios não se perdem.
- Safari Balls emitidas durante a visita são de uso único: erros não devolvem uma bola normal no chão.
- Ajuste de fechamento de menu durante entrada/retorno e manutenção da interface em recusa por inventário cheio.
- Integração com Clear adaptado: limpeza de itens/Pokémon a cada 120 segundos somente na área e dimensão do Safari, sem mudar o contador global e com mensagens locais.
- Painel administrativo mostra o estado da limpeza e a dimensão alvo.

# 0.1.1

- Safari Ball nativa no lugar da Pokébola comum, com migração do tipo antigo e preservação de saldo/ciclo.
- Compra com confirmação por menu/chat, diagnóstico do Impactor, consulta de saldo/moeda e pacote gratuito quando o preço é zero.
- Coleta de itens do chão liberada; acesso a containers, uso de bolas externas e consumíveis de batalha permanecem restritos.
- Ajuda completa, confirmação de saída salva, evento/status e argumento de espécie com namespace corrigido.
- Painel administrativo de consulta com visitantes, eventos, multiplicadores e métricas reais de execução do spawner.
- Boss bar com tempo e bolas; evento tem prioridade temporária; integração visual opcional para ocultar/restaurar a barra da Raid por visitante.
- Broadcast no chat e título com JSON editável, cores e negrito; mensagens podem ser recarregadas sem retirar visitantes.
