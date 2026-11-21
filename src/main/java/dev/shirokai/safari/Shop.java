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
    if (v.purchase != null)
      throw new IllegalStateException("Existe uma compra pendente de conferência");
    if (v.balls + mod.config.ballBundle > 2304 || !Balls.room(p, mod.config.ballBundle, mod.config))
      throw new IllegalStateException("Sem espaço para o pacote");
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
                            return java.util.concurrent.CompletableFuture.completedFuture(false);
                          return economy.save(account).thenApply(ignored -> true);
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
                      if (!Boolean.TRUE.equals(success)) {
                        v.purchase = null;
                        v.purchaseAmount = 0;
                        mod.save();
                        mod.tell(p, "Pagamento recusado; nenhuma bola entregue.");
                        return;
                      }
                      // Save the balance entitlement before physical delivery; recovery rebuilds
                      // from this balance.
                      boolean online =
                          mod.server.getPlayerList().getPlayer(p.getUUID()) == p && mod.active(p);
                      if (online) v.balls = Balls.count(p, v);
                      v.balls += amount;
                      v.purchase = null;
                      v.purchaseAmount = 0;
                      mod.save();
                      if (online) {
                        Balls.remove(p);
                        Balls.give(p, v, v.balls, mod.config);
                        mod.tell(p, "Compra concluída: +" + amount + " bolas.");
                      }
                    }));
  }
}
