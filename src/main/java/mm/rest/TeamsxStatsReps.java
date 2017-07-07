
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsxStatsReps {

    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("total_member_count")
    @Expose
    public int totalMemberCount;
    @SerializedName("active_member_count")
    @Expose
    public int activeMemberCount;

}
