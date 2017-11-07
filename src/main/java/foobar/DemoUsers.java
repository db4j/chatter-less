package foobar;

import java.util.Random;
import static kilim.Task.spawn;

public class DemoUsers extends  DemoTeams {
    public static void main(String[] args) {
        int mode = 0;
        if (mode < 1) MatterData.main(args);
        DemoTeams dt = new DemoUsers();
        dt.matter.random = new Random(0L);
        for (int ii=0; ii < 10; ii++) {
            String name = "dave"+ii;
            spawn(() -> dt.new Human().user(name)).joinb();
        }
        spawn(() -> dt.new Human().user("mark")).joinb();
        spawn(() -> dt.new Human().user("mark2")).joinb();
        dt.db4j.shutdown();
        kilim.Scheduler.getDefaultScheduler().idledown();
    }
    
}
