
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Broadcast {

    @SerializedName("omit_users")
    @Expose
    public Object omitUsers;
    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("channel_id")
    @Expose
    public String channelId;
    @SerializedName("team_id")
    @Expose
    public String teamId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Broadcast() {
    }

    /**
     * 
     * @param teamId
     * @param userId
     * @param channelId
     * @param omitUsers
     */
    public Broadcast(Object omitUsers, String userId, String channelId, String teamId) {
        super();
        this.omitUsers = omitUsers;
        this.userId = userId;
        this.channelId = channelId;
        this.teamId = teamId;
    }

}
