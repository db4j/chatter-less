package foobar;

import foobar.MatterControl.NickInfo;
import foobar.Tuplator.HunkTuples;
import static foobar.Utilmm.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import kilim.Pausable;
import kilim.http.HttpResponse;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.data.Posts;
import mm.data.Preferences;
import mm.data.Reactions;
import mm.data.Sessions;
import mm.rest.FileInfoReps;
import mm.rest.TeamsUnreadRep;
import mm.rest.Xxx;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Command;
import org.db4j.Database;
import org.db4j.Db4j;
import org.db4j.Db4j.Transaction;
import org.db4j.HunkArray;
import org.db4j.HunkCount;
import org.db4j.TextSearchTable;
import static org.db4j.perf.DemoHunker.resolve;

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
    // (kuser,krow) -> pref
    // note: krow is not necessarily unique, eg members, channels and posts all use independent row numbering
    Tuplator.IIK<Preferences> prefs;
    HunkCount postCount;
    TextSearchTable postsIndex;
    // (kchan, kpost) -> null
    Tuplator.IIV pins;
    // kroot => kposts
    Btrees.II root2posts;
    // (kpost, kuser) -> reaction
    Tuplator.IIK<Reactions> reactions;
    Btrees.IK<UserMeta> usermeta;
    HunkCount numSessions;
    Btrees.SI sessionMap;
    Btrees.IK<Sessions> sessions;
    HunkCount numFiles;
    // file_id -> kfile
    Btrees.SI fileMap;
    Btrees.IK<FileInfoReps> files;
    // kpost -> kfile
    Btrees.II filesByPost;

    MatterControl matter;
    public MatterData(MatterControl matter) {
        this.matter = matter;
    }
    
    
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
    Postfo postfo;
    
    public static class Chanfo extends Table {
        /** a mapping from kchan to kteam */
        HunkArray.I kteam;
        HunkArray.L delete;
        HunkArray.I msgCount;
        HunkArray.L lastPostAt;
        
        // this value is duplicated in the channel structure - it's here for quicker Etag checking
        // should be set on create, update, delete and extraUpdate
        HunkArray.L modified;

        void set(Transaction txn,int kchan,int kteam,long now) throws Pausable {
            this.kteam.set(txn,kchan,kteam);
            delete.set(txn,kchan,0L);
            msgCount.set(txn,kchan,0);
            lastPostAt.set(txn,kchan,0L);
            modified.set(txn,kchan,now);
        }
    }

    public static class Postfo extends Table {
        /** a mapping from kchan to kteam */
        HunkArray.I kchan;
        HunkArray.I kteam;
        HunkArray.L delete;
        HunkArray.L update;
        HunkArray.I numReactions;
        void set(Transaction txn,int kpost,int kchan,int kteam,Posts post) throws Pausable {
            this.kchan.set(txn,kpost,kchan);
            this.kteam.set(txn,kpost,kteam);
            delete.set(txn,kpost,0L);
            update.set(txn,kpost,post.updateAt);
            numReactions.set(txn,kpost,0);
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
    

    static class UserMeta {
        byte [] digest;
    }    
    
    static <TT> ArrayList<TT> get(Transaction txn,Btrees.IK<TT> map,List<Integer> keys) throws Pausable {
        ArrayList<TT> result = new ArrayList<>();
        get(txn,map,keys,result);
        return result;
    }
    static <TT> void get(Transaction txn,Btrees.IK<TT> map,List<Integer> keys,ArrayList<TT> result) throws Pausable {
        for (Integer key : keys) {
            TT user = map.find(txn,key);
            result.add(user);
        }
    }
    Integer getk(Transaction txn,String key) throws Pausable {
        if (key==null) return null;
        return idmap.find(txn,key);
    }
    <TT> TT get(Transaction txn,Btrees.IK<TT> map,String key) throws Pausable {
        Integer kk = idmap.find(txn,key);
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

    /** check the password, returning null if it fails */
    UserMeta checkMeta(Transaction txn,int kuser,String password) throws Pausable {
        UserMeta meta = usermeta.find(txn,kuser);
        boolean good = check(password,meta.digest);
        return good ? meta:null;
    }
    
    boolean check(String password,byte [] digest) {
        byte [] salted = matter.sessioner.digest(password.getBytes(),digest);
        return Arrays.equals(salted,digest);
    }
    UserMeta salt(String password) {
        return MatterControl.set(new UserMeta(),meta ->
                meta.digest = matter.sessioner.digest(password.getBytes(),null));
    }
    Integer addUser(Transaction txn,Users user,String password) throws Pausable {
        Integer old = usersByName.find(txn,user.username);
        if (old != null)
            throw new BadRoute(400,"An account with that username already exists");
        int kuser = idcount.plus(txn,1);
        users.insert(txn,kuser,user);
        idmap.insert(txn,user.id,kuser);
        usersByName.insert(txn,user.username,kuser);
        status.set(txn,kuser,Tuplator.StatusEnum.away.tuple(false,0));
        usermeta.insert(txn,kuser,salt(password));
        return kuser;
    }
    Integer addSession(Transaction txn,Sessions session) throws Pausable {
        int ksess = numSessions.plus(txn,1);
        sessions.insert(txn,ksess,session);
        sessionMap.insert(txn,session.id,ksess);
        return ksess;
    }
    Integer addFile(Transaction txn,FileInfoReps info) throws Pausable {
        int kfile = numFiles.plus(txn,1);
        files.insert(txn,kfile,info);
        fileMap.insert(txn,info.id,kfile);
        return kfile;
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
    static String fullChannelName(int kteam,String name) {
        // fixme - this adhoc test detects group and direct channels, ie ones not tied to a team
        if (name.length() != 26)
            return ""+kteam+":"+name;
        else
            return "0:"+name;
    }
    static class Row<TT> {
        int key;
        TT val;
        public Row() {}
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
    void renameChan(Transaction txn,int kchan,Channels prev,Channels chan,int kteam) throws Pausable {
        String fullprev = fullChannelName(kteam,prev.name);
        String fullname = fullChannelName(kteam,chan.name);
        Integer k2 = chanByName.find(txn,fullname);
        if (k2 != null)
                throw new BadRoute(403,"a channel with same url was already created",HttpResponse.ST_FORBIDDEN);
        chanByName.remove(txn,fullprev);
        chanByName.insert(txn,fullname,kchan);
    }
    int addChan(Transaction txn,Channels chan,int kteam) throws Pausable {
        int kchan = numChannels.plus(txn,1);
        String fullname = fullChannelName(kteam,chan.name);
        Integer k2 = chanByName.find(txn,fullname);
        if (k2 != null)
                throw new BadRoute(403,"a channel with same url was already created",HttpResponse.ST_FORBIDDEN);
        channels.insert(txn,kchan,chan);
        chanByName.insert(txn,fullname,kchan);
        idmap.insert(txn,chan.id,kchan);
        // don't add direct channels to byTeam map
        if (kteam > 0)
            chanByTeam.context().set(txn).set(kteam,kchan).insert();
        chanfo.set(txn,kchan,kteam,chan.updateAt);
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
        long time = timestamp();
        chanfo.delete.set(txn,kchan,time);
        chanfo.modified.set(txn,kchan,time);
        Teams team = teams.find(txn,kteam.val);
        ret.teamid = team.id;
        ret.kteam = kteam.val;
        // fixme - use the deleteAt overlay instead of the object itself
        return ret;
    }
    // fixme - the MatterMost client apps don't support deleting a team so this method is not wired in/tested
    int removeTeam(Transaction txn,String teamid) throws Pausable {
        int kteam = idmap.find(txn,teamid);
        long time = timestamp();
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
    ArrayList<Row<Channels>> removeTeamMember(Transaction txn,int kuser,int kteam) throws Pausable {
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
        ArrayList<Row<Channels>> rows = new ArrayList();
        for (int ii=0; ii < num; ii++)
            if (kteams.get(ii).val==kteam) {
                int kchan = kchans.get(ii).val;
                Channels chan = removeChanMember(txn,kuser,kchan);
                rows.add(new Row(kchan,chan));
            }


        int ktember = team2tember.remove(
                team2tember.context().set(txn).set(new Tuplator.Pair(kteam,kuser),null)
        ).val;
        tembers.remove(tembers.context().set(txn).set(ktember,null));
        Btree.Range<Btrees.II.Data> range = temberMap.findPrefix(temberMap.context().set(txn).set(kuser,0));
        while (range.next())
            if (range.cc.val==ktember)
                return range.remove().match ? rows:null;
        return null;
    }
    static final ArrayList dummyList = new ArrayList();
    static final PostMetadata dummyMeta = new PostMetadata(dummyList,dummyList);
    int addPost(Transaction txn,int kchan,Posts post,PostMetadata meta) throws Pausable {
        Integer kroot = null;
        if (post.rootId != null)
            kroot = idmap.find(txn,post.rootId);
        // fixme - which of the various timestamps in the post should be used ... edit, update, create, etc
        Command.RwLong stamp = chanfo.lastPostAt.get(txn,kchan);
        Command.RwInt kpostCmd = postCount.read(txn),
                chanCountCmd = chanfo.msgCount.get(txn,kchan),
                kteamCmd = chanfo.kteam.get(txn,kchan);
        if (meta==null) meta = dummyMeta;
        ArrayList<Integer> kcembers = new ArrayList<>();
        for (NickInfo mention : meta.mentions) {
            // if a mention isn't a member of the channel, send an ephemeral message to the sender
            Integer old = chan2cember.find(txn,new Tuplator.Pair(kchan,mention.kuser));
            if (old != null)
                kcembers.add(old);
        }
        ArrayList<Command.RwInt> mentions = get(txn,links.mentionCount,kcembers);
        int kpost = kpostCmd.val;
        postCount.set(txn,kpost+1);
        chanfo.msgCount.set(txn,kchan,chanCountCmd.val+1);
        if (post.createAt > stamp.val)
            chanfo.lastPostAt.set(txn,kchan,post.createAt);
        channelPosts.insert(txn,new Tuplator.Pair(kchan,kpost),post);
        if (kroot != null)
            root2posts.context().set(txn).set(kroot,kpost).insert();
        postfo.set(txn,kpost,kchan,kteamCmd.val,post);
        postsIndex.addExact(txn,meta.tags,kpost);
        for (int ii=0; ii < meta.mentions.size(); ii++)
            links.mentionCount.set(txn,kcembers.get(ii),mentions.get(ii).val+1);
        idmap.insert(txn,post.id,kpost);
        for (String fid : post.fileIds) {
            Integer kfile = fileMap.find(txn,fid);
            filesByPost.context().set(txn).set(kpost,kfile).insert();
        }
        return kpost;
    }
    void getPostsInfo(Transaction txn,ArrayList<Row<Posts>> rows) throws Pausable {
        PostInfo [] pis = new PostInfo[rows.size()];
        for (int ii=0; ii < pis.length; ii++)
            pis[ii] = getPostInfo(txn,rows.get(ii).key);
        txn.submitYield();
        for (int ii=0; ii < pis.length; ii++)
            pis[ii].finish(txn,rows.get(ii).val,true);
    }
    Posts getPostInfo(Transaction txn,int kchan,int kpost) throws Pausable {
        PostInfo pi = getPostInfo(txn,kpost);
        Posts post = channelPosts.find(txn,new Tuplator.Pair(kchan,kpost));
        pi.finish(txn,post,true);
        return post;
    }
    static class PostMetadata {
        ArrayList<NickInfo> mentions;
        ArrayList<String> tags;

        public PostMetadata(ArrayList<NickInfo> kmentions,ArrayList<String> tags) {
            this.mentions = kmentions;
            this.tags = tags;
        }
    }
    static class PostInfo {
        Command.RwLong del, update;
        Command.RwInt num;
        void finish(Transaction txn,Posts post,boolean dontYield) throws Pausable {
            if (!dontYield) txn.submitYield();
            post.deleteAt = del.val;
            post.updateAt = update.val;
            post.hasReactions = num.val > 0;
        }
    }
    public PostInfo getPostInfo(Transaction txn,int kpost) throws Pausable {
        PostInfo pi = new PostInfo();
        pi.del = postfo.delete.get(txn,kpost);
        pi.update = postfo.update.get(txn,kpost);
        pi.num = postfo.numReactions.get(txn,kpost);
        return pi;
    }
    static <KK,TT> TT [] filterArray(KK [] array,Function<Integer,TT []> alloc,Function<KK,TT> map) {
        TT [] dst = alloc.apply(array.length);
        for (int ii=0; ii < array.length; ii++) dst[ii] = map.apply(array[ii]);
        return dst;
    }

    public static class TemberArray extends ArrayList<TeamMembers> {
        Integer [] kusers;
        Row<Channels> town;
        Row<Channels> topic;
        Teams team;
        Socketable [] sock;
        TemberArray(int num,Row<Channels> $town,Row<Channels> $topic) {
            kusers = new Integer[num];
            town = $town;
            topic = $topic;
        }
        void systemPostTember(Transaction txn,PostsTypes type,String uid,MatterData dm) throws Pausable {
            Integer kuid = dm.idmap.find(txn,uid);
            Users user = dm.users.find(txn,kuid);
            int num = kusers.length;
            sock = new Socketable[num*2];
            for (int ii=0; ii < num; ii++) {
                Users victim = dm.users.find(txn,kusers[ii]);
                sock[ii    ] = dm.systemPost(txn,type, town.key, town.val,user,victim);
                sock[ii+num] = dm.systemPost(txn,type,topic.key,topic.val,user,victim);
            }
        }
        void runSock(MatterWebsocket ws) {
            for (Socketable sockx : sock)
                sockx.run(ws);
        }
    }
    public TemberArray addUserByInviteId(Transaction txn,String userid,String inviteId) throws Pausable {
        Btrees.IK<Teams>.Data teamcc = MatterData.filter(txn,teams,tx ->
                inviteId.equals(tx.inviteId));
        Teams team = teamcc.val;
        Integer kteam = teamcc.key;
        TemberArray ta = addUsersToTeam(txn,kteam,team.id,userid);
        ta.team = team;
        ta.systemPostTember(txn,PostsTypes.system_join_channel,userid,this);
        return ta;
    }
    public TemberArray addUserByUrl(Transaction txn,String userid,String url) throws Pausable {
        Integer kteam = teamsByName.find(txn,url);
        if (kteam==null) return null;
        Teams team = teams.find(txn,kteam);
        if (team.allowOpenInvite) {
            TemberArray ta = addUsersToTeam(txn,kteam,team.id,userid);
            ta.team = team;
            ta.systemPostTember(txn,PostsTypes.system_join_channel,userid,this);
            return ta;
        }
        return null;
    }
    public TemberArray addUsersToTeam(Transaction txn,Integer kteam,String teamid,String ... userids) throws Pausable {
        MatterData dm = this;
        if (kteam==null)
            kteam = dm.idmap.find(txn,teamid);
        Row<Channels>
            town = dm.getChanByName(txn,kteam,TOWN[0]),
            topic = dm.getChanByName(txn,kteam,TOPIC[0]);
        TemberArray result = new TemberArray(userids.length,town,topic);
        for (int ii=0; ii < userids.length; ii++) {
            String userid = userids[ii];
            TeamMembers tember = newTeamMember(teamid,userid);
            int kuser = dm.idmap.find(txn,userid);
            result.kusers[ii] = kuser;
            Integer ktember = dm.team2tember.find(txn,new Tuplator.Pair(kteam,kuser));
            if (ktember != null) {
                result.add(null);
                continue;
            }
            dm.addTeamMember(txn,kuser,kteam,tember);
            if (town != null)
                dm.addChanMember(txn,kuser,town.key,newChannelMember(userid,town.val.id),kteam);
            if (topic != null)
                dm.addChanMember(txn,kuser,topic.key,newChannelMember(userid,topic.val.id),kteam);
            result.add(tember);
        }
        return result;
    }

    <TT,CC extends Command.RwPrimitive<Integer,CC>>
        ArrayList<Integer> getKuser(Transaction txn,Tuplator.III map,Integer key) throws Pausable {
        if (key==null) return null;
        ArrayList<Integer> kusers = new ArrayList<>();
        ArrayList<Integer> kmembers = map.findPrefix(txn,new Tuplator.Pair(key,true)).vals();
        int num = kmembers.size();
        ArrayList<Command.RwInt> kuserCmds = get(txn,links.kuser,kmembers);
        txn.submitYield();
        for (int ii=0; ii < num; ii++)
            kusers.add(kuserCmds.get(ii).val);
        return kusers;
    }

    static <TT,CC extends Command.RwPrimitive<TT,CC>>
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
    static <TT,CC extends Command.RwPrimitive<TT,CC>,XX>
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
        ArrayList<Integer> kcembers = getall(txn,cemberMap,kuser);
        ArrayList<Integer> ktembers = getall(txn,temberMap,kuser);
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
            cember.lastUpdateAt = timestamp();
            return cember;
        }
    }
    String check(ChannelGetter [] getters) {
        int num = getters.length;
        long [] kchans = new long[num+1];
        long last = 0;
        for (int ii=0; ii < num; ii++) {
            ChannelGetter gg = getters[ii];
            kchans[ii] = gg.kchan;
            last = Math.max(last,gg.changedAt());
        }
        kchans[num] = last;
        // this etag hash doesn't need to be super strong because chan.update should be nearly monotonic
        long hash = Arrays.hashCode(kchans);
        return Long.toUnsignedString(hash,36);
    }
    class ChannelGetter {
        Command.RwInt kteamCmd;
        Command.RwInt kchanCmd;
        Command.RwInt memberCount;
        Command.RwInt chanCount;
        Command.RwLong del;
        Command.RwLong last;
        Command.RwLong modified;
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
            modified = chanfo.modified.get(txn,kchan);
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
        long changedAt() {
            return Math.max(last.val,modified.val);
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

    Socketable systemPost(Transaction txn,PostsTypes type,int kchan,Channels chan,Object user,Object victim)
            throws Pausable {
        // fixme - it seems likely that mention counts should be incremented
        Users user1 = user instanceof Integer
                ? users.find(txn,(int) user)
                : (Users) user;
        Users victim1 = victim instanceof Integer
                ? users.find(txn,(int) victim)
                : (Users) victim;
        String vname = victim1==null ? null : victim1.username;
        Posts post = type.cember(user1.username,vname,user1.id,chan.id);
        addPost(txn,kchan,post,null);
        return ws -> {
            Xxx reply = posts2rep.copy(post);
            ws.send.posted(reply,chan,user1.username,kchan,null);
        };
    }
    
    
    
    
    
    public static void main(String[] args) {
        MatterData dm = new MatterData(null);
        Db4j db4j = dm.start(resolve("./db_files/hunk.mmap"),args.length==0);
        db4j.submitCall(txn -> dm.idcount.set(txn,1)).awaitb();
        dm.shutdown(true);
    }
    
}
