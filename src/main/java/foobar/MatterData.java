package foobar;

import foobar.MatterKilim.BadRoute;
import foobar.Tuplator.HunkTuples;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import kilim.Pausable;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.data.Posts;
import mm.data.Preferences;
import org.db4j.Bmeta;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Command;
import org.db4j.Database;
import org.db4j.Db4j;
import org.db4j.Db4j.Transaction;
import org.db4j.HunkArray;
import org.db4j.HunkCount;
import static org.db4j.perf.DemoHunker.resolve;
import org.srlutils.Simple;
import org.srlutils.btree.Bpage;

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
    // kteam -> kchan
    Btrees.II chanByTeam;
    Btrees.IK<TeamMembers> tembers;
    Btrees.IK<ChannelMembers> cembers;
    // kuser -> kcember
    Btrees.II cemberMap;
    // kuser -> ktember
    Btrees.II temberMap;
    // (kchan,kuser) -> kcember
    Tuplator.III chan2cember;
    // (kteam,kuser) -> ktember
    Tuplator.III team2tember;
    HunkTuples status;
    HunkCount   numChannels;
    HunkArray.I channelCounts;
    // (kchan,kpost) -> post
    Tuplator.IIK<Posts> channelPosts;
    Btrees.IK<Preferences> prefs;
    
    /**
     * each row corresponds to an entity allocated using the shared idcount
     * currently this includes users, teams, cembers and tembers
     * ie, can be indexed by kcember or kuser
     * field values are not defined for all types
     */
    public static class Links extends Table {
        // for tembers and cembers
        HunkArray.I kteam;
        HunkArray.I kchan;
        HunkArray.I kuser;
        HunkArray.L delete;
        
        void set(Transaction txn,int kmember,int kuser,int kchan,int kteam) throws Pausable {
            Links links = this;
            links.kchan.set(txn,kmember,kchan);
            links.kteam.set(txn,kmember,kteam);
            links.kuser.set(txn,kmember,kuser);
        }
    }
    Links links;
    Chanfo chanfo;
    
    public static class Chanfo extends Table {
        /** a mapping from kchan to kteam */
        HunkArray.I kteam;
        HunkArray.L delete;
        void set(Transaction txn,int kchan,int kteam) throws Pausable {
            this.kteam.set(txn,kchan,kteam);
            this.delete.set(txn,kchan,0L);
        }
    }

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
    
    Integer addUser(Transaction txn,Users user) throws Pausable {
        Integer old = usersByName.find(txn,user.username);
        if (old != null)
            throw new BadRoute(400,"An account with that username already exists");
        int kuser = idcount.plus(txn,1);
        users.insert(txn,kuser,user);
        idmap.insert(txn,user.id,kuser);
        usersByName.insert(txn,user.username,kuser);
        status.set(txn,kuser,Tuplator.StatusEnum.away.tuple(false,0));
        return kuser;
    }
    Integer addTeam(Transaction txn,Teams team) throws Pausable {
        Integer row = teamsByName.find(txn,team.name);
        if (row != null) return null;
        int kteam = idcount.plus(txn,1);
        teams.insert(txn,kteam,team);
        teamsByName.insert(txn,team.name,kteam);
        idmap.insert(txn,team.id,kteam);
        return kteam;
    }
    boolean iseq(Object obj1,Object obj2) {
        return obj1==obj2 || obj1.equals(obj2);
    }
    int addChan(Transaction txn,Channels chan,int kteam) throws Pausable {
        int kchan = numChannels.plus(txn,1);
        channels.getall(txn).visit(cc -> {
            if (iseq(cc.val.teamId,chan.teamId) & cc.val.name.equals(chan.name))
                throw new BadRoute(500,"a channel with same url was already created");
        });
        channels.insert(txn,kchan,chan);
        idmap.insert(txn,chan.id,kchan);
        // don't add direct channels to byTeam map
        if (kteam > 0)
            chanByTeam.context().set(txn).set(kteam,kchan).insert();
        channelCounts.set(txn,kchan,0);
        chanfo.set(txn,kchan,kteam);
        return kchan;
    }
    static class RemoveChanRet {
        int kteam;
        String teamid;
    }
    RemoveChanRet removeChan(Transaction txn,String chanid) throws Pausable {
        RemoveChanRet ret = new RemoveChanRet();
        int kchan = idmap.find(txn,chanid);
        Command.RwInt kteam = chanfo.kteam.get(txn,kchan);
        long time = MatterKilim.timestamp();
        chanfo.delete.set(txn,kchan,time);
        Btrees.IK<Channels>.Range range = channels.findPrefix(txn,kchan);
        range.next();
        Teams team = teams.find(txn,kteam.val);
        ret.teamid = team.id;
        ret.kteam = kteam.val;
        range.cc.val.deleteAt = time;
        range.update();
        return ret;
    }
    // fixme - the MatterMost client apps don't support deleting a team so this method is not wired in/tested
    int removeTeam(Transaction txn,String teamid) throws Pausable {
        int kteam = idmap.find(txn,teamid);
        long time = MatterKilim.timestamp();
        links.delete.set(txn,kteam,time);
        Btrees.IK<Teams>.Range range = teams.findPrefix(txn,kteam);
        range.next();
        range.cc.val.deleteAt = time;
        range.update();
        return kteam;
    }
    int addTeamMember(Transaction txn,int kuser,int kteam,TeamMembers member) throws Pausable {
        Integer old = team2tember.find(txn,new Tuplator.Pair(kteam,kuser));
        if (old != null)
            throw new BadRoute(400,"user is already a member of team");
        int ktember = idcount.plus(txn,1);
        tembers.insert(txn,ktember,member);
        temberMap.context().set(txn).set(kuser,ktember).insert();
        team2tember.insert(txn,new Tuplator.Pair(kteam,kuser),ktember);
        links.set(txn,ktember,kuser,0,kteam);
        return ktember;
    }
    int addChanMember(Transaction txn,Integer kuser,int kchan,ChannelMembers member,int kteam) throws Pausable {
        if (kuser==null)
            kuser = idmap.find(txn,member.userId);
        Integer old = chan2cember.find(txn,new Tuplator.Pair(kchan,kuser));
        if (old != null)
            throw new BadRoute(400,"user is already a member of channel");
        int kcember = idcount.plus(txn,1);
        cembers.insert(txn,kcember,member);
        cemberMap.context().set(txn).set(kuser,kcember).insert();
        chan2cember.insert(txn,new Tuplator.Pair(kchan,kuser),kcember);
        links.set(txn,kcember,kuser,kchan,kteam);
        return kcember;
    }
    boolean removeChanMember(Transaction txn,int kuser,int kchan) throws Pausable {
        int kcember = chan2cember.remove(
                chan2cember.context().set(txn).set(new Tuplator.Pair(kchan,kuser),null)
        ).val;
        cembers.remove(cembers.context().set(txn).set(kcember,null));
        Btree.Range<Btrees.II.Data> range = cemberMap.findPrefix(cemberMap.context().set(txn).set(kuser,0));
        while (range.next())
            if (range.cc.val==kcember)
                return range.remove().match;
        System.out.println("matter:removeChanMember - not found");
        return false;
    }
    boolean removeTeamMember(Transaction txn,int kuser,int kteam) throws Pausable {
        // remove all cembers for the team/user
        ArrayList<Integer> kcembers =
                cemberMap.findPrefix(cemberMap.context().set(txn).set(kuser,0)).getall(cc -> cc.val);
        int num = kcembers.size();
        ArrayList<org.db4j.Command.RwInt> kteams = new ArrayList<>(), kchans = new ArrayList<>();
        for (int kcember : kcembers) {
            kchans.add(links.kchan.get(txn,kcember));
            kteams.add(links.kteam.get(txn,kcember));
        }
        txn.submitYield();
        for (int ii=0; ii < num; ii++)
            if (kteams.get(ii).val==kteam)
                removeChanMember(txn,kuser,kchans.get(ii).val);

        int ktember = team2tember.remove(
                team2tember.context().set(txn).set(new Tuplator.Pair(kteam,kuser),null)
        ).val;
        tembers.remove(tembers.context().set(txn).set(ktember,null));
        Btree.Range<Btrees.II.Data> range = temberMap.findPrefix(temberMap.context().set(txn).set(kuser,0));
        while (range.next())
            if (range.cc.val==ktember)
                return range.remove().match;
        return false;
    }
    int addPost(Transaction txn,int kchan,Posts post) throws Pausable {
        int kpost = channelCounts.get(txn,kchan).yield().val;
        channelCounts.set(txn,kchan,kpost+1);
        channelPosts.insert(txn,new Tuplator.Pair(kchan,kpost),post);
        idmap.insert(txn,post.id,kpost);
        return kpost;
    }
    static <KK,TT> TT [] filterArray(KK [] array,Function<Integer,TT []> alloc,Function<KK,TT> map) {
        TT [] dst = alloc.apply(array.length);
        for (int ii=0; ii < array.length; ii++) dst[ii] = map.apply(array[ii]);
        return dst;
    }

    public static class TemberArray extends ArrayList<TeamMembers> {
        Integer [] kusers;
        TemberArray(int num) { kusers = new Integer[num]; }
    }
    
    public TemberArray addUsersToTeam(Transaction txn,Integer kteam,String teamid,String ... userids) throws Pausable {
        MatterData dm = this;
        if (kteam==null)
            kteam = dm.idmap.find(txn,teamid);
        Btrees.IK<Channels>.Data town,topic;
        town = MatterData.filter(txn,dm.chanByTeam,kteam,dm.channels,chan -> {
            return "town-square".equals(chan.name);
        });
        topic = MatterData.filter(txn,dm.chanByTeam,kteam,dm.channels,chan -> {
            return "off-topic".equals(chan.name);
        });
        TemberArray result = new TemberArray(userids.length);
        for (int ii=0; ii < userids.length; ii++) {
            String userid = userids[ii];
            TeamMembers tember = MatterKilim.newTeamMember(teamid,userid);
            int kuser = dm.idmap.find(txn,userid);
            result.kusers[ii] = kuser;
            Integer ktember = dm.team2tember.find(txn,new Tuplator.Pair(kteam,kuser));
            if (ktember != null) {
                result.add(null);
                continue;
            }
            dm.addTeamMember(txn,kuser,kteam,tember);
            if (town.match)
                dm.addChanMember(txn,kuser,town.key,MatterKilim.newChannelMember(userid,town.val.id),kteam);
            if (topic.match)
                dm.addChanMember(txn,kuser,topic.key,MatterKilim.newChannelMember(userid,topic.val.id),kteam);
            result.add(tember);
        }
        return result;
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
    
    static public class Box<TT> {
        public TT val;
        public Box() {};
        public Box(TT $val) { val = $val; }
    }
    public static <TT> Box<TT> box() { return new Box(); }
    
    public static void main(String[] args) {
        MatterData dm = new MatterData();
        Db4j db4j = dm.start(resolve("./db_files/hunk.mmap"),args.length==0);
        db4j.submitCall(txn -> dm.idcount.set(txn,1)).awaitb();
        dm.shutdown(true);
    }
    
}
