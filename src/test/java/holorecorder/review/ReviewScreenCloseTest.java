package holorecorder.review;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing the review screen used to call Gui.setScreen(null) again from removed(), inside the outer setScreen; fabric-screen-api
 * then saw a null screen and threw "Screen cannot be null". The game classes cannot run in a unit test, so this pins the source.
 */
class ReviewScreenCloseTest {
    @Test
    void removedDoesNotCloseTheScreenAgain() throws IOException {
        String screen = Files.readString(Path.of("src/main/java/holorecorder/review/ReviewScreen.java"));
        int at = screen.indexOf("public void removed()");
        String body = screen.substring(at, screen.indexOf('}', at));
        assertTrue(body.contains("ReviewClient.stop(minecraft, true, false)"), body);
        String client = Files.readString(Path.of("src/main/java/holorecorder/review/ReviewClient.java"));
        assertTrue(client.contains("if (closeScreen && mc.gui.screen() instanceof ReviewScreen) mc.gui.setScreen(null);"));
    }
}
