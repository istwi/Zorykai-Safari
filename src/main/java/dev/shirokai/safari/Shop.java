package dev.shirokai.safari;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.impactdev.impactor.api.economy.EconomyService;
import net.kyori.adventure.key.Key;
import net.minecraft.server.level.ServerPlayer;

/** Debit is never retried automatically after an ambiguous failure. */
public final class Shop {
  private final SafariMod mod;
  private final Set<String> inFlight = new HashSet<>();

  private record Payment(boolean paid, String reason, java.math.BigDecimal balance) {}

  public boolean inFlight(String id) {
    return inFlight.contains(id);
  }

  public Shop(SafariMod mod) {
    this.mod = mod;
  }

  public void buy(ServerPlayer p) {
    var v = mod.visit(p);
    if (!mod.active(p) || !v.usable(System.currentTimeMillis()))
      throw new IllegalStateException("Entre no Safari primeiro");
    if (v.bundlePurchased)
      throw new IllegalStateException(
          "Pacote adicional já comprado. Renova junto com o Safari em "
              + mod.format(v.cycleEnd - System.currentTimeMillis()));
    if (v.purchase != null)
      throw new IllegalStateException("Existe uma compra pendente de conferência");
    if (v.balls + mod.config.ballBundle > 2304 || !Balls.room(p, mod.config.ballBundle, mod.config))
      throw new IllegalStateException("Sem espaço para o pacote");
    if (mod.config.bundlePrice.signum() == 0) {
      v.balls = Balls.count(p, v);
      v.creditBundle(mod.config.ballBundle, System.currentTimeMillis());
      mod.save();
      Balls.remove(p);
      Balls.give(p, v, v.balls, mod.config);
      mod.tell(p, "Pacote gratuito recebido: +" + mod.config.ballBundle + " Safari Balls.");
      return;
    }
    if (!FabricLoader.getInstance().isModLoaded("impactor"))
      throw new IllegalStateException("Impactor indisponível; compra não iniciada");
    var economy = EconomyService.instance();
    var currency =
        economy
            .currencies()
            .currency(Key.key(mod.config.currency))
            .orElseThrow(() -> new IllegalStateException("Moeda não encontrada"));
    String id = UUID.randomUUID().toString();
    var price = mod.config.bundlePrice;
    int amount = mod.config.ballBundle;
    v.purchase = id;
    v.purchasePrice = price.toPlainString();
    v.purchaseCurrency = mod.config.currency;
    v.purchaseAmount = amount;
    mod.save();
    inFlight.add(id);
    economy
        .account(currency, p.getUUID())
        .thenCompose(
            account ->
                account
                    .withdrawAsync(price)
                    .thenCompose(
                        report -> {
                          if (!report.successful())
                            return java.util.concurrent.CompletableFuture.completedFuture(
                                new Payment(false, report.result().name(), account.balance()));
                          return economy
                              .save(account)
                              .thenApply(
                                  ignored -> new Payment(true, "SUCCESS", account.balance()));
                        }))
        .whenComplete(
            (success, error) ->
                mod.server.execute(
                    () -> {
                      inFlight.remove(id);
                      if (!id.equals(v.purchase)) {
                        SafariMod.LOG.error(
                            "Compra {} finalizou após alteração administrativa; confira o débito"
                                + " manualmente",
                            id);
                        return;
                      }
                      if (error != null) {
                        mod.tell(
                            p,
                            "Compra incerta. Não repita; informe o ID " + id + " à administração.");
                        SafariMod.LOG.error("Compra Safari requer conferência: {}", id, error);
                        return;
                      }
                      if (success == null || !success.paid()) {
                        v.purchase = null;
                        v.purchaseAmount = 0;
                        mod.save();
                        String reason =
                            success == null
                                ? "Sem resposta do provedor"
                                : switch (success.reason()) {
                                  case "NOT_ENOUGH_FUNDS" -> "saldo insuficiente";
                                  case "CANCELLED" -> "transação cancelada pelo provedor";
                                  case "INVALID" -> "valor ou transação inválida";
                                  default -> success.reason();
                                };
                        String balance =
                            success == null ? "indisponível" : success.balance().toPlainString();
                        mod.tell(
                            p,
                            "Pagamento recusado: "
                                + reason
                                + ". Moeda: "
                                + v.purchaseCurrency
                                + " | saldo: "
                                + balance
                                + " | preço: "
                                + price.toPlainString()
                                + ". Nenhuma bola entregue.");
                        SafariMod.LOG.warn(
                            "Pagamento Safari recusado jogador={} pedido={} resultado={} moeda={}"
                                + " saldo={} preço={}",
                            p.getUUID(),
                            id,
                            reason,
                            v.purchaseCurrency,
                            balance,
                            price);
                        return;
                      }
                      // Save the balance entitlement before physical delivery; recovery rebuilds
                      // from this balance.
                      boolean online =
                          mod.server.getPlayerList().getPlayer(p.getUUID()) == p && mod.active(p);
                      if (online) v.balls = Balls.count(p, v);
                      v.creditBundle(amount, System.currentTimeMillis());
                      v.purchase = null;
                      v.purchaseAmount = 0;
                      mod.save();
                      if (v.nextCycleBundle > 0) {
                        mod.tell(
                            p,
                            "O ciclo terminou durante o pagamento. O pacote está reservado para o"
                                + " próximo Safari, sem nova cobrança.");
                        return;
                      }
                      if (online) {
                        Balls.remove(p);
                        Balls.give(p, v, v.balls, mod.config);
                        mod.tell(p, "Compra concluída: +" + amount + " bolas.");
                      }
                    }));
  }

  public void balance(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
    if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("impactor"))
      throw new IllegalStateException("Impactor indisponível");
    var economy = EconomyService.instance();
    String reference = mod.config.currency;
    var currency =
        economy
            .currencies()
            .currency(Key.key(reference))
            .orElseThrow(() -> new IllegalStateException("Moeda não encontrada: " + reference));
    economy
        .account(currency, player.getUUID())
        .whenComplete(
            (account, error) ->
                mod.server.execute(
                    () -> {
                      if (error != null) {
                        source.sendFailure(
                            net.minecraft.network.chat.Component.literal(
                                "Safari: não foi possível consultar o saldo."));
                        SafariMod.LOG.error("Consulta econômica Safari falhou", error);
                        return;
                      }
                      source.sendSuccess(
                          () ->
                              net.minecraft.network.chat.Component.literal(
                                  "Safari • "
                                      + player.getGameProfile().getName()
                                      + " • moeda "
                                      + reference
                                      + " • saldo "
                                      + account.balance().toPlainString()
                                      + " • pacote "
                                      + mod.config.ballBundle
                                      + " bolas por "
                                      + mod.config.bundlePrice.toPlainString()),
                          false);
                    }));
  }

  public static boolean currencyAvailable(String reference) {
    return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("impactor")
        && EconomyService.instance().currencies().currency(Key.key(reference)).isPresent();
  }
}
