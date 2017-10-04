package foobar;

import foobar.MatterKilim.BadRoute;
import static foobar.MatterKilim.TOPIC;
import static foobar.MatterKilim.TOWN;
import foobar.Tuplator.HunkTuples;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
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
import mm.rest.TeamsUnreadRep;
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
    // kuser -> kchan
    Btrees.II groupByUser;
    // "kteam:name" -> kchan
    Btrees.SI chanByName;
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
    HunkCount numChannels;
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
        HunkArray.I msgCount;
        HunkArray.I mentionCount;
        
        void set(Transaction txn,int kmember,int kuser,int kchan,int kteam) throws Pausable {
            Links links = this;
            links.kchan.set(txn,kmember,kchan);
            links.kteam.set(txn,kmember,kteam);
            links.kuser.set(txn,kmember,kuser);
            links.delete.set(txn,kmember,0L);
            links.msgCount.set(txn,kmember,0);
            links.mentionCount.set(txn,kmember,0);
        }
    }
    Links links;
    Chanfo chanfo;
    
    public static class Chanfo extends Table {
        /** a mapping from kchan to kteam */
        HunkArray.I kteam;
        HunkArray.L delete;
        HunkArray.I msgCount;
        HunkArray.L lastPostAt;
        void set(Transaction txn,int kchan,int kteam) throws Pausable {
            this.kteam.set(txn,kchan,kteam);
            delete.set(txn,kchan,0L);
            msgCount.set(txn,kchan,0);
            lastPostAt.set(txn,kchan,0L);
        }
    }

    // direct channels
    // channel count: total messages
    // member mention: messages to that member not yet viewed
    // member count: viewed messages (including sent messages)
    // to add a post, increment the receiver cember mention count (and the chan total count)
    
    
    // public channels
    // channel total: all
    // member msg count: viewed messages
    // mention count: number of unviewed mentions
    // to add a post, increment the chan total count and any mentioned cembers mention count
    
    // for both
    // on view, copy chan count to cember count, zero mention count
    
    static enum PostsTypes {
        system_add_to_channel,
        system_remove_from_channel,
        system_join_channel,
        system_header_change,
        system_channel_deleted,
        system_displayname_change,
        system_leave_channel,
        system_purpose_change;
    }
    

    <TT> ArrayList<TT> get(Transaction txn,Btrees.IK<TT> map,List<Integer> keys) throws Pausable {
        ArrayList<TT> result = new ArrayList<>();
        get(txn,map,keys,result);
        return result;
    }
    <TT> void get(Transaction txn,Btrees.IK<TT> map,List<Integer> keys,ArrayList<TT> result) throws Pausable {
        for (Integer key : keys) {
            TT user = map.find(txn,key);
            result.add(user);
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
    String fullChannelName(int kteam,String name) {
        return ""+kteam+":"+name;
    }
    static class Row<TT> {
        int key;
        TT val;
        public Row(int key,TT val) {
            this.key = key;
            this.val = val;
        }
    }
    Row<Channels> getChanByName(Transaction txn,int kteam,String name) throws Pausable {
        String fullname = fullChannelName(kteam,name);
        Integer kchan = chanByName.find(txn,fullname);
        if (kchan==null)
            return null;
        Channels chan = getChan(txn,kchan);
        return new Row(kchan,chan);
    }
    Channels getChan(Transaction txn,int kchan) throws Pausable {
        return new ChannelGetter(txn).first(kchan).get(null,0);
    }
    int addChan(Transaction txn,Channels chan,int kteam) throws Pausable {
        int kchan = numChannels.plus(txn,1);
        String fullname = fullChannelName(kteam,chan.name);
        Integer k2 = chanByName.find(txn,fullname);
        if (k2 != null)
                throw new BadRoute(500,"a channel with same url was already created");
        channels.insert(txn,kchan,chan);
        chanByName.insert(txn,fullname,kchan);
        idmap.insert(txn,chan.id,kchan);
        // don't add direct channels to byTeam map
        if (kteam > 0)
            chanByTeam.context().set(txn).set(kteam,kchan).insert();
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
        // fixme - use the deleteAt overlay instead of the object itself
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
    Channels removeChanMember(Transaction txn,int kuser,int kchan) throws Pausable {
        Channels chan = channels.find(txn,kchan);
        int kcember = chan2cember.remove(
                chan2cember.context().set(txn).set(new Tuplator.Pair(kchan,kuser),null)
        ).val;
        cembers.remove(cembers.context().set(txn).set(kcember,null));
        Btree.Range<Btrees.II.Data> range = cemberMap.findPrefix(cemberMap.context().set(txn).set(kuser,0));
        while (range.next())
            if (range.cc.val==kcember)
                return range.remove().match ? chan:null;
        System.out.println("matter:removeChanMember - not found");
        return null;
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
    int addPost(Transaction txn,int kchan,Posts post,ArrayList<Integer> kmentions) throws Pausable {
        // fixme - which of the various timestamps in the post should be used ... edit, update, create, etc
        Command.RwLong stamp = chanfo.lastPostAt.get(txn,kchan);
        ArrayList<Integer> kcembers = new ArrayList<>();
        for (Integer kmention : kmentions) {
            Integer old = chan2cember.find(txn,new Tuplator.Pair(kchan,kmention));
            if (old != null)
                kcembers.add(old);
        }
        ArrayList<Command.RwInt> mentions = get(txn,links.mentionCount,kcembers);
        int kpost = chanfo.msgCount.get(txn,kchan).yield().val;
        chanfo.msgCount.set(txn,kchan,kpost+1);
        if (post.createAt > stamp.val)
            chanfo.lastPostAt.set(txn,kchan,post.createAt);
        channelPosts.insert(txn,new Tuplator.Pair(kchan,kpost),post);
        for (int ii=0; ii < kmentions.size(); ii++)
            links.mentionCount.set(txn,kcembers.get(ii),mentions.get(ii).val+1);
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
        Row<Channels>
            town = dm.getChanByName(txn,kteam,TOWN[0]),
            topic = dm.getChanByName(txn,kteam,TOPIC[0]);
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
            if (town != null)
                dm.addChanMember(txn,kuser,town.key,MatterKilim.newChannelMember(userid,town.val.id),kteam);
            if (topic != null)
                dm.addChanMember(txn,kuser,topic.key,MatterKilim.newChannelMember(userid,topic.val.id),kteam);
            result.add(tember);
        }
        return result;
    }

    <TT,CC extends Command.RwPrimitive<Integer,CC>>
        ArrayList<Integer> getKuser(Transaction txn,Tuplator.III map,Integer key) throws Pausable {
        ArrayList<Integer> kusers = new ArrayList<>();
        ArrayList<Integer> kmembers = map.findPrefix(txn,new Tuplator.Pair(key,true)).vals();
        int num = kmembers.size();
        ArrayList<Command.RwInt> kuserCmds = get(txn,links.kuser,kmembers);
        txn.submitYield();
        for (int ii=0; ii < num; ii++)
            kusers.add(kuserCmds.get(ii).val);
        return kusers;
    }

    <TT,CC extends Command.RwPrimitive<TT,CC>>
        ArrayList<CC> get(Transaction txn,HunkArray<TT,CC,?> map,ArrayList<Integer> indices) throws Pausable {
        ArrayList<CC> list = new ArrayList<>();
        for (Integer index : indices)
            list.add(map.get(txn,index));
        return list;
    }

        /**
         * get a collection of values from a db4j array using a collection of objects and a mapping of those
         * objects to array indices
         * @param <TT> the array key type
         * @param <CC> the array command type
         * @param <XX> the type of the keys
         * @param txn the transaction
         * @param array the db4j array component
         * @param keys objects that can be mapped to indices
         * @param keyifier function to convert the keys to indices
         * @return
         * @throws Pausable 
         */
    <TT,CC extends Command.RwPrimitive<TT,CC>,XX>
        ArrayList<CC> get(Transaction txn,HunkArray<TT,CC,?> array,ArrayList<XX> keys,Function<XX,Integer> keyifier) throws Pausable {
        ArrayList<CC> list = new ArrayList<>();
        for (XX key : keys) {
            int index = keyifier==null ? (Integer) key:keyifier.apply(key);
            list.add(array.get(txn,index));
        }
        return list;
    }
        
    public Collection<TeamsUnreadRep> calcUnread(Transaction txn,String userid) throws Pausable {
        int kuser = idmap.find(txn,userid);
        ArrayList<Integer> kcembers = MatterKilim.getall(txn,cemberMap,kuser);
        ArrayList<Integer> ktembers = MatterKilim.getall(txn,temberMap,kuser);
        int nc = kcembers.size(), nt = ktembers.size();
        ArrayList<Command.RwInt>
                kteams = get(txn,links.kteam,kcembers),
                kchans = get(txn,links.kchan,kcembers),
                memberCounts = get(txn,links.msgCount,kcembers);
        ArrayList<Command.RwLong>
                dels = get(txn,links.delete,kcembers);
        txn.submitYield();
        ArrayList<Command.RwInt> chanCounts = get(txn,chanfo.msgCount,kchans,cmd -> cmd.val);
        txn.submitYield();
        LinkedHashMap<Integer,TeamsUnreadRep> map = new LinkedHashMap<>();
        for (int ii=0; ii < nc; ii++) {
            if (dels.get(ii).val > 0) continue;
            int kt = kteams.get(ii).val;
            TeamsUnreadRep rep = map.get(kt);
            int kteam = kteams.get(ii).val;
            if (rep==null & kteam > 0) {
                rep = new TeamsUnreadRep();
                Teams team = teams.find(txn,kteam);
                rep.teamId = team.id;
                map.put(kt,rep);
            }
            // fixme - sniff packets to see if direct messages should be accounted for somewhere ...
            if (kteam > 0)
                rep.msgCount += chanCounts.get(ii).val - memberCounts.get(ii).val;
        }
        return map.values();
    }

    
    class CemberGetter {
        Command.RwInt kteamCmd;
        Command.RwInt kchan;
        Command.RwInt memberCount;
        Command.RwInt mentionCount;
        Command.RwLong del;
        int kcember;
        Transaction txn;
        ChannelMembers cember;

        public CemberGetter(Transaction txn,int kcember) { this.txn = txn; this.kcember = kcember; }
        CemberGetter mid() throws Pausable { txn.submitYield(); return this; }
        CemberGetter prep() throws Pausable {
            kteamCmd = links.kteam.get(txn,kcember);
            kchan = links.kchan.get(txn,kcember);
            memberCount = links.msgCount.get(txn,kcember);
            mentionCount = links.mentionCount.get(txn,kcember);
            del = links.delete.get(txn,kcember);
            return this;
        }
        ChannelMembers get(Integer kteam) throws Pausable {
            boolean isteam = kteam==null || (kteam==kteamCmd.val | kteamCmd.val==0);
            if (!isteam | del.val > 0)
                return null;
            cember = cembers.find(txn,kcember);
            cember.msgCount = memberCount.val;
            cember.mentionCount = mentionCount.val;
            // fixme - need to store the true update time
            cember.lastUpdateAt = MatterKilim.timestamp();
            return cember;
        }
    }
    class ChannelGetter {
        Command.RwInt kteamCmd;
        Command.RwInt kchanCmd;
        Command.RwInt memberCount;
        Command.RwInt chanCount;
        Command.RwLong del;
        Command.RwLong last;
        Integer kchan;
        Transaction txn;
        ChannelMembers cember;

        public ChannelGetter(Transaction txn) { this.txn = txn; }
        ChannelGetter mid() throws Pausable { txn.submitYield(); return this; }
        ChannelGetter prep(int kcember) throws Pausable {
            kteamCmd = links.kteam.get(txn,kcember);
            kchanCmd = links.kchan.get(txn,kcember);
            return this;
        }
        ChannelGetter first(Integer $kchan) throws Pausable {
            if ($kchan==null)
                kchan = kchanCmd.val;
            else
                kchan = $kchan;
            del = chanfo.delete.get(txn,(long) kchan);
            last = chanfo.lastPostAt.get(txn,kchan);
            chanCount = chanfo.msgCount.get(txn,kchan);
            return this;
        }
        Channels get(Integer kteam,int min) throws Pausable {
            boolean isteam = kteam==null || (kteam==kteamCmd.val | kteamCmd.val==0);
            boolean islive = del.val==0 & chanCount.val >= min;
            if (!isteam | !islive)
                return null;
            Channels chan = channels.find(txn,kchan);
            chan.deleteAt = del.val;
            chan.lastPostAt = last.val;
            chan.totalMsgCount = chanCount.val;
            return chan;
        }
    }

    public ChannelMembers getCember(Transaction txn,int kcember) throws Pausable {
        return new CemberGetter(txn,kcember).prep().mid().get(null);
    }
    
    public ArrayList<ChannelMembers> calcChannelUnreads(
            Transaction txn,ArrayList<Integer> kcembers,String teamid) throws Pausable {
        Integer kteam = teamid==null ? null:idmap.find(txn,teamid);
        ArrayList<ChannelMembers> result = new ArrayList<>();
        CemberGetter [] getters = new CemberGetter[kcembers.size()];
        for (int ii=0; ii < getters.length; ii++)
            getters[ii] = new CemberGetter(txn,kcembers.get(ii)).prep();
        txn.submitYield();
        for (int ii=0; ii < getters.length; ii++)
            result.add(getters[ii].get(kteam));
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
    static public class Ibox {
        public int val;
        public Ibox() {};
        public Ibox(int $val) { val = $val; };
    }
    public static Ibox ibox() { return new Ibox(); }
    
    public static void main(String[] args) {
        MatterData dm = new MatterData();
        Db4j db4j = dm.start(resolve("./db_files/hunk.mmap"),args.length==0);
        db4j.submitCall(txn -> dm.idcount.set(txn,1)).awaitb();
        dm.shutdown(true);
    }
    
}
