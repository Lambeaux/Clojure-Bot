package net.lambeaux.bots;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BotImplTest {
  @Test
  public void testDefaultBot() {
    BotImpl bot = new BotImpl(0);
    assertEquals(1.0f, bot.processInput(null).getThrottle(), 0.0f);
  }
}
