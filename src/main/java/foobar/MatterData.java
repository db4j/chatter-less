package foobar;

import java.lang.reflect.Field;
import kilim.Pausable;
import mm.data.Channels;
import mm.data.Teams;
import mm.data.Users;
import org.db4j.Btrees;
import org.db4j.Database;
import org.db4j.Db4j.Transaction;
import org.db4j.HunkCount;
import static org.db4j.perf.DemoHunker.resolve;
import org.srlutils.Simple;

public class MatterData extends Database {
    private static final long serialVersionUID = -1766716344272097374L;

    Btrees.IK<Object> gen;
    HunkCount userCount;
    Btrees.IK<Users> users;
    Btrees.SI usersById;
    Btrees.SI usersByName;
    HunkCount teamCount, nchan;
    Btrees.IK<Teams> teams;
    Btrees.SI teamsByName, teamsById, chanById;
    Btrees.IK<Channels> channels;
    Btrees.II chanByTeam;

    Integer addTeam(Transaction txn,Teams team) throws Pausable {
        Integer row = teamsByName.find(txn,team.name);
        if (row !=null ) return null;
        int newrow = teamCount.plus(txn,1);
        teams.insert(txn,newrow,team);
        teamsByName.insert(txn,team.name,newrow);
        teamsById.insert(txn,team.id,newrow);
        return newrow;
    }
    int addChan(Transaction txn,Channels chan,int kteam) throws Pausable {
        int newrow = nchan.plus(txn,1);
        channels.insert(txn,newrow,chan);
        chanById.insert(txn,chan.id,newrow);
        chanByTeam.context().set(txn).set(kteam,newrow).insert();
        return newrow;
    }

    public static class FieldCopier<SS,TT> {
        Field[] map, srcFields;
        Class <TT> dstClass;
        
        public <XX extends TT> XX copy(SS src) {
            return copy(src,null);
        }
        public <XX extends TT> XX copy(SS src,XX dst) {
            if (dst==null) dst = (XX) Simple.Reflect.alloc(dstClass,true);
            try {
                for (int ii=0; ii < srcFields.length; ii++)
                    if (map[ii] != null)
                        map[ii].set(dst, srcFields[ii].get(src));
                return dst;
            }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }
        
        public FieldCopier(Class<SS> srcClass,Class<TT> dstClass) {
            this.dstClass = dstClass;
            srcFields = srcClass.getDeclaredFields();
            Field[] dstFields = dstClass.getDeclaredFields();
            map = new Field[srcFields.length];
            for (int ii=0; ii < srcFields.length; ii++)
                for (int jj=0; jj < dstFields.length; jj++) {
                    Field src = srcFields[ii], dst = dstFields[jj];
                    if (src.getName().equals(dst.getName()) & src.getType().equals(dst.getType())) {
                        src.setAccessible( true );
                        dst.setAccessible( true );
                        map[ii] = dst;
                    }
                }
        }
        
    }    
    
    
    public static void main(String[] args) {
        MatterData hello = new MatterData();
        hello.start(resolve("./db_files/hunk.mmap"),args.length==0);
        hello.shutdown(true);
    }
    
}
