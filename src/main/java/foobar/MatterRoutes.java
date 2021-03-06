package foobar;

import static foobar.MatterControl.*;
import static foobar.Utilmm.req2users;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Consumer;
import kilim.Pausable;
import mm.data.ChannelMembers;
import mm.data.Channels;
import mm.data.Posts;
import mm.data.Preferences;
import mm.data.Reactions;
import mm.data.Sessions;
import mm.data.TeamMembers;
import mm.data.Teams;
import mm.data.Users;
import mm.rest.ChannelsMembersxViewReqs;
import mm.rest.ChannelsReps;
import mm.rest.ChannelsReqs;
import mm.rest.ChannelsxMembersReps;
import mm.rest.ChannelsxMembersReqs;
import mm.rest.ChannelsxStatsReps;
import mm.rest.LicenseClientFormatOldReps;
import mm.rest.NotifyUsers;
import mm.rest.PreferencesSaveReq;
import mm.rest.Reaction;
import mm.rest.TeamsAddUserToTeamFromInviteReqs;
import mm.rest.TeamsMembersRep;
import mm.rest.TeamsNameExistsReps;
import mm.rest.TeamsReps;
import mm.rest.TeamsReqs;
import mm.rest.TeamsxChannelsSearchReqs;
import mm.rest.TeamsxChannelsxPostsCreateReqs;
import mm.rest.TeamsxChannelsxPostsPage060Reps;
import mm.rest.TeamsxChannelsxPostsUpdateReqs;
import mm.rest.TeamsxChannelsxPostsxDeleteRep;
import mm.rest.TeamsxMembersBatchReq;
import mm.rest.TeamsxPostsSearchReqs;
import mm.rest.TeamsxStatsReps;
import mm.rest.User;
import mm.rest.UsersAutocompleteInTeamInChannelNameSeReps;
import mm.rest.UsersLogin4Reqs;
import mm.rest.UsersLoginReqs;
import mm.rest.UsersPassword;
import mm.rest.UsersReps;
import mm.rest.UsersReqs;
import mm.rest.UsersSearchReqs;
import mm.rest.UsersStatusIdsRep;
import mm.rest.Xxx;
import org.db4j.Btree;
import org.db4j.Btrees;
import org.db4j.Command;
import org.srlutils.Simple;
import static foobar.Utilmm.*;
import foobar.MatterData.TemberArray;
import foobar.MatterKilim.AuthRouter;
import static foobar.Utilmm.PostsTypes.*;
import kilim.http.HttpResponse;
import mm.rest.FileInfoReps;
import org.db4j.Db4j.Transaction;
import kilim.http.KilimMvc;

public class MatterRoutes extends AuthRouter<MatterRoutes> {
    static Routes routes = new Routes();

    MatterRoutes(KilimMvc.Clerk mk) { super(mk); }

    { make0(new KilimMvc.Route(),self -> self::fallback); }
    Object fallback() throws Pausable {
        System.out.println("matter.fallback: " + req);
        return new int[0];
    }

    { make1(routes.fileGet,self -> self::fileGet); }
    public Object fileGet(String fileId) throws IOException, Pausable {
        FileInfoReps info = select(txn -> {
            Integer kfile = dm.fileMap.find(txn,fileId);
            return kfile==null ? null:dm.files.find(txn,kfile);
        });
        if (info==null)
            throw new BadRoute(400,"file not found");
        String filename = makeFilename(fileId);
        File file = new File(filename);
        cacheControl(resp,1.13*365*24*3600);
        session.sendFile(req,resp,file,info.mimeType);
        return null;
    }

    { make1(routes.fileGetThumb,self -> self::fileGetThumb); }
    public Object fileGetThumb(String fileId) throws IOException, Pausable {
        String filename = makeFilename(fileId+"_thumb.jpg");
        File file = new File(filename);
        cacheControl(resp,1.13*365*24*3600);
        // fixme - on error send a proper response and don't set cache
        session.sendFile(req,resp,file,"image/jpeg");
        return null;
    }

    { make1(routes.filePreview,self -> self::fileGetPreview); }
    public Object fileGetPreview(String fileId) throws IOException, Pausable {
        String filename = makeFilename(fileId+"_preview.jpg");
        File file = new File(filename);
        cacheControl(resp,1.13*365*24*3600);
        session.sendFile(req,resp,file,"image/jpeg");
        return null;
    }

    { make3(routes.fileInfos,self -> self::fileInfos); }
    public Object fileInfos(String teamid,String chanid,String postid) throws IOException, Pausable {
        ArrayList<FileInfoReps> infos = new ArrayList();
        call(txn -> {
            Integer kpost = dm.idmap.find(txn,postid);
            Btree.Range<Btrees.II.Data> range =
                    dm.filesByPost.findPrefix(dm.filesByPost.context().set(txn).set(kpost,0));
            while (range.next()) {
                FileInfoReps info = dm.files.find(txn,range.cc.val);
                infos.add(info);
            }
        });
        return infos;
    }

    { make0(routes.config,self -> self::config); }
    public Object config() throws IOException, Pausable {
        File file = new File("data/config.json");
        session.sendFile(req,resp,file,"application/json");
        return null;
    }

    { make0(mapping("POST",routes.users),self -> self::users); }
    public Object users() throws Pausable {
        UsersReqs ureq = body(UsersReqs.class);
        String iid = req.getQueryComponents().get("iid");
        
        // fixme::non-standard-ui - if the subdomain is a open team name, auto join it
        //   should eventually either document this or remove the functionality
        //   but for now it's convenient for testing
        String sub = subdomain(req.getHeader("Host"));
        Users u = req2users.copy(ureq,new Users());
        u.id = newid();
        u.password = null;
        u.updateAt = u.lastPasswordUpdate = u.createAt = timestamp();
        u.roles = "system_user";
        u.notifyProps = null; // new NotifyUsers().init(rep.username);
        u.locale = "en";
        Tuple2<Integer,TemberArray> tuple = select(txn -> {
            int kuser = dm.addUser(txn,u,ureq.password);
            TemberArray ta = null;
            if (! iid.isEmpty())
                ta = dm.addUserByInviteId(txn,u.id,iid);
            else if (sub != null)
                ta = dm.addUserByUrl(txn,u.id,sub);
            return new Tuple2(kuser,ta);
        });
        matter.addNicks(u,tuple.v1);
        ws.send.newUser(u.id);
        if (tuple.v2 != null)
            tuple.v2.runSock(ws);
        return users2reps.copy(u);
    }
    { make1(mapping("PUT",routes.password),self -> self::password); }
    public Object password(String userid) throws Pausable {
        UsersPassword ureq = body(UsersPassword.class);
        boolean result = select(txn -> {
            int kuser = dm.idmap.find(txn,userid);
            MatterData.UserMeta meta = dm.usermeta.find(txn,kuser);
            boolean good = dm.check(ureq.currentPassword,meta.digest);
            if (good) {
                dm.usermeta.update(txn,kuser,dm.salt(ureq.newPassword));
                dm.users.update(txn,kuser,user -> {
                    user.updateAt = user.lastPasswordUpdate = timestamp();
                });
            }
            return good;
        });
        if (!result)
            throw new Utilmm.BadRoute(400,"The \"Current Password\" you entered is incorrect. "
                    + "Please check that Caps Lock is off and try again.");
        return set(new ChannelsReps.View(),rep -> rep.status="OK");
    }

    { make0(routes.login,self -> self::login); }
    { make0(routes.login4,self -> self::login); }
    public Object login() throws Pausable {
        boolean v4 = req.uriPath.equals(routes.login4);
        UsersLoginReqs login = v4 ? null : body(UsersLoginReqs.class);
        UsersLogin4Reqs login4 = !v4 ? null : body(UsersLogin4Reqs.class);
        String password = v4 ? login4.password : login.password;
        Utilmm.Box<MatterData.UserMeta> meta = new Utilmm.Box();
        Utilmm.Box<Sessions> session = new Utilmm.Box();
        Users user = select(txn -> {
            Integer row;
            if (login4==null)
                row = dm.idmap.find(txn,login.id);
            else row = dm.usersByName.find(txn,login4.loginId);
            if (row==null) {
                matter.print(login);
                matter.print(login4);
                return null;
            }
            meta.val = dm.checkMeta(txn,row,password);
            Users u2 = dm.users.find(txn,row);
            if (meta.val != null)
                dm.addSession(txn,session.val = newSession(u2.id));
            return u2;
        });
        if (user==null)
            throw new Utilmm.BadRoute(400,"We couldn't find an existing account matching your credentials. "
                    + "This team may require an invite from the team owner to join.");
        else if (meta.val==null)
            throw new Utilmm.BadRoute(401,"Login failed because of invalid password",HttpResponse.ST_UNAUTHORIZED);
        // fixme::fakeSecurity - add auth token (and check for it on requests)
        String token = session.val.id;
        setCookie(resp,matter.mmuserid,user.id,30.0,false);
        setCookie(resp,matter.mmauthtoken,token,30.0,true);
        resp.addField("Token",token);
        return users2reps.copy(user);
    }

    { make0(routes.logout,self -> self::logout); }

