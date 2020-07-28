package net.lambeaux.bots;

import java.io.IOException;
import rlbot.manager.BotManager;

/** Primary entry point for the process that controls the bot. */
public class Main {

  private static final int DEFAULT_PORT = 17357;

  public static void main(String[] args) throws IOException {

    // For now we assume the bot is run from the repository root
    System.setProperty("jna.library.path", "tmp/");

    // Setup
    final BotManager botManager = new BotManager();
    final BotServerImpl botServer = new BotServerImpl(DEFAULT_PORT, botManager);
    final Thread botThread = new Thread(botServer::start);

    botThread.start();
  }
}
