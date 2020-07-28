package net.lambeaux.bots;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import java.io.IOException;
import rlbot.manager.BotManager;

/** Primary entry point for the process that controls the bot. */
public class Main {

  static {
    IFn require = Clojure.var("clojure.core", "require");
    require.invoke(Clojure.read("nrepl.server"));
  }

  private static final int DEFAULT_REPL_PORT = 7888;

  private static final int DEFAULT_BOT_PORT = 17357;

  private static final String JNA_LIB_PATH = "tmp/";

  public static void main(String[] args) throws IOException {

    // For now we assume the bot is run from the repository root
    System.setProperty("jna.library.path", JNA_LIB_PATH);
    System.out.println("System property 'jna.library.path' has been set to " + JNA_LIB_PATH);

    // Starts the nREPL server so Clojure can change the bot's bytecode while running.
    IFn start = Clojure.var("nrepl.server", "start-server");
    start.invoke(Clojure.read(":port"), Clojure.read(Integer.toString(DEFAULT_REPL_PORT)));
    System.out.println("nrepl server started on port " + DEFAULT_REPL_PORT);

    // Setup
    final BotManager botManager = new BotManager();
    final BotServerImpl botServer = new BotServerImpl(DEFAULT_BOT_PORT, botManager);
    final Thread botThread = new Thread(botServer::start);

    // Go!
    botThread.start();
  }
}