    { make0(routes.um,self -> self::um); }
    public Object um() throws Pausable {
        Users user = select(txn -> {
            Integer row = dm.idmap.find(txn,uid);
            return row==null ? null : dm.users.find(txn,row);
        });
        if (user==null)
            throw new Utilmm.BadRoute(400,"user not found");
        return users2reps.copy(user);
    }        

    { make0(mapping("POST",routes.usersIds),self -> self::getUsersByIds); }
    public Object getUsersByIds() throws Pausable {
        String [] userids = body(String [].class);
        ArrayList<Users> users = new ArrayList();
        db4j.submitCall(txn -> {
            for (String userid : userids)
                users.add(dm.get(txn,dm.users,userid));
        }).await();
        return map(users,users2userRep::copy,Utilmm.HandleNulls.skip);
    }

    { make1(routes.ux,self -> self::ux); }
    public Object ux(String userid) throws Pausable {
        Users user = select(txn -> {
            Integer row = dm.idmap.find(txn,userid);
            return row==null ? null : dm.users.find(txn,row);
        });
        if (user==null)
            throw new Utilmm.BadRoute(400,"user not found");
        return users2reps.copy(user);
    }        

    { make1(routes.teamsMe,self -> self::teamsMe); }
    public Object teamsMe(String teamid) throws Pausable {
        Integer kteam = get(dm.idmap,teamid);
        Teams team = select(txn -> dm.teams.find(txn,kteam));
        return team2reps.copy(team);
    }        

    { make0(routes.umt,self -> self::umt); }
    public Object umt() throws Pausable {
        Integer kuser = get(dm.idmap,uid);
        ArrayList<Teams> teams = new ArrayList();
        db4j.submitCall(txn -> {
            Btree.Range<Btrees.II.Data> range = prefix(txn,dm.temberMap,kuser);
            while (range.next()) {
                TeamMembers tember = dm.tembers.find(txn,range.cc.val);
                Integer kteam = dm.idmap.find(txn,tember.teamId);
                teams.add(dm.teams.find(txn,kteam));
            }
        }).await();
        return map(teams,team -> team2reps.copy(team),Utilmm.HandleNulls.skip);
    }        

    { make1(routes.txmi,self -> self::txmi); }
    public Object txmi(String teamid) throws Pausable {
        String [] userids = body(String [].class);
        Integer kteam = get(dm.idmap,teamid);
        ArrayList<TeamMembers> tembers = new ArrayList();


        Utilmm.Spawner<TeamsMembersRep> tasker = new Utilmm.Spawner();
        for (String userid : userids) tasker.spawn(() -> {
            // userid -> kuser -(temberMap)-> ktembers -(filter)-> reply
            Integer kuser = get(dm.idmap,userid);
            TeamMembers tember = select(txn ->
                    dm.filter(txn,dm.temberMap,kuser,dm.tembers,t -> t.teamId.equals(teamid))
            ).val;
            return tember2reps.copy(tember);
        });
        return tasker.join();
    }

    { make1(routes.cxmi,self -> self::cxmi); }
    public Object cxmi(String chanid) throws Pausable {
        String [] userids = body(String [].class);
        Integer kchan = get(dm.idmap,chanid);
        ArrayList<ChannelMembers> tembers = new ArrayList();


        Utilmm.Spawner<ChannelsxMembersReps> tasker = new Utilmm.Spawner();
        for (String userid : userids) tasker.spawn(() -> {
            // userid -> kuser -(temberMap)-> ktembers -(filter)-> reply
            Integer kuser = get(dm.idmap,userid);
            ChannelMembers cember = select(txn ->
                    dm.filter(txn,dm.cemberMap,kuser,dm.cembers,t -> t.channelId.equals(chanid))
            ).val;
            return cember2reps.copy(cember);
        });
        return tasker.join();
    }

