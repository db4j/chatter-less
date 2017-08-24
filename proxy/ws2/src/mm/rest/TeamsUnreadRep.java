
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsUnreadRep {

    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("msg_count")
    @Expose
    public long msgCount;
    @SerializedName("mention_count")
    @Expose
    public long mentionCount;

}
