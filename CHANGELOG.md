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