    { make1(mapping("POST",routes.cxm),self -> self::joinChannel); }
    public Object joinChannel(String chanid) throws Pausable {
        ChannelsxMembersReqs info = body(ChannelsxMembersReqs.class);
        String userid = info.userId;
        boolean self = userid.equals(uid);
        PostsTypes type = self ? PostsTypes.system_join_channel : PostsTypes.system_add_to_channel;
        boolean direct = info.channelId==null;
        Simple.softAssert(direct || chanid.equals(info.channelId),
                "if these ever differ need to determine which one is correct: %s vs %s",
                chanid, info.channelId);
        ChannelMembers cember = newChannelMember(userid,chanid);
        MatterData.Row<Channels> chan = new MatterData.Row();
        Box<Socketable> box = new Box();
        ChannelMembers result = select(txn -> {
            Integer kuser = dm.idmap.find(txn,userid);
            Integer kuid = self ? kuser : dm.idmap.find(txn,uid);
            Integer kchan = chan.key = dm.idmap.find(txn,chanid);
            Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan,kuser));
            if (kcember != null)
                return dm.cembers.find(txn,kcember);
            chan.val = dm.getChan(txn,kchan);
            Integer kteam;
            if (direct)
                kteam = 0;
            else {
                kteam = dm.idmap.find(txn,chan.val.teamId);
                box.val = dm.systemPost(txn,type,kchan,chan.val,kuid,kuser);
            }
            dm.addChanMember(txn,kuser,kchan,cember,kteam);
            return cember;
        });
        if (chan.val != null)
            ws.send.userAdded(chan.val.teamId,userid,chanid,chan.key);
        if (box.val != null)
            box.val.run(ws);
        return cember2reps.copy(result);
    }

    { make2(mapping("DELETE",routes.cxmx),self -> self::leaveChannel); }
    public Object leaveChannel(String chanid,String userid) throws Pausable {
        PostsTypes type = userid.equals(uid)
                ? PostsTypes.system_leave_channel
                : PostsTypes.system_remove_from_channel;
        Box<Runnable> remove = new Box();
        Box<Socketable> post = new Box();
        call(txn -> {
            Integer kuid = dm.idmap.find(txn,uid);
            Integer kuser = dm.idmap.find(txn,userid);
            Integer kchan = dm.idmap.find(txn,chanid);
            Channels chan2 = dm.removeChanMember(txn,kuser,kchan);
            remove.val = isOpenGroup(chan2)
                ? () -> ws.send.userRemoved(uid,userid,chanid,kchan,kuser)
                : () -> ws.send.userRemovedPrivate(uid,userid,chanid,kuser);
            if (isFull(chan2))
                post.val = dm.systemPost(txn,type,kchan,chan2,kuid,kuser);
        });
        remove.val.run();
        if (post.val != null)
            post.val.run(ws);
        return set(new ChannelsReps.View(),x->x.status="OK");
    }

    { make2(mapping("DELETE",routes.txmx),self -> self::leaveTeam); }
    public Object leaveTeam(String teamid,String userid) throws Pausable {
        PostsTypes type = userid.equals(uid)
                ? PostsTypes.system_leave_channel
                : PostsTypes.system_remove_from_channel;
        Box<Runnable> box = new Box();
        ArrayList<Socketable> socks = new ArrayList();
        call(txn -> {
            Integer kuid = dm.idmap.find(txn,uid);
            Integer kuser = dm.idmap.find(txn,userid);
            Integer kteam = dm.idmap.find(txn,teamid);
            box.val = () -> ws.send.leaveTeam(userid,teamid,kteam,kuser);
            ArrayList<MatterData.Row<Channels>> rows = dm.removeTeamMember(txn,kuser,kteam);
            if (rows==null)
                throw new BadRoute(404,"team member not found");
            Users user = dm.users.find(txn,kuid);
            Users victim = dm.users.find(txn,kuser);
            for (int ii = 0; ii < rows.size(); ii++)
                socks.add(
                        dm.systemPost(txn,type,rows.get(ii).key,rows.get(ii).val,user,victim));
        });
        box.val.run();
        for (int ii = 0; ii < socks.size(); ii++)
            socks.get(ii).run(ws);
        return set(new ChannelsReps.View(),x->x.status="OK");
    }

    { make0(routes.oldTembers,self -> self::umtm); }
    { make0(routes.umtm,self -> self::umtm); }
    public Object umtm() throws Pausable {
        Integer kuser = get(dm.idmap,uid);
        ArrayList<TeamMembers> tembers = new ArrayList();
        db4j.submitCall(txn -> {
            Btree.Range<Btrees.II.Data> range = prefix(txn,dm.temberMap,kuser);
            while (range.next())
                tembers.add(dm.tembers.find(txn,range.cc.val));
        }).await();
        return map(tembers,team -> tember2reps.copy(team),Utilmm.HandleNulls.skip);
    }        

    // fixme - instead of checking the uri, store the route and compare
    { make1(mapping("PUT",routes.patchChannel),self -> self::updateChannel); }
    { make1(mapping("PUT",routes.cx),self -> self::updateChannel); }
    public Object updateChannel(String chanid) throws Pausable {
        ChannelsReqs body = body(ChannelsReqs.class);
        Box<Posts> post = new Box();
        Box<Users> user = new Box();
        Box<Channels> prev = new Box();
        Ibox kchan = new Ibox();
        
        // fixme::ExtraUpdateAt - appears to be the time of the most recent system message event
        // select *, abs(ExtraUpdateAt-UpdateAt) as delta from Channels having delta > 1000 limit 1;
        // select Message, UpdateAt, GREATEST (CreateAt, UpdateAt)-1510024482800 as delta from Posts where ChannelId="1aunawoeqtyq7gz6eoyfsj8hwa" order by UpdateAt ;

        
        long timestamp = timestamp();
        Channels result = select(txn -> {
            kchan.val = dm.idmap.find(txn,chanid);
            Command.RwInt kteam = dm.chanfo.kteam.get(txn,kchan.val);
            Integer kuser = dm.idmap.find(txn,uid);
            user.val = dm.users.find(txn,kuser);
            Channels update = dm.channels.update(txn,kchan.val,chan -> {
                prev.val = chan2chan.copy(chan);
                if (null != body.displayName) chan.displayName = body.displayName;
                if (null != body.header)      chan.header = body.header;
                if (null != body.purpose)     chan.purpose = body.purpose;
                if (null != body.name)        chan.name = body.name;
                chan.updateAt = timestamp;
            }).val;
            
            dm.chanfo.modified.set(txn,kchan.val,timestamp);

            if (! prev.val.name.equals(update.name))
                dm.renameChan(txn,kchan.val,prev.val,update,kteam.val);

            postSystem(txn,kchan.val,system_displayname_change,post,chanid,user.val.username,prev.val,update);
            postSystem(txn,kchan.val,system_header_change     ,post,chanid,user.val.username,prev.val,update);
            postSystem(txn,kchan.val,system_purpose_change    ,post,chanid,user.val.username,prev.val,update);
            return update;
        });
        Xxx reply = posts2rep.copy(post.val);
        ws.send.posted(reply,result,user.val.username,kchan.val,null);
        return chan2reps.copy(result);
    }
    private void postSystem(Transaction txn,int kchan,PostsTypes type,Box<Posts> post,String chanid,String username,Channels v0,Channels v1) throws Pausable {
        if (type.changed(v0,v1)) {
            post.val = type.gen(uid,chanid,username,v0,v1);
            dm.addPost(txn,kchan,post.val,null);
        }
    }
    
    
    { make1(mapping("DELETE",routes.cx),self -> self::deleteChannel); }
    public Object deleteChannel(String chanid) throws Pausable {
        MatterData.RemoveChanRet info = select(txn -> dm.removeChan(txn,chanid));
        ws.send.channelDeleted(chanid,info.teamid,info.kteam);
        return set(new ChannelsReps.View(),x->x.status="OK");
    }

    { make1(mapping("GET",routes.cx),self -> self::cx); }
    public Object cx(String chanid) throws Pausable {
        Channels chan = select(txn -> {
            int kchan = dm.idmap.find(txn,chanid);
            return dm.getChan(txn,kchan);
        });
        return chan2reps.copy(chan);
    }

    { make1(mapping("POST",routes.txcSearch),self -> self::searchChannels); }
    public Object searchChannels(String teamid) throws Pausable {
        TeamsxChannelsSearchReqs body = body(TeamsxChannelsSearchReqs.class);
        ArrayList<Channels> channels = new ArrayList<>();
        Integer kteam = get(dm.idmap,teamid);
        String name = dm.fullChannelName(kteam,body.term);
        db4j.submitCall(txn -> {
            ArrayList<Integer> kchans = dm.chanByName.findPrefix(txn,name).getall(cc -> cc.val);
            for (int kchan : kchans) {
                Channels chan = dm.getChan(txn,kchan);
                channels.add(chan);
            }
        });
        return map(channels,chan2reps::copy,Utilmm.HandleNulls.skip);
    }

    { make2(mapping("GET",routes.txcName),self -> self::namedChannel); }
    public Object namedChannel(String teamid,String name) throws Pausable {
        // fixme:mmapi - only see this being used for direct channels, which aren't tied to a team ...
        Channels chan = select(txn -> {
            Integer kteam = teamid.length()==0 ? 0:dm.idmap.find(txn,teamid);
            return dm.getChanByName(txn,kteam,name).val;
        });
        return chan2reps.copy(chan);
    }

    { make1(routes.txmBatch,self -> self::txmBatch); }
    public Object txmBatch(String teamid) throws Pausable {
        TeamsxMembersBatchReq [] batch = body(TeamsxMembersBatchReq[].class);
        int num = batch.length;
        String [] userids = dm.filterArray(batch,String []::new,x -> x.userId);
        MatterData.TemberArray tembers = select(txn -> {
            TemberArray ta = dm.addUsersToTeam(txn,null,teamid,userids);
            ta.systemPostTember(txn,PostsTypes.system_add_to_channel,uid,dm);
            return ta;
        });
        for (int ii=0; ii < num; ii++) {
            TeamMembers tember = tembers.get(ii);
            Integer kuser = tembers.kusers[ii];
            if (tember != null)
                ws.send.addedToTeam(kuser,tember.teamId,tember.userId);
        }
        tembers.runSock(ws);
        return map(tembers,tember -> tember2reps.copy(tember),Utilmm.HandleNulls.skip);
    }


    { make0(routes.invite,self -> self::invite); }
    public Object invite() throws Pausable {
        TeamsAddUserToTeamFromInviteReqs data = body(TeamsAddUserToTeamFromInviteReqs.class);
        String inviteId = data.inviteId;
        if (inviteId==null)
            throw new Utilmm.BadRoute(400,"user or team missing");
        TemberArray ta = select(txn -> dm.addUserByInviteId(txn,uid,inviteId));
        ta.runSock(ws);
        return team2reps.copy(ta.team);
    }        

    { make0(mapping("POST",routes.inviteInfo),self -> self::inviteInfo); }
    public Object inviteInfo() throws Pausable {
        TeamsAddUserToTeamFromInviteReqs data = body(TeamsAddUserToTeamFromInviteReqs.class);
        String query = data.inviteId;
        if (query==null) throw new Utilmm.BadRoute(400,"user or team missing");
        Teams team = select(txn ->
                MatterData.filter(txn,dm.teams,tx ->
                        query.equals(tx.inviteId)).val
        );
        // fixme - the sniffed response has only these fields, but gson populates the others
        //   perhaps should add a TeamsInviteInfo structure ???
        TeamsReps rep = new TeamsReps();
        rep.description = team.description;
        rep.displayName = team.displayName;
        rep.id = team.id;
        rep.name = team.name;
        return rep;
    }        


    { make1(routes.cxmm,self -> chanid -> self.getChannelMember(null,chanid,self.uid)); }
    { make3(routes.txcxmx,self -> self::getChannelMember); }
    public Object getChannelMember(String teamid,String chanid,String userid) throws Pausable {
        ChannelMembers cember = select(txn -> {
            Integer kuser = dm.idmap.find(txn,uid);
            int kchan = dm.idmap.find(txn,chanid);
            Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan,kuser));
            if (kcember==null)
                throw new BadRoute(403,"You do not have the appropriate permissions");
            return dm.getCember(txn,kcember);
        });
        return cember2reps.copy(cember);
    }        

    { make1(routes.umtxcm,self -> self::umtxcm); }
    public Object umtxcm(String teamid) throws Pausable {
        Integer kuser = get(dm.idmap,uid);
        ArrayList<ChannelMembers> cembers = select(txn -> {
            ArrayList<Integer> kcembers = getall(txn,dm.cemberMap,kuser);
            return dm.calcChannelUnreads(txn,kcembers,teamid);
        });
        return map(cembers,cember2reps::copy,null);
    }        

    { make1(routes.teamExists,self -> self::teamExists); }
    public Object teamExists(String name) throws Pausable {
        Integer row = select(txn -> 
                dm.teamsByName.find(txn,name));
        return set(new TeamsNameExistsReps(), x->x.exists=row!=null);
    }

    { make0(routes.unread,self -> self::unread); }
    { make0(routes.umtu,self -> self::unread); }
    public Object unread() throws Pausable {
        return select(txn -> dm.calcUnread(txn,uid));
    }

    { make3(mapping("GET",routes.channelUsers),self -> self::chanUsers); }
    public Object chanUsers(String chanid,String page,String per) throws Pausable {
        Integer kchan = get(dm.idmap,chanid);
        ArrayList<Users> users = new ArrayList();
        db4j.submitCall(txn -> {
            Btree.Range<Tuplator.III.Data> range =
                    dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true));
            while (range.next()) {
                ChannelMembers cember = dm.cembers.find(txn,range.cc.val);
                Integer kuser = dm.idmap.find(txn,cember.userId);
                Users user = dm.users.find(txn,kuser);
                users.add(user);
            }
        }).await();
        return map(users,users2userRep::copy,Utilmm.HandleNulls.skip);
    }

    { make2(mapping("GET",routes.allUsers),self -> self::getUsers); }
    public Object getUsers(String page,String per) throws Pausable {
        int kpage = Integer.parseInt(page);
        int num = Integer.parseInt(per);
        int m1=kpage*num, m2=m1+num;
        ArrayList<Users> users = new ArrayList();
        db4j.submitCall(txn -> {
            ArrayList<Integer> kusers = dm.usersByName.getall(txn).getall(cc -> cc.val);
            for (int ii=m1,jj=0; ii < m2 & jj < kusers.size(); ii++,jj++) {
                int kuser = kusers.get(ii);
                Users user = dm.users.find(txn,kuser);
                users.add(user);
            }
        }).await();
        return map(users,users2userRep::copy,Utilmm.HandleNulls.skip);
    }
    { make0(mapping("POST",routes.search),self -> self::search); }
    public Object search() throws Pausable, Exception {
        UsersSearchReqs body = body(UsersSearchReqs.class);
        ArrayList<Users> users = select(txn -> {
            ArrayList<Integer> kusers = dm.usersByName.findPrefix(txn,body.term).getall(cc -> cc.val);

            Integer kteam    = dm.getk(txn,body.teamId);
            Integer kteamNot = dm.getk(txn,body.notInTeamId);
            Integer kchanNot = dm.getk(txn,body.notInChannelId);
            ArrayList<Integer> team = dm.getKuser(txn,dm.team2tember,kteam);
            ArrayList<Integer> notTeam = dm.getKuser(txn,dm.team2tember,kteamNot);
            ArrayList<Integer> notChan = dm.getKuser(txn,dm.chan2cember,kchanNot);
            kusers = Tuplator.join(kusers,team);
            kusers = Tuplator.not(kusers,notTeam);
            kusers = Tuplator.not(kusers,notChan);
            return dm.get(txn,dm.users,kusers);
        });
        return map(users,users2userRep::copy,Utilmm.HandleNulls.skip);
    }


    { make3(mapping("GET",routes.autoUser),self -> self::autocompleteUsers); }
    public Object autocompleteUsers(String teamid,String chanid,String name) throws Pausable {
        // byName                           -> kuser1
        // team2tember -> kuser,kteam       -> kuser2
        // chan2cember -> kuser,kteam,kchan -> kuser3
        // merge (if chan or team) -> kuser

        int kteam = teamid==null || teamid.length()==0 ? -1:get(dm.idmap,teamid);
        int kchan = chanid==null || chanid.length()==0 ? -1:get(dm.idmap,chanid);
        ArrayList<Users> users = select(txn -> {
            ArrayList<Integer> kusers = dm.usersByName.findPrefix(txn,name).getall(cc -> cc.val);

            ArrayList<Integer> kusers2 = null;
            if      (kchan >= 0) kusers2 = dm.getKuser(txn,dm.chan2cember,kchan);
            else if (kteam >= 0) kusers2 = dm.getKuser(txn,dm.team2tember,kteam);
            if (kusers2 != null)
                kusers = Tuplator.join(kusers,kusers2);

            return dm.get(txn,dm.users,kusers);
        });
        ArrayList<User> map = map(users,users2userRep::copy,Utilmm.HandleNulls.skip);
        return set(new UsersAutocompleteInTeamInChannelNameSeReps(),x -> x.users=map);
    }

    { make3(mapping("GET",routes.teamUsers),self -> self::getTeamUsers); }
    public Object getTeamUsers(String teamid,String page,String per) throws Pausable {
        Integer kteam = get(dm.idmap,teamid);
        String chanid = req.getQueryComponents().get("not_in_channel");
        boolean nochan = chanid.isEmpty();
        Integer kchan = nochan ? null:get(dm.idmap,chanid);
        ArrayList<Users> users = new ArrayList();
        db4j.submitCall(txn -> {
            Btree.Range<Tuplator.III.Data> teamz =
                    dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true));
            ArrayList<Integer> kusers = nochan ? null:
                    dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true)).getall(cc -> cc.key.v2);
            HashSet<Integer> excluded = nochan ? null:new HashSet<>(kusers);
            while (teamz.next()) {
                int kuser = teamz.cc.key.v2, ktember = teamz.cc.val;
                if (!nochan && excluded.contains(kuser)) continue;
                users.add(dm.users.find(txn,kuser));
            }
        }).await();
        return map(users,users2userRep::copy,Utilmm.HandleNulls.skip);
    }

    { make3(mapping("GET",routes.nonTeamUsers),self -> self::nonTeamUsers); }
    public Object nonTeamUsers(String teamid,String page,String per) throws Pausable {
        Integer kteam = get(dm.idmap,teamid);
        ArrayList<Users> users = new ArrayList();
        db4j.submitCall(txn -> {
            Btree.Range<Tuplator.III.Data> teamz =
                    dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true));
            ArrayList<Integer> teamUsers = teamz.getall(cc -> cc.key.v2);
            HashSet<Integer> map = new HashSet<>(teamUsers);
            Btrees.IK<Users>.Range range = dm.users.getall(txn);
            while (range.next())
                if (! map.contains(range.cc.key))
                    users.add(range.cc.val);
        }).await();
        return map(users,users2userRep::copy,Utilmm.HandleNulls.skip);
    }

    { make1(routes.cxs,self -> self::cxs); }
    public Object cxs(String chanid) throws Pausable {
        Integer kchan = get(dm.idmap,chanid);
        int num = select(txn ->
                dm.chan2cember.findPrefix(txn,new Tuplator.Pair(kchan,true)).count());
        return set(new ChannelsxStatsReps(), x -> { x.channelId=chanid; x.memberCount=num; });
    }

    { make1(routes.txs,self -> self::txs); }
    public Object txs(String teamid) throws Pausable {
        Integer kteam = get(dm.idmap,teamid);
        int num = select(txn ->
                dm.team2tember.findPrefix(txn,new Tuplator.Pair(kteam,true)).count());
        // fixme - where should active member count come from ?
        //   maybe from the websocket active connections ???
        return set(new TeamsxStatsReps(),
                x -> { x.teamId=teamid; x.activeMemberCount=x.totalMemberCount=num; });
    }

    { make1(mapping("GET",routes.status),self -> self::getStatus); }
    public Object getStatus(String userid) throws Pausable {
        Integer kuser = get(dm.idmap,userid);
        Tuplator.HunkTuples.Tuple tuple = select(txn ->
                dm.status.get(txn,kuser).yield().val);
        return set(Tuplator.StatusEnum.get(tuple), x -> x.userId=userid);
    }

    { make1(mapping("PUT",routes.status),self -> self::putStatus); }
    public Object putStatus(String userid) throws Pausable {
        // fixme - need to handle status on timeout and restart
        // based on sniffing ws frames, mattermost uses a 6 minute timer at which point you're marked "away"
        Integer kuser = get(dm.idmap,userid);
        UsersStatusIdsRep status = body(UsersStatusIdsRep.class);
        status.lastActivityAt = timestamp();
        select(txn ->
            dm.status.set(txn,kuser,Tuplator.StatusEnum.get(status)));
        return status;
    }

    { make0(routes.usi,self -> self::usi); }
    public Object usi() throws Pausable {
        String [] userids = body(String [].class);
        Utilmm.Spawner<Integer> tasker = new Utilmm.Spawner(false);
        for (String userid : userids) tasker.spawn(() -> get(dm.idmap,userid));
        ArrayList<Integer> list = tasker.join();
        ArrayList<Tuplator.HunkTuples.RwTuple> tuples = new ArrayList();

        db4j.submitCall(txn -> {
            for (int ii=0; ii < userids.length; ii++) {
                Integer kuser = list.get(ii);
                tuples.add(dm.status.get(txn,kuser));
            }
        }).await();

        return mapi(tuples,
                (tup,ii) -> set(Tuplator.StatusEnum.get(tup.val),x -> x.userId=userids[ii]),                    
                Utilmm.HandleNulls.skip);
    }

    { make4(mapping("GET",routes.getPosts),self -> self::getPosts); }
    public Object getPosts(String teamid,String chanid,String firstTxt,String numTxt) throws Pausable {
        int first = Integer.parseInt(firstTxt);
        int num = Integer.parseInt(numTxt);
        ArrayList<MatterData.Row<Posts>> posts = new ArrayList();
        db4j.submitCall(txn -> {
            Integer kchan = dm.idmap.find(txn,chanid);
            Tuplator.IIK<Posts>.Range range = dm.channelPosts.findPrefix(txn,new Tuplator.Pair(kchan,true));
            for (int ii=0; ii < first && range.goprev(); ii++) {}
            for (int ii=0; ii < num && range.prev(); ii++)
                posts.add(new MatterData.Row<>(range.cc.key.v2,range.cc.val));
            dm.getPostsInfo(txn,posts);
        }).await();
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        for (MatterData.Row<Posts> row : posts) {
            Posts post = row.val;
            rep.order.add(post.id);
            rep.posts.put(post.id,posts2rep.copy(post));
        }
        return rep;
    }
    { make5(mapping("GET",routes.postsAfter),self -> self::getPostsAfter); }
    public Object getPostsAfter(String teamid,String chanid,String postid,String firstTxt,String numTxt) throws Pausable {
        int first = Integer.parseInt(firstTxt);
        int num = Integer.parseInt(numTxt);
        ArrayList<MatterData.Row<Posts>> posts = new ArrayList();
        db4j.submitCall(txn -> {
            Integer kuser = dm.idmap.find(txn,uid);
            Integer kchan = dm.idmap.find(txn,chanid);
            Integer kpost = dm.idmap.find(txn,postid);
            Tuplator.IIK<Posts>.Range range = dm.channelPosts.findRange(txn,
                    new Tuplator.Pair(kchan,kpost),new Tuplator.Pair(kchan+1,0));
            for (int ii=0; ii < first && range.gonext(); ii++) {}
            for (int ii=0; ii < num && range.next(); ii++)
                posts.add(new MatterData.Row<>(range.cc.key.v2,range.cc.val));
            dm.getPostsInfo(txn,posts);
        }).await();
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        for (MatterData.Row<Posts> row : posts) {
            Posts post = row.val;
            rep.order.add(post.id);
            rep.posts.put(post.id,posts2rep.copy(post));
        }
        return rep;
    }
    { make5(mapping("GET",routes.postsBefore),self -> self::getPostsBefore); }
    public Object getPostsBefore(String teamid,String chanid,String postid,String firstTxt,String numTxt) throws Pausable {
        int first = Integer.parseInt(firstTxt);
        int num = Integer.parseInt(numTxt);
        ArrayList<MatterData.Row<Posts>> posts = new ArrayList();
        db4j.submitCall(txn -> {
            Integer kuser = dm.idmap.find(txn,uid);
            Integer kchan = dm.idmap.find(txn,chanid);
            Integer kpost = dm.idmap.find(txn,postid);
            Tuplator.IIK<Posts>.Range range = dm.channelPosts.findRange(txn,
                    new Tuplator.Pair(kchan,0),new Tuplator.Pair(kchan,kpost));
            for (int ii=0; ii < first && range.goprev(); ii++) {}
            for (int ii=0; ii < num && range.prev(); ii++)
                posts.add(new MatterData.Row<>(range.cc.key.v2,range.cc.val));
            dm.getPostsInfo(txn,posts);
        }).await();
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        for (MatterData.Row<Posts> row : posts) {
            Posts post = row.val;
            rep.order.add(post.id);
            rep.posts.put(post.id,posts2rep.copy(post));
        }
        return rep;
    }

    { make3(mapping("GET",routes.postsSince),self -> self::getPostsSince); }
    public Object getPostsSince(String teamid,String chanid,String sinceTxt) throws Pausable {
        long since = Long.parseLong(sinceTxt);
        int num = 100;
        ArrayList<MatterData.Row<Posts>> posts = new ArrayList();
        db4j.submitCall(txn -> {
            Integer kuser = dm.idmap.find(txn,uid);
            Integer kchan = dm.idmap.find(txn,chanid);
            Tuplator.IIK<Posts>.Range range = dm.channelPosts.findPrefix(txn,new Tuplator.Pair(kchan,true));
            for (int ii=0; ii < num && range.prev(); ii++) {
                Posts post = range.cc.val;
                if (post.createAt <= since) break;
                posts.add(new MatterData.Row<>(range.cc.key.v2,post));
            }
            dm.getPostsInfo(txn,posts);
        }).await();
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        for (MatterData.Row<Posts> row : posts) {
            Posts post = row.val;
            rep.order.add(post.id);
            rep.posts.put(post.id,posts2rep.copy(post));
        }
        return rep;
    }

    { make2(mapping("POST",routes.updatePost),self -> self::updatePost); }
    public Object updatePost(String teamid,String chanid) throws Pausable {
        TeamsxChannelsxPostsUpdateReqs update = body(TeamsxChannelsxPostsUpdateReqs.class);
        // fixme - replace the old message tokens in postsIndex with the new message tokens
        ArrayList<String> tags = new ArrayList<>();
        String hashtags = getHashtags(update.message,tags);
        dm.postsIndex.tokenize(update.message,tags);
        matter.getMentions(update.message,tags);
        // https://docs.mattermost.com/help/messaging/sending-messages.html
        // note: message edits don't trigger new @mention notifications
        Integer kpost = get(dm.idmap,update.id);
        Integer kchan = get(dm.idmap,update.channelId);
        if (! chanid.equals(update.channelId))
            System.out.format("matter:updatePost - unexpected mismatch between body(%s) and params(%s)\n",
                    update.channelId,chanid);
        Posts post = select(txn -> {
            MatterData.PostInfo info = dm.getPostInfo(txn,kpost);
            Tuplator.IIK<Posts>.Range range = dm.channelPosts.findPrefix(txn,new Tuplator.Pair(kchan,kpost));
            range.next();
            Posts prev = range.cc.val;
            prev.message = update.message;
            prev.editAt = prev.updateAt = timestamp();
            prev.hashtags = hashtags;
            // fixme - bmeta.update should have a nicer api, ie bmeta.update(txn,key,val)
            // fixme - bmeta.remove should have a nicer api, ie bmeta.remove(txn,key)
            range.update();
            // fixme - abstract out the update process, ie move to MatterData
            info.finish(txn,prev,true);
            dm.postfo.update.set(txn,kpost,prev.updateAt);
            return prev;
        });
        Xxx reply = posts2rep.copy(post);
        ws.send.postEdited(reply,update.channelId,kchan);
        return reply;
    }

    { make3(mapping("POST",routes.deletePost),self -> self::deletePost); }
    public Object deletePost(String teamid,String chanid,String postid) throws Pausable {
        long time = timestamp();
        Utilmm.Ibox kchan = new Utilmm.Ibox();
        Posts post = select(txn -> {
            kchan.val = dm.idmap.find(txn,chanid);
            Integer kpost = dm.idmap.find(txn,postid);
            dm.postfo.delete.set(txn,kpost,time);
            return dm.getPostInfo(txn,kchan.val,kpost);
        });
        Xxx reply = posts2rep.copy(post);
        ws.send.postDeleted(reply,chanid,kchan.val);
        return set(new TeamsxChannelsxPostsxDeleteRep(),x -> x.id=post.id);
    }

    { make1(mapping("POST",routes.searchPosts),self -> self::searchPosts); }
    public Object searchPosts(String teamid) throws Pausable {
        TeamsxPostsSearchReqs search = body(TeamsxPostsSearchReqs.class);
        // fixme - handle teamid and the various search options, eg exact and not_in_chan
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        call(txn -> {
            // need to handle mention, free text and hashtag
            // for all messages, add all @mention and #hashtag to the index
            // for mentions, generate a list of terms, search each and OR them
            // for hashtags, search-exact
            // may need to be careful with tokenizing, ie mentions
            boolean join = !search.isOrSearch;
            ArrayList<Integer> kposts = dm.postsIndex.search(txn,join,search.terms);
            if (kposts.isEmpty()) return;
            ArrayList<Command.RwInt> kchans = dm.get(txn,dm.postfo.kchan,kposts);
            ArrayList<Command.RwInt> kteams = dm.get(txn,dm.postfo.kteam,kposts);
            txn.submitYield();
            // fixme - the golang server only returns max 100 results and in reverse chronological. mimic that
            int last = Math.max(kposts.size()-100,0);
            for (int ii=kposts.size()-1; ii >= last; ii--) {
                Posts post = dm.getPostInfo(txn,kchans.get(ii).val,kposts.get(ii));
                rep.order.add(post.id);
                rep.posts.put(post.id,posts2rep.copy(post));
            }
        });
        return rep;
    }

    { make2(mapping("GET",routes.permalink),self -> self::permalink); }
    public Object permalink(String teamid,String postid) throws Pausable {
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        rep.order.add(postid);
        ArrayList<Posts> posts = new ArrayList<>();
        db4j.submitCall(txn -> {
            Integer kpost = dm.idmap.find(txn,postid);
            int kchan = dm.postfo.kchan.get(txn,kpost).yield().val;
            Posts post = dm.getPostInfo(txn,kchan,kpost);
            posts.add(post);
            if (post.rootId != null) {
                int kroot = dm.idmap.find(txn,post.rootId);
                ArrayList<Integer> kposts = dm.root2posts.findPrefix(
                        dm.root2posts.context().set(txn).set(kroot,0)
                ).getall(cc -> cc.val);
                kposts.add(kroot);
                for (int ii=0; ii < kposts.size(); ii++) {
                    int k2 = kposts.get(ii);
                    if (k2==kpost) continue;
                    Posts post2 = dm.getPostInfo(txn,kchan,k2);
                    posts.add(post2);
                }
            }
        }).await();
        for (Posts post : posts) rep.posts.put(post.id,posts2rep.copy(post));
        return rep;
    }
    { make3(mapping("GET",routes.getFlagged),self -> self::getFlagged); }
    public Object getFlagged(String teamid,String firstTxt,String numTxt) throws Pausable {
        int first = Integer.parseInt(firstTxt);
        int num = Integer.parseInt(numTxt);
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        call(txn -> {
            Integer kuser = dm.idmap.find(txn,uid);
            Integer kteam = dm.idmap.find(txn,teamid);
            ArrayList<Integer> tmp = new ArrayList(), kposts = tmp;
            dm.prefs.findPrefix(txn,new Tuplator.Pair(kuser,true)).visit(cc -> {
                if (PrefsTypes.flagged_post.test(cc.val,"true"))
                    tmp.add(cc.key.v2);
            });
            ArrayList<Command.RwInt> kteams = MatterData.get(txn,dm.postfo.kteam,kposts);
            txn.submitYield();
            kposts = filter2(kposts,(ii,row) -> either(kteams.get(ii).val,0,kteam));
            kposts = filter(kposts,first,num);
            ArrayList<Command.RwInt> kchans = MatterData.get(txn,dm.postfo.kchan,kposts);
            txn.submitYield();
            for (int ii=0; ii < kposts.size(); ii++) {
                Posts post = dm.getPostInfo(txn,kchans.get(ii).val,kposts.get(ii));
                rep.order.add(post.id);
                rep.posts.put(post.id,posts2rep.copy(post));
            }
        });
        return rep;
    }
    { make2(mapping("GET",routes.getPinned),self -> self::pinnedPosts); }
    public Object pinnedPosts(String teamid,String chanid) throws Pausable {
        TeamsxChannelsxPostsPage060Reps rep = new TeamsxChannelsxPostsPage060Reps();
        call(txn -> {
            Integer kchan = dm.idmap.find(txn,chanid);
            ArrayList<Integer> kposts = dm.pins.findPrefix(txn,new Tuplator.Pair(kchan,true)).getall(cc -> cc.key.v2);
            for (int ii=0; ii < kposts.size(); ii++) {
                Posts post = dm.getPostInfo(txn,kchan,kposts.get(ii));
                rep.order.add(post.id);
                rep.posts.put(post.id,posts2rep.copy(post));
            }
        });
        return rep;
    }
    { make3(mapping("POST",routes.unpinPost),self -> self::pinPost); }
    { make3(mapping("POST",routes.pinPost),self -> self::pinPost); }
    public Object pinPost(String teamid,String chanid,String postid) throws Pausable {
        boolean pin = req.uriPath.endsWith("/pin");
        Utilmm.Ibox kchan = new Utilmm.Ibox();
        // fixme:dry - this and updatePost share code
        Posts post = select(txn -> {
            kchan.val = dm.idmap.find(txn,chanid);
            Integer kpost = dm.idmap.find(txn,postid);
            MatterData.PostInfo info = dm.getPostInfo(txn,kpost);
            Tuplator.IIK<Posts>.Range range = dm.channelPosts.findPrefix(txn,new Tuplator.Pair(kchan.val,kpost));
            range.next();
            Posts prev = range.cc.val;
            info.finish(txn,prev,true);
            if (prev.isPinned==pin)
                return prev;
            prev.isPinned = pin;
            prev.updateAt = timestamp();
            range.update();
            dm.postfo.update.set(txn,kpost,prev.updateAt);
            if (pin)
                dm.pins.insert(txn,new Tuplator.Pair(kchan.val,kpost),null);
            else
                dm.pins.remove(txn,new Tuplator.Pair(kchan.val,kpost));
            return prev;
        });
        Xxx reply = posts2rep.copy(post);
        ws.send.postEdited(reply,chanid,kchan.val);
        return reply;
    }

    { make3(mapping("GET",routes.reactions),self -> self::getReactions); }
    public Object getReactions(String teamid,String chanid,String postid) throws Pausable {
        ArrayList<Reactions> reactions = select(txn -> {
            Integer kpost = dm.idmap.find(txn,postid);
            Tuplator.Pair key = new Tuplator.Pair(kpost,true);
            return dm.reactions.findPrefix(txn,key).getall(cc -> cc.val);
        });
        return map(reactions,reactions2rep::copy,null);
    }

    { make3(mapping("POST",routes.deleteReaction),self -> self::saveReaction); }
    { make3(mapping("POST",routes.saveReaction),self -> self::saveReaction); }
    public Object saveReaction(String teamid,String chanid,String postid) throws Pausable {
        boolean save = req.uriPath.endsWith("/save");
        Reaction body = body(Reaction.class);
        body.createAt = timestamp();
        Reactions reaction = req2reactions.copy(body);
        Utilmm.Ibox kchan = new Utilmm.Ibox();
        Posts post = select(txn -> {
            Integer kpost = dm.idmap.find(txn,reaction.postId);
            Command.RwInt chanCmd = dm.postfo.kchan.get(txn,kpost);
            Command.RwInt numReact = dm.postfo.numReactions.get(txn,kpost);
            Integer kuser = dm.idmap.find(txn,reaction.userId);
            kchan.val = chanCmd.val;
            Tuplator.Pair key = new Tuplator.Pair(kpost,kuser);
            Tuplator.IIK<Reactions>.Range range =
                    dm.reactions.findPrefix(txn,key);
            while (range.next())
                if (range.cc.val.emojiName.equals(reaction.emojiName))
                    break;
            if (save==range.cc.match) return null;
            range.cc.set(key,reaction);
            if (save) range.insert();
            else range.remove();
            dm.postfo.update.set(txn,kpost,timestamp());
            dm.postfo.numReactions.set(txn,kpost,numReact.val+(save?1:-1));
            return dm.getPostInfo(txn,kchan.val,kpost);
        });
        Xxx reply = posts2rep.copy(post);
        if (post != null) {
            ws.send.reactionAdded(body,save,chanid,kchan.val);
            ws.send.postEdited(reply,chanid,kchan.val);
        }
        return body;
    }

    { make2(mapping("POST",routes.createPosts),self -> self::createPosts); }
    public Object createPosts(String teamid,String chanid) throws Pausable {
        TeamsxChannelsxPostsCreateReqs postReq = body(TeamsxChannelsxPostsCreateReqs.class);
        Posts post = newPost(req2posts.copy(postReq),uid,postReq.fileIds.toArray(new String[0]));
        // fixme - verify fileIds exist and are the correct length
        // fixme - verify userid is a member of channel
        // fixme - use the array overlay to finf this faster

        ArrayList<String> tags = new ArrayList<>();
        post.hashtags = getHashtags(post.message,tags);
        dm.postsIndex.tokenize(post.message,tags);
        ArrayList<MatterControl.NickInfo> kmentions = matter.getMentions(post.message,tags);
        Utilmm.Ibox kchan = new Utilmm.Ibox();
        Utilmm.Box<Users> user = box();
        Utilmm.Box<Channels> chan = box();
        boolean success = select(txn -> {
            Integer kuser = dm.idmap.find(txn,uid);
            kchan.val = dm.idmap.find(txn,chanid);
            Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan.val,kuser));
            if (kcember==null)
                return false;
            Channels c2 = chan.val = dm.getChan(txn,kchan.val);
            if (isDirect(c2)) {
                boolean starts = c2.name.startsWith(uid);
                // hidden dependency - kcembers should be consecutive and in sort order
                int kcember2 = starts ? kcember+1:kcember-1;
                int kmention = dm.links.kuser.get(txn,kcember2).yield().val;
                String [] ids = c2.name.split("__");
                kmentions.add(new MatterControl.NickInfo(kmention,ids[starts ? 1:0]));
            }
            MatterData.PostMetadata pmd = new MatterData.PostMetadata(kmentions,tags);
            dm.addPost(txn,kchan.val,post,pmd);
            user.val = dm.users.find(txn,kuser);
            return true;
        });
        if (! success)
            return "user not a member of channel - post not created";
        Xxx reply = set(posts2rep.copy(post),x -> x.pendingPostId = postReq.pendingPostId);
        // fixme - mentions need to be sent to websocket
        ArrayList<String> mentionIds = map(kmentions,ni -> ni.userid);
        ws.send.posted(reply,chan.val,user.val.username,kchan.val,mentionIds);
        return reply;
    }

    { make1(mapping("GET",routes.image),self -> self::image); }
    public Object image(String userid) throws Pausable, IOException {
        // the query includes the updateAt value from the user
        // but it's not clear how this is intended to be used
        // at times i've seen 304 but i haven't been able to reproduce it recently
        String base = makeFilename(userid+".png");
        File file = new File(base);
        if (file.exists())
            session.sendFile(req,resp,file,"image/png");
        else {
            Users user = select(txn -> dm.get(txn,dm.users,userid));
            String initials = makeInitials(user.username,user.firstName,user.lastName);
            String svg = genSvg(userid.hashCode(),initials);
            cacheControl(resp,25.17*3600);
            session.send(resp,svg.getBytes(),"image/svg+xml");
        }
        return null;
    }

    // from devtools: {email: } -> {email: }
    { make0(mapping("POST",routes.passwordReset),self -> self::reset); }
    public Object reset() throws Pausable, IOException {
        throw new BadRoute(418,"password reset by email is not (yet) supported",HttpResponse.ST_TEAPOT);
    }

    { make0(mapping("POST",routes.savePreferences),self -> self::savePref); }
    public Object savePref() throws Pausable {
        return putPref(null);
    }        

    { make0(mapping("POST",routes.deletePrefs),self -> self::delPref); }
    public Object delPref() throws Pausable {
        PreferencesSaveReq [] body = body(PreferencesSaveReq [].class);
        ArrayList<Preferences> prefs = map(java.util.Arrays.asList(body),req2prefs::copy,null);
        int num = body.length;
        db4j.submitCall(txn -> {
            for (int ii=0; ii < num; ii++) {
                Preferences pref = prefs.get(ii);
                Integer kuser = dm.idmap.find(txn,pref.userId);
                Integer krow = dm.idmap.find(txn,pref.name);
                dm.prefs.findPrefix(txn,new Tuplator.Pair(kuser,krow))
                        .first(cc -> pref.category.equals(cc.val.category))
                        .remove();
            }
        }).await();
        return set(new ChannelsReps.View(),x->x.status="OK");
    }        

    { make1(mapping("PUT",routes.uxPreferences),self -> self::putPref); }
    public Object putPref(String userid) throws Pausable {
        PreferencesSaveReq [] body = body(PreferencesSaveReq [].class);
        ArrayList<Preferences> prefs = map(java.util.Arrays.asList(body),req2prefs::copy,null);
        int num = body.length;
        int [] kusers = new int[num];
        db4j.submitCall(txn -> {
            for (int ii=0; ii < num; ii++) {
                if (userid==null | ii==0)
                    kusers[ii] = dm.idmap.find(txn,body[ii].userId);
                else {
                    kusers[ii] = kusers[0];
                    if (userid != null && !userid.equals(body[ii].userId))
                        // fixme - this check is presumably redundant but if they do disagree it's not
                        //   clear which id should be honored, ie what's the point of the userid param ???
                        System.out.format(
                                "matter.warning: preferences userid mismatch ... %s, %s\n",
                                userid,body[ii].userId);
                }
                Preferences pref = prefs.get(ii);
                Integer krow = dm.idmap.find(txn,pref.name);
                dm.prefs.findPrefix(txn,new Tuplator.Pair(kusers[ii],krow))
                        .first(cc -> pref.category.equals(cc.val.category))
                        .set(cc -> cc.val=pref)
                        .upsert();
            }
        }).await();
        for (int ii=0; ii < num; ii++)
            ws.send.preferencesChanged(kusers[ii],body[ii].userId,body[ii]);
        return true;
    }        

    { make0(mapping("GET",routes.umPreferences),self -> self::getPref); }
    public Object getPref() throws Pausable {
        ArrayList<Preferences> prefs = select(txn -> {
            int kuser = dm.idmap.find(txn,uid);
            return dm.prefs.findPrefix(txn,new Tuplator.Pair(kuser,true)).getall(cc -> cc.val);
        });
        return map(prefs,prefs2rep::copy,Utilmm.HandleNulls.skip);
    }

    { make2(mapping("GET",routes.getTeams),self -> self::getTeams); }
    public Object getTeams(String page,String perPage) throws Pausable {
        // fixme - get the page and per_page values
        ArrayList<Teams> teams = select(txn -> dm.teams.getall(txn).vals());
        return map(teams,team2reps::copy,Utilmm.HandleNulls.skip);
    }        

    { make1(mapping("GET",routes.umtxc),self -> self::myTeamChannels); }
    public Object myTeamChannels(String teamid) throws Pausable {
        Integer kuser = get(dm.idmap,uid);
        Integer kteamDesired = get(dm.idmap,teamid);
        if (kteamDesired==null)
            return null;
        ArrayList<Channels> channels = new ArrayList();
        db4j.submitCall(txn -> {
            ArrayList<Integer> kcembers
                    = dm.cemberMap.findPrefix(dm.cemberMap.context().set(txn).set(kuser,0)).
                            getall(cc -> cc.val);

            int num = kcembers.size();
            MatterData.ChannelGetter [] getters = new MatterData.ChannelGetter[num];
            for (int ii=0; ii < num; ii++)
                getters[ii] = dm.new ChannelGetter(txn).prep(kcembers.get(ii));
            txn.submitYield();
            for (int ii = 0; ii < num; ii++)
                getters[ii].first(null);
            txn.submitYield();
            String hash = dm.check(getters);
            if (Utilmm.equals(etag,hash))
                throw new Http304();
            for (int ii = 0; ii < num; ii++)
                channels.add(getters[ii].get(kteamDesired,1));
            resp.addField("Etag","" + hash);
        }).await();
        return map(channels,chan -> chan2reps.copy(chan),Utilmm.HandleNulls.skip);
    }

    { make1(mapping("POST",routes.upload),self -> self::upload); }
    // Note: handled by spring matter
    public Object upload(String teamid) throws Pausable, Exception {
        throw new Utilmm.BadRoute(501,"images are disabled - code has been stashed",HttpResponse.ST_NOT_IMPLEMENTED);
    }

    { make0(mapping("PUT",routes.patch),self -> self::patch); }
    public Object patch() throws Pausable, Exception {
        UsersReps body = body(UsersReps.class);
        NotifyUsers nu = gson.fromJson(body.notifyProps,NotifyUsers.class);
        String props = gson.toJson(body.notifyProps);
        ArrayList<String> nicks1 = new ArrayList(), nicks2;
        Utilmm.Ibox kuser = new Utilmm.Ibox();
        // fixme - need to handle changes to indexes, ie remove old and then add the new
        Users result = select(txn -> {
            kuser.val = dm.idmap.find(txn,uid);
            Utilmm.Box<String> name = new Utilmm.Box();
            Users u2 = dm.users.update(txn,kuser.val,user -> {
                getUserNicks(user,nicks1);
                name.val = user.username;
                if (body.email != null) user.email = body.email;
                if (body.firstName != null) user.firstName = body.firstName;
                if (body.lastName != null) user.lastName = body.lastName;
                if (body.nickname != null) user.nickname = body.nickname;
                if (body.position != null) user.position = body.position;
                if (body.username != null) user.username = body.username;
                if (body.notifyProps != null) user.notifyProps = props;
                user.updateAt = timestamp();
            }).val;
            if (! name.val.equals(u2.username)) {
                dm.usersByName.remove(txn,name.val);
                dm.usersByName.insert(txn,u2.username,kuser.val);
            }
            return u2;
        });
        nicks2 = getUserNicks(result);
        MatterControl.NickInfo row = new MatterControl.NickInfo(kuser.val,result.id);
        Tuplator.delta(nicks1,nicks2,nick -> matter.addNick(nick,row),nick -> matter.delNick(nick,kuser.val));
        User reply = users2userRep.copy(result);
        ws.send.userUpdated(reply);
        return users2reps.copy(result);
    }

    { make0(mapping("POST",routes.cmmv),self -> self::cmmv); }
    public Object cmmv() throws Pausable {
        ChannelsMembersxViewReqs body = body(ChannelsMembersxViewReqs.class);
        // fixme - need to figure out what this is supposed to do (other than just reply with ok)
        boolean update = body.channelId.length() > 0;
        // fixme::immediacy - on rollback, could update using a newer count
        if (update) db4j.submitCall(txn -> {
            Integer kchan = dm.idmap.find(txn,body.channelId);
            Integer kuser = dm.idmap.find(txn,uid);
            Command.RwInt count = dm.chanfo.msgCount.get(txn,kchan);
            Integer kcember = dm.chan2cember.find(txn,new Tuplator.Pair(kchan,kuser));
            dm.links.msgCount.set(txn,kcember,count.val);
            dm.links.mentionCount.set(txn,kcember,0);
        });
        return set(new ChannelsReps.View(),x->x.status="OK");
    }
    
    { make0(mapping("POST",routes.channels),self -> self::postChannel); }
    public Object postChannel() throws Pausable {
        ChannelsReqs body = body(ChannelsReqs.class);
        Channels chan = req2channel.copy(body);
        chan.creatorId = uid;
        Box<Socketable> box = new Box();
        ChannelMembers cember = newChannelMember(uid,chan.id);
        db4j.submitCall(txn -> {
            Integer kuser = dm.idmap.find(txn,uid);
            Integer kteam = dm.idmap.find(txn,chan.teamId);
            int kchan = dm.addChan(txn,chan,kteam);
            dm.addChanMember(txn,kuser,kchan,cember,kteam);
            box.val = dm.systemPost(txn,PostsTypes.system_join_channel,kchan,chan,kuser,null);
        }).await();
        box.val.run(ws);
        return chan2reps.copy(chan);
    }

    { make0(mapping("POST",routes.direct),self -> () -> self.createGroup(false)); }
    { make1(mapping("POST",routes.createGroup),self -> teamid -> self.createGroup(true)); }
    ChannelsReps createGroup(boolean group) throws Pausable {
        // for a direct message, the format is: [initiator, teammate]
        String [] body = body(String [].class);
        String [] userids = group ? append(body,uid) : body;
        int num = userids.length;
        String [] names = new String[num];
        java.util.Arrays.sort(userids);
        Channels chan = group
                ? newChannel("",MatterControl.sha1hex(userids),"","G",uid)
                : newChannel("",userids[0] + "__" + userids[1],"","D",uid);
        ChannelMembers [] cembers = new ChannelMembers[num];
        for (int ii=0; ii < num; ii++)
            cembers[ii] = newChannelMember(userids[ii],chan.id);
        MatterData.Row<Channels> row = select(txn -> {
            Integer kteam = 0;
            MatterData.Row<Channels> existing = dm.getChanByName(txn,kteam,chan.name);
            if (existing != null)
                return existing;
            if (group) {
                for (int ii=0; ii < num; ii++)
                    names[ii] = dm.get(txn,dm.users,userids[ii]).username;
                java.util.Arrays.sort(names);
                chan.displayName = String.join(", ", names);
            }
            int kchan = dm.addChan(txn,chan,kteam);
            // hidden dependency - for direct channels, kcmebers must be consecutive
            for (int ii=0; ii < num; ii++)
                dm.addChanMember(txn,null,kchan,cembers[ii],kteam);
            return new MatterData.Row(kchan,chan);
        });
        if (group)
            ws.send.groupAdded(userids,row.val.id,row.key);
        else {
            String teammate = body[1];
            ws.send.directAdded(teammate,row.val.id,row.key);
        }
        return chan2reps.copy(row.val);
    }

    { make1(mapping("PUT",routes.setTeams),self -> self::patchTeams); }
    public Object patchTeams(String teamid) throws Pausable {
        TeamsReps body = body(TeamsReps.class);
        Teams team = reps2teams.copy(body);
        team.updateAt = timestamp();
        call(txn -> {
            Integer kteam = dm.idmap.find(txn,teamid);
            dm.teams.update(txn,kteam,team);
        });
        TeamsReps reply = team2reps.copy(team);
        ws.send.updateTeam(reply);
        return reply;
    }

    { make0(mapping("POST",routes.teams),self -> self::postTeams); }
    public Object postTeams() throws Pausable {
        String body = body();
        Integer kuser = get(dm.idmap,uid);
        TeamsReqs treq = gson.fromJson(body,TeamsReqs.class);
        Teams team = req2teams.copy(treq);
        team.id = newid();
        team.inviteId = newid();
        team.updateAt = team.createAt = new java.util.Date().getTime();
        Channels town = newChannel(team.id,TOWN[0],TOWN[1],"O",uid);
        Channels topic = newChannel(team.id,TOPIC[0],TOPIC[1],"O",uid);
        ChannelMembers townm = newChannelMember(uid,town.id);
        ChannelMembers topicm = newChannelMember(uid,topic.id);
        TeamMembers tm = newTeamMember(team.id,uid);
        tm.roles = "team_user team_admin";
        Box<Socketable> box = new Box();
        Integer result = select(txn -> {
            Users user = dm.users.find(txn,kuser);
            team.email = user.email;
            Integer kteam = dm.addTeam(txn,team);
            if (kteam==null) return null;
            dm.addTeamMember(txn,kuser,kteam,tm);
            int ktown = dm.addChan(txn,town,kteam);
            int ktopic = dm.addChan(txn,topic,kteam);
            dm.addChanMember(txn,kuser,ktown,townm,kteam);
            dm.addChanMember(txn,kuser,ktopic,topicm,kteam);
            box.val = dm.systemPost(txn,PostsTypes.system_join_channel,ktown,town,user,null);
            dm.systemPost(txn,PostsTypes.system_join_channel,ktopic,topic,user,null);
            return kteam;
        });
        if (result==null)
            return "team already exists";
        box.val.run(ws);
        return team2reps.copy(team);
    }



    { make0("/api/v3/general/log_client",self -> () -> new int[0]); }

    // fixme - txc should have ?page/per_page
    // fixme - should prolly check auth, ie use make1() instead of add(), but it's nice to have a
    //           a non-factory usage
    { add(routes.txc,this::teamChannels); }
    public Object teamChannels(String teamid) throws Pausable {
        Integer kteam = get(dm.idmap,teamid);
        if (kteam==null)
            return null;
        ArrayList<Channels> channels = new ArrayList();
        db4j.submitCall(txn -> {
            Btree.Range<Btrees.II.Data> range = dm.chanByTeam.findPrefix(dm.chanByTeam.context().set(txn).set(kteam,0));
            while (range.next()) {
                Channels chan = dm.getChan(txn,range.cc.val);
                if (chan.deleteAt==0)
                    channels.add(chan);
            }
        }).await();
        return map(channels,chan -> chan2reps.copy(chan),Utilmm.HandleNulls.skip);
    }



    { add(routes.license,() -> set(new LicenseClientFormatOldReps(),x->x.isLicensed="false")); }
    { add(routes.websocket,() -> "not available"); }


        
        
        

    
    
    
    public static class Routes {
        String cx = "/api/v4/channels/{chanid}";
        String patchChannel = "/api/v4/channels/{chanid}/patch";
        String cxmm = "/api/v4/channels/{chanid}/members/me";
        String teamExists = "/api/v4/teams/name/{name}/exists";
        String umt = "/api/v4/users/me/teams/";
        String umtm = "/api/v4/users/me/teams/members";
        String umtxc = "/api/v4/users/me/teams/{teamid}/channels";
        String umtxcm = "/api/v4/users/me/teams/{teamid}/channels/members";
        String channels = "/api/v4/channels";
        String setTeams = "/api/v4/teams/{teamid}";
        String teams = "/api/v4/teams";
        String getTeams = "/api/v4/teams?page/per_page";
        String cmmv = "/api/v4/channels/members/me/view";
        String websocket = "/api/v3/users/websocket";
        String cxs = "/api/v4/channels/{chanid}/stats";
        String txs = "/api/v4/teams/{teamid}/stats";
        String invite = "/api/v3/teams/add_user_to_team_from_invite";
        String inviteInfo = "/api/v3/teams/get_invite_info";
        String license = "/api/v4/license/client?format";
        String status = "/api/v4/users/{userid}/status";
        String image = "/api/v3/users/{userid}/image?*";
        String config = "/api/v4/config/client?format";
        String users = "/api/v4/users?*";
        String usersIds = "/api/v4/users/ids";
        String channelUsers = "/api/v4/users?in_channel/page/per_page";
        String allUsers = "/api/v4/users?page/per_page";
        String teamUsers = "/api/v4/users?in_team/page/per_page/*";
        String nonTeamUsers = "/api/v4/users?not_in_team/page/per_page";
        String login = "/api/v3/users/login";
        String login4 = "/api/v4/users/login";
        String logout = "/api/v3/users/logout";
        String um = "/api/v4/users/me";
        String ux = "/api/v4/users/{userid}";
        String umPreferences = "/api/v4/users/me/preferences";
        String unread = "/api/v3/teams/unread";
        String umtu = "/api/v4/users/me/teams/unread";
        String txmi = "/api/v4/teams/{teamid}/members/ids";
        String txc = "/api/v4/teams/{teamid}/channels?page/per_page";
        String usi = "/api/v4/users/status/ids";
        String cxm = "/api/v4/channels/{chanid}/members";
        String cxmx = "/api/v4/channels/{chanid}/members/{userid}";
        String txmx = "/api/v4/teams/{teamid}/members/{userid}";
        String cxmi = "/api/v4/channels/{chanid}/members/ids";
        String searchPosts = "/api/v3/teams/{teamid}/posts/search";
        String createPosts = "/api/v3/teams/{teamid}/channels/{chanid}/posts/create";
        String getPosts = "/api/v3/teams/{teamid}/channels/{chanid}/posts/page/{first}/{num}";
        String postsAfter = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/after/{first}/{num}";
        String postsBefore = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/before/{first}/{num}";
        String postsSince = "/api/v3/teams/{teamid}/channels/{chanid}/posts/since/{start}";
        String pinPost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/pin";
        String unpinPost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/unpin";
        String deletePost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/delete";
        String getPinned = "/api/v3/teams/{teamid}/channels/{chanid}/pinned";
        String getFlagged = "/api/v3/teams/{teamid}/posts/flagged/{first}/{num}";
        String updatePost = "/api/v3/teams/{teamid}/channels/{chanid}/posts/update";
        String permalink = "/api/v3/teams/{teamid}/pltmp/{postid}";
        String txmBatch = "/api/v4/teams/{teamid}/members/batch";
        String teamsMe = "/api/v3/teams/{teamid}/me";
        String oldTembers = "/api/v3/teams/members";
        String direct = "/api/v4/channels/direct";
        String uxPreferences = "/api/v4/users/{userid}/preferences";
        String savePreferences = "/api/v3/preferences/save";
        String deletePrefs = "/api/v3/preferences/delete";
        String createGroup = "/api/v3/teams/{teamid}/channels/create_group";
        String txcName = "/api/v4/teams/{teamid}/channels/name/{channelName}";
        String txcxmx = "/api/v3/teams/{teamid}/channels/{chanid}/members/{userid}";
        String autoUser = "/api/v4/users/autocomplete?in_team/in_channel/name"; // -> [users]
        String txcSearch = "/api/v4/teams/{teamid}/channels/search"; // post {term:} -> channel
        String upload = "/api/v3/teams/{teamid}/files/upload";
        String fileGet = "/api/v3/files/{fileId}/get";
        String fileGetThumb = "/api/v3/files/{fileId}/get_thumbnail";
        String filePreview = "/api/v3/files/{fileId}/get_preview";
        String fileInfos = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/get_file_infos";
        String patch = "/api/v4/users/me/patch";
        String password = "/api/v4/users/{userid}/password";
        String search = "/api/v4/users/search";
        String deleteReaction = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/reactions/delete";
        String saveReaction = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/reactions/save";
        String reactions = "/api/v3/teams/{teamid}/channels/{chanid}/posts/{postid}/reactions";
        String passwordReset = "/api/v3/users/send_password_reset";
    }

}
    
