
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsMembersRep {

    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("user_id")
    @Expose
    public String userId;
    @SerializedName("roles")
    @Expose
    public String roles;
    @SerializedName("delete_at")
    @Expose
    public long deleteAt;

}
