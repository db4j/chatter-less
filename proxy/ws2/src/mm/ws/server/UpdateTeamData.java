
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class UpdateTeamData {

    @SerializedName("team")
    @Expose
    public String team;

    /**
     * No args constructor for use in serialization
     * 
     */
    public UpdateTeamData() {
    }

    /**
     * 
     * @param team
     */
    public UpdateTeamData(String team) {
        super();
        this.team = team;
    }

}
