
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UsersSearchReqs {

    @SerializedName("term")
    @Expose
    public String term;
    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("not_in_channel_id")
    @Expose
    public String notInChannelId;
    @SerializedName("not_in_team_id")
    @Expose
    public String notInTeamId;

}
