package net.lambeaux.bots;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;

/** Adapter between socket receiving packets and the bot itself. Controls the bot's lifecycle. */
public class BotServerImpl extends SocketServer {

  static {
    IFn require = Clojure.var("clojure.core", "require");
    require.invoke(Clojure.read("net.lambeaux.bots.core"));
  }

  private static final IFn BOT_FACTORY = Clojure.var("net.lambeaux.bots.core", "create-bot");

  public BotServerImpl(int port, BotManager botManager) {
    super(port, botManager);
  }

  @Override
  protected Bot initBot(int index, String botType, int team) {
    return (Bot) BOT_FACTORY.invoke(index);
  }
}
