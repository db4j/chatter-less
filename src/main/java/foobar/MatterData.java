package foobar;

import mm.rest.UsersReps;
import org.db4j.Btrees;
import org.db4j.Database;
import org.db4j.HunkCount;
import static org.db4j.perf.DemoHunker.resolve;

public class MatterData extends Database {
    Btrees.IK<Object> gen;
    HunkCount userCount;
    Btrees.IK<UsersReps> users;
    Btrees.SI usersById;

    
    
    
    public static void main(String[] args) {
        MatterData hello = new MatterData();
        hello.start(resolve("./db_files/hunk.mmap"),args.length==0);
        hello.shutdown(true);
    }
    
}
