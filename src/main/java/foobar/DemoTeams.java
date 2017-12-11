package foobar;

import static foobar.MatterControl.*;
import foobar.MatterKilim.Processor;
import static foobar.Utilmm.newid;
import kilim.Pausable;
import kilim.Task;
import kilim.http.HttpRequest;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.TeamsxMembersBatchReq;
import mm.rest.UsersReps;
import mm.rest.UsersReqs;
import org.db4j.Db4j;
import org.db4j.Db4j.Transaction;

public class DemoTeams {
    MatterControl matter = new MatterControl();
    MatterData dm = matter.dm;
    Db4j db4j = matter.db4j;
    
    public static class Req extends HttpRequest {
        String body;
        Req(String $body) { body=$body; }
        public String extractRange(int x1,int x2) { return body; }
    }
    
    public class Human {
        String uid = newid();
        UsersReps reply;

        Processor fake(Object obj) {
            String body = obj==null||obj instanceof String ? (String)obj:gson.toJson(obj);
            Processor proc = new Processor(null);
            proc.setup(matter);
            proc.req = new Req(body);
            proc.uid = uid;
            return proc;
        }
        UsersReps user(String name) throws Pausable {
            UsersReqs user = set(new UsersReqs(),
                    x -> { x.email=name+"@seth.com"; x.username=name; x.password="fool"; });
            reply = (UsersReps) fake(user).users();
            uid = reply.id;
            return reply;
        }
    }
    
    public static void main(String[] args) {
        int mode = 2;
        if (mode==0) MatterData.main(args);
        DemoTeams dt = new DemoTeams();
        if (mode==1) Task.spawnCall(() -> { dt.stuff(); }).joinb();
        if (mode==2) dt.db4j.submitCall(txn -> dt.dump(txn)).awaitb();
        dt.db4j.shutdown();
    }
    TeamsReqs team(String name) {
        return set(new TeamsReqs(),
                x -> { x.displayName=x.name=name; x.type="0"; });
    }
    void pugly(Object obj) { System.out.println(ugly(obj)); }
    void dump(Transaction txn) throws Pausable {
        dm.tembers.getall(txn).visit(cc -> {
            System.out.format("tember %d: ",cc.key);
            print(cc.val);
        });
        dm.temberMap.getall(txn).visit(cc -> prnt("map: %d -> %d\n",cc.key,cc.val));
        dm.team2tember.getall(txn).visit(cc -> prnt("t2t: %d,%d -> %d\n",cc.key.v1,cc.key.v2,cc.val));
        dm.cembers.getall(txn).visit(cc -> {
            System.out.format("%d: ",cc.key);
            print(cc.val);
        });
        dm.cemberMap.getall(txn).visit(cc -> prnt("map: %d -> %d\n",cc.key,cc.val));
        dm.chan2cember.getall(txn).visit(cc -> prnt("c2c: %d,%d -> %d\n",cc.key.v1,cc.key.v2,cc.val));
    }
    void prnt(String fmt,Object ... obj) { System.out.format(fmt,obj); }

    void stuff() throws Pausable {
        Human p1 = new Human();
        Human p2 = new Human();
        print(p1.user("seth"));
        print(p2.user("mark"));
        TeamsReps t1 = (TeamsReps) p1.fake(team("x1")).postTeams();
        TeamsReps t2 = (TeamsReps) p2.fake(team("x2")).postTeams();
        print(t1);
        print(t2);
        TeamsxMembersBatchReq invite = set(new TeamsxMembersBatchReq(),x -> { x.teamId=t1.id; x.userId=p2.uid; });
        Object obj = null;
        obj = p1.fake(new Object[] {invite}).txmBatch(t1.id);
        print(obj);
        p1.fake("").leaveTeam(t1.id,p2.uid);
        db4j.submitCall(txn -> dump(txn)).await();
    }
    
}
