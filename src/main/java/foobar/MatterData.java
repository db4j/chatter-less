package foobar;

import foobar.Tuplator.HunkTuples;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.Function;
import kilim.Pausable;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.data.Posts;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Database;
import org.db4j.Db4j;
import org.db4j.Db4j.Transaction;
import org.db4j.HunkArray;
import org.db4j.HunkCount;
import static org.db4j.perf.DemoHunker.resolve;
import org.srlutils.Simple;

public class MatterData extends Database {
    private static final long serialVersionUID = -1766716344272097374L;

    Btrees.IK<Object> gen;
    HunkCount idcount;
    Btrees.IK<Users> users;
    Btrees.SI idmap;
    Btrees.SI usersByName;
    Btrees.IK<Teams> teams;
    Btrees.SI teamsByName;
    Btrees.IK<Channels> channels;
    Btrees.II chanByTeam;
    Btrees.IK<TeamMembers> tembers;
    Btrees.IK<ChannelMembers> cembers;
    Btrees.II cemberMap;
    Btrees.II temberMap;
    Tuplator.III chan2cember;
    HunkTuples status;
    Btrees.IK<Posts> posts;
    HunkCount   numChannels;
    HunkArray.I channelCounts;
    Tuplator.IIK<Posts> channelPosts;
    


    <TT> TT get(Transaction txn,Btrees.IK<TT> map,String key) throws Pausable {
        Integer kk = idmap.context().set(txn).set(key,null).find(idmap).val;
        return map.find(txn,kk);
    }
    static <TT> Btrees.IK<TT>.Data filter(Transaction txn,Btrees.IK<TT> map,Function<TT,Boolean> filter) throws Pausable {
        Btrees.IK<TT>.Range r1 = map.getall(txn);
        while (r1.next())
            if (filter.apply(r1.cc.val))
                return r1.cc;
        return map.context();
    }
    static <TT> Btrees.IK<TT>.Data filter(Transaction txn,Btrees.II index,int key,Btrees.IK<TT> map,
            Function<TT,Boolean> filter) throws Pausable {
        Btree.Range<Btrees.II.Data> r1 = index.findPrefix(index.context().set(txn).set(key,0));
        Btrees.IK<TT>.Data cc = map.context().set(txn);
        while (r1.next()) {
            cc.set(r1.cc.val,null);
            map.findData(cc);
            if (filter.apply(cc.val))
                return cc;
        }
        cc.match = false;
        cc.set(-1,null);
        return cc;
    }
    
    Integer addTeam(Transaction txn,Teams team) throws Pausable {
        Integer row = teamsByName.find(txn,team.name);
        if (row !=null ) return null;
        int newrow = idcount.plus(txn,1);
        teams.insert(txn,newrow,team);
        teamsByName.insert(txn,team.name,newrow);
        idmap.insert(txn,team.id,newrow);
        return newrow;
    }
    int addChan(Transaction txn,Channels chan,int kteam) throws Pausable {
        int newrow = numChannels.plus(txn,1);
        channels.insert(txn,newrow,chan);
        idmap.insert(txn,chan.id,newrow);
        chanByTeam.context().set(txn).set(kteam,newrow).insert();
        channelCounts.set(txn,newrow,0);
        return newrow;
    }
    int addTeamMember(Transaction txn,int kuser,TeamMembers member) throws Pausable {
        int newrow = idcount.plus(txn,1);
        tembers.insert(txn,newrow,member);
        temberMap.context().set(txn).set(kuser,newrow).insert();
        return newrow;
    }
    int addChanMember(Transaction txn,int kuser,int kchan,ChannelMembers member) throws Pausable {
        int newrow = idcount.plus(txn,1);
        cembers.insert(txn,newrow,member);
        cemberMap.context().set(txn).set(kuser,newrow).insert();
        chan2cember.insert(txn,new Tuplator.Pair(kchan,kuser),newrow);
        return newrow;
    }
    int addPost(Transaction txn,int kchan,Posts post) throws Pausable {
        int kpost = channelCounts.get(txn,kchan).yield().val;
        channelCounts.set(txn,kchan,kpost+1);
        channelPosts.insert(txn,new Tuplator.Pair(kchan,kpost),post);
        idmap.insert(txn,post.id,kpost);
        return kpost;
    }

    public static class FieldCopier<SS,TT> {
        Field[] map, srcFields;
        Class <TT> dstClass;
        BiConsumer<SS,TT> [] extras;
        
        public TT copy(SS src) {
            return copy(src,null);
        }
        public <XX extends TT> XX copy(SS src,XX dst) {
            if (src==null) return dst;
            if (dst==null) dst = (XX) Simple.Reflect.alloc(dstClass,true);
            try {
                for (int ii=0; ii < srcFields.length; ii++)
                    if (map[ii] != null)
                        map[ii].set(dst, srcFields[ii].get(src));
            }
            catch (Exception ex) { throw new RuntimeException(ex); }
            for (BiConsumer extra : extras)
                extra.accept(src,dst);
            return dst;
        }
        public FieldCopier(Class<SS> srcClass,Class<TT> dstClass,BiConsumer<SS,TT> ... extras) {
            this.extras = extras;
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
        MatterData dm = new MatterData();
        Db4j db4j = dm.start(resolve("./db_files/hunk.mmap"),args.length==0);
        dm.shutdown(true);
    }
    
}
