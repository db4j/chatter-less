
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsxMembersBatchReq {

    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("team_id")
    @Expose
    public String teamId;
    /**
     * 
     * (Required)
     * 
     */
    @SerializedName("user_id")
    @Expose
    public String userId;

}
