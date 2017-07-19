package foobar;

import java.lang.reflect.Field;
import mm.data.Teams;
import mm.data.Users;
import org.db4j.Btrees;
import org.db4j.Database;
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
    Btrees.SI teamsByName, chanById;
    Btrees.IK<Channels> channels;
    Btrees.II chanByTeam;


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
