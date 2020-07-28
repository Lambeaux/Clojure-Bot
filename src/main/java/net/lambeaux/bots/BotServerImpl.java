package net.lambeaux.bots;

import rlbot.Bot;
import rlbot.manager.BotManager;
import rlbot.pyinterop.SocketServer;

/** Adapter between socket receiving packets and the bot itself. Controls the bot's lifecycle. */
public class BotServerImpl extends SocketServer {
  public BotServerImpl(int port, BotManager botManager) {
    super(port, botManager);
  }

  @Override
  protected Bot initBot(int index, String botType, int team) {
    return new BotImpl(index);
  }
}
