
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UserAddedData {

    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("user_id")
    @Expose
    public String userId;

    /**
     * No args constructor for use in serialization
     * 
     */
    public UserAddedData() {
    }

    /**
     * 
     * @param teamId
     * @param userId
     */
    public UserAddedData(String teamId, String userId) {
        super();
        this.teamId = teamId;
        this.userId = userId;
    }

}
